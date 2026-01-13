package com.github.alphapaca.claudeclient.presentation.codesession

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File
import javax.swing.JFileChooser

@Composable
fun CreateCodeSessionDialog(
    onDismiss: () -> Unit,
    onCreate: (repoPath: String) -> Unit,
) {
    var repoPath by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("New Code Session")
        },
        text = {
            Column {
                Text(
                    "Enter the path to a git repository to index for code search.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = repoPath,
                        onValueChange = {
                            repoPath = it
                            error = null
                        },
                        label = { Text("Repository Path") },
                        placeholder = { Text("/path/to/your/project") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        isError = error != null,
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedButton(
                        onClick = {
                            val chooser = JFileChooser().apply {
                                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                                dialogTitle = "Select Repository Folder"
                            }

                            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                                repoPath = chooser.selectedFile.absolutePath
                                error = null
                            }
                        },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("Browse")
                    }
                }

                error?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "The codebase will be indexed for semantic search. " +
                    "A .code-index folder will be created in the repository.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val validation = validateRepoPath(repoPath)
                    if (validation != null) {
                        error = validation
                    } else {
                        onCreate(repoPath)
                    }
                },
                enabled = repoPath.isNotBlank(),
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private fun validateRepoPath(path: String): String? {
    if (path.isBlank()) {
        return "Please enter a repository path"
    }

    val dir = File(path)
    if (!dir.exists()) {
        return "Directory does not exist"
    }

    if (!dir.isDirectory) {
        return "Path is not a directory"
    }

    // Check if it's a git repo (optional but recommended)
    val gitDir = File(dir, ".git")
    if (!gitDir.exists()) {
        // Just a warning, not an error - user might want to index non-git folders
        return null
    }

    return null
}
