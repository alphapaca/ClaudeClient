package com.github.alphapaca.claudeclient.presentation.codesession

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun CodeSessionScreenWrapper(
    modifier: Modifier,
    onBackClick: () -> Unit,
) {
    CodeSessionScreen(
        modifier = modifier,
        onBackClick = onBackClick,
    )
}
