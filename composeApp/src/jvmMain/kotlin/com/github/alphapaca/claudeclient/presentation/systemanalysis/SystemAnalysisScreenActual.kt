package com.github.alphapaca.claudeclient.presentation.systemanalysis

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun SystemAnalysisScreenWrapper(
    modifier: Modifier,
    onBackClick: () -> Unit,
) {
    SystemAnalysisScreen(
        modifier = modifier,
        onBackClick = onBackClick,
    )
}
