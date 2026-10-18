package ru.flobsterable.cosplay2.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.File

@Composable
actual fun rememberMyCityStorage(): FavoritesStorage {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidFavoritesStorage(File(context.filesDir, "my-city.txt")) }
}
