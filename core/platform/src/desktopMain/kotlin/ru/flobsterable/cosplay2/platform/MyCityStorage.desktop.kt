package ru.flobsterable.cosplay2.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberMyCityStorage(): FavoritesStorage = remember {
    FileFavoritesStorage(appDataDirectory().resolve("Cosplay2/my-city.txt"))
}
