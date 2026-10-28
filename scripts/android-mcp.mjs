import { pathToFileURL } from 'node:url';
import path from 'node:path';
import { createInterface } from 'node:readline';
import { writeFile } from 'node:fs/promises';

// Uses the same installed stdio server as Codex; no direct ADB UI fallback.
// The bridge lets the current chat verify MCP before a tool-catalog reload.
export async function connectAndroidMcp({
    runtimeDirectory = path.join(process.env.LOCALAPPDATA, 'Cosplay2/testing/mobile-mcp-1.0.12'),
    sdkDirectory = process.env.ANDROID_HOME || path.join(process.env.LOCALAPPDATA, 'Android/Sdk'),
} = {}) {
    const sdk = path.join(runtimeDirectory, 'node_modules/@modelcontextprotocol/sdk/dist/esm');
    const { Client } = await import(pathToFileURL(path.join(sdk, 'client/index.js')).href);
    const { StdioClientTransport } = await import(pathToFileURL(path.join(sdk, 'client/stdio.js')).href);
    const transport = new StdioClientTransport({
        command: process.execPath,
        args: [path.join(runtimeDirectory, 'node_modules/@mobilenext/mobile-mcp/lib/index.js')],
        env: {
            ...process.env,
            ANDROID_HOME: sdkDirectory,
            ANDROID_SDK_ROOT: sdkDirectory,
            PATH: path.join(sdkDirectory, 'platform-tools') + path.delimiter + process.env.PATH,
            MOBILEMCP_DISABLE_TELEMETRY: '1',
        },
        stderr: 'pipe',
    });
    // Drain diagnostics without mixing server stdout with MCP JSON-RPC.
    transport.stderr?.on('data', () => {});
    const client = new Client({ name: 'cosplay-android-smoke', version: '1.0.0' });
    try {
        await client.connect(transport);
        return client;
    } catch (error) {
        await transport.close();
        throw error;
    }
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
    if (!['--probe', '--session'].includes(process.argv[2])) throw new Error('Usage: node scripts/android-mcp.mjs --probe | --session');
    const client = await connectAndroidMcp();
    try {
        if (process.argv[2] === '--session') {
            // One JSON request per line: {name, arguments, imagePath?}; one response per line.
            const input = createInterface({ input: process.stdin, crlfDelay: Infinity });
            for await (const line of input) {
                if (!line.trim()) continue;
                try {
                    const request = JSON.parse(line);
                    if (request.method === 'close') break;
                    const result = request.method === 'tools' ? await client.listTools() :
                        await client.callTool({ name: request.name, arguments: request.arguments || {} });
                    if (request.imagePath) {
                        const image = result.content?.find(item => item.type === 'image');
                        if (image) {
                            await writeFile(request.imagePath, Buffer.from(image.data, 'base64'));
                            result.imagePath = request.imagePath;
                        }
                    }
                    if (result.content) result.content = result.content.map(item => item.type === 'image' ?
                        { type: 'text', text: '[MCP image saved to imagePath when supplied]' } : item);
                    console.log(JSON.stringify(result));
                } catch (error) { console.log(JSON.stringify({ isError: true, error: String(error) })); }
            }
        } else {
            const tools = await client.listTools();
            console.log(JSON.stringify({ tools: tools.tools.map(tool => tool.name) }, null, 2));
            const devices = await client.callTool({ name: 'mobile_list_available_devices', arguments: {} });
            console.log(JSON.stringify(devices, null, 2));
            if (devices.isError) process.exitCode = 1;
        }
    } finally { await client.close(); }
}
