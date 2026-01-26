package com.github.alphapaca.claudeclient.presentation.systemanalysis

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
actual fun SystemAnalysisScreenWrapper(
    modifier: Modifier,
    onBackClick: () -> Unit,
) {
    // System analysis is not yet supported on Android
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text("System analysis is only available on desktop")
    }
}
