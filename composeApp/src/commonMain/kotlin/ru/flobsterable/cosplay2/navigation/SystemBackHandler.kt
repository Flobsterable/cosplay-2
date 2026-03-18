package ru.flobsterable.cosplay2.navigation

import androidx.compose.runtime.Composable

@Composable
expect fun SystemBackHandler(
    enabled: Boolean,
    onBack: () -> Unit
)
