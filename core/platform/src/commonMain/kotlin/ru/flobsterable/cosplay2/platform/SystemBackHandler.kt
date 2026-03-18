package ru.flobsterable.cosplay2.platform

import androidx.compose.runtime.Composable

@Composable
expect fun SystemBackHandler(
    enabled: Boolean,
    onBack: () -> Unit
)
