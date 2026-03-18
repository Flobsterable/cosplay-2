package ru.flobsterable.cosplay2.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import org.jetbrains.skia.Image

actual suspend fun loadNetworkImage(
    client: HttpClient,
    url: String
): ImageBitmap? {
    val bytes = runCatching { client.get(url).body<ByteArray>() }.getOrNull() ?: return null
    return runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
}
