package com.github.alphapaca.webagent

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.github.alphapaca.webagent.presentation.qa.QAScreen
import com.github.alphapaca.webagent.presentation.theme.WebAgentTheme
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val root = document.getElementById("composeTarget")
        ?: throw IllegalStateException("Root element 'composeTarget' not found")

    ComposeViewport(root) {
        WebAgentTheme {
            QAScreen()
        }
    }
}
