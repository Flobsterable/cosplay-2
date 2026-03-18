package ru.flobsterable.cosplay2.image

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

actual suspend fun loadNetworkImage(
    client: HttpClient,
    url: String
): ImageBitmap? {
    val bytes = runCatching { client.get(url).body<ByteArray>() }.getOrNull() ?: return null
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    return bitmap.asImageBitmap()
}
