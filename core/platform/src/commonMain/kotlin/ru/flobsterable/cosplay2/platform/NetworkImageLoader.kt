package ru.flobsterable.cosplay2.platform

import androidx.compose.ui.graphics.ImageBitmap
import io.ktor.client.HttpClient

expect suspend fun loadNetworkImage(
    client: HttpClient,
    url: String
): ImageBitmap?
