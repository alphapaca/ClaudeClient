package com.github.alphapaca.claudeclient

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.github.alphapaca.claudeclient.presentation.theme.DarkColorScheme
import com.github.alphapaca.claudeclient.presentation.theme.LightColorScheme
import com.github.alphapaca.claudeclient.presentation.theme.Typography

@Composable
fun ClaudeClientTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}