package com.github.alphapaca.claudeclient.presentation.codesession

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
actual fun CodeSessionScreenWrapper(
    modifier: Modifier,
    onBackClick: () -> Unit,
) {
    // Code sessions are not yet supported on Android
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text("Code sessions are only available on desktop")
    }
}
