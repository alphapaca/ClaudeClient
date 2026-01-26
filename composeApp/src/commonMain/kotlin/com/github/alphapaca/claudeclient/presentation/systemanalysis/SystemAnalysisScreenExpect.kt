package com.github.alphapaca.claudeclient.presentation.systemanalysis

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * System analysis screen expect declaration.
 * Implemented in jvmMain for desktop support using local LLM.
 */
@Composable
expect fun SystemAnalysisScreenWrapper(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
)
