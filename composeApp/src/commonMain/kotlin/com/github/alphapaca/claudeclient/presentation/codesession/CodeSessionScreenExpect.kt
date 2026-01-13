package com.github.alphapaca.claudeclient.presentation.codesession

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Code session screen expect declaration.
 * Implemented in jvmMain for desktop support.
 */
@Composable
expect fun CodeSessionScreenWrapper(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
)
