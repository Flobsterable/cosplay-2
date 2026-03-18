package ru.flobsterable.cosplay2.platform

import androidx.compose.runtime.Composable

@Composable
actual fun SystemBackHandler(
    enabled: Boolean,
    onBack: () -> Unit
) = Unit
