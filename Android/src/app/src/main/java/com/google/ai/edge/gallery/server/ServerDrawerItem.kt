package com.google.ai.edge.gallery.server

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.data.Model
import java.io.File

private const val DEFAULT_PORT = 8080

@Composable
fun ServerDrawerItem(
    modifier: Modifier = Modifier,
    downloadedModels: List<Model> = emptyList(),
    port: Int = DEFAULT_PORT,
    onStarted: () -> Unit = {},
) {
    val context = LocalContext.current
    val serverState by LlmServerService.state.collectAsState()
    val serverPort by LlmServerService.port.collectAsState()
    val modelName by LlmServerService.modelName.collectAsState()

    var showModelPicker by remember { mutableStateOf(false) }

    val borderColor by animateColorAsState(
        targetValue = when (serverState) {
            ServerState.STOPPED -> MaterialTheme.colorScheme.surfaceContainerHigh
            ServerState.LOADING -> Color(0xFFFFB74D)
            ServerState.RUNNING -> Color(0xFF66BB6A)
            ServerState.ERROR -> Color(0xFFEF5350)
        },
        animationSpec = tween(500),
        label = "serverBorderColor",
    )

    val iconColor by animateColorAsState(
        targetValue = when (serverState) {
            ServerState.STOPPED -> MaterialTheme.colorScheme.onSurfaceVariant
            ServerState.LOADING -> Color(0xFFFFB74D)
            ServerState.RUNNING -> Color(0xFF66BB6A)
            ServerState.ERROR -> Color(0xFFEF5350)
        },
        animationSpec = tween(500),
        label = "serverIconColor",
    )

    val statusText = when (serverState) {
        ServerState.STOPPED -> "Tap to start localhost API server"
        ServerState.LOADING -> "Loading $modelName..."
        ServerState.RUNNING -> "Running on :$serverPort - Tap to stop"
        ServerState.ERROR -> "Error - Tap to retry"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable {
                when (serverState) {
                    ServerState.STOPPED, ServerState.ERROR -> {
                        val available = getAvailableModels(context, downloadedModels)
                        if (available.size == 1) {
                            startServer(context, available.first().second, port)
                            onStarted()
                        } else if (available.isNotEmpty()) {
                            showModelPicker = true
                        } else {
                            Toast
                                .makeText(context, "No models found. Download a model first.", Toast.LENGTH_LONG)
                                .show()
                        }
                    }

                    ServerState.RUNNING -> {
                        stopServer(context)
                        onStarted()
                    }

                    ServerState.LOADING -> {
                        Toast
                            .makeText(context, "Model is loading...", Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            }
            .border(
                width = 2.dp,
                color = borderColor,
                shape = RoundedCornerShape(24.dp),
            )
            .padding(18.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Cloud,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = iconColor,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "API Server",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                )
                Text(
                    statusText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    if (showModelPicker) {
        val available = getAvailableModels(context, downloadedModels)
        AlertDialog(
            onDismissRequest = { showModelPicker = false },
            title = { Text("Select Model") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for ((name, path) in available) {
                        TextButton(onClick = {
                            showModelPicker = false
                            startServer(context, path, port)
                            onStarted()
                        }) {
                            Text(name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showModelPicker = false }) { Text("Cancel") }
            },
        )
    }
}

private fun getAvailableModels(context: Context, downloadedModels: List<Model>): List<Pair<String, String>> {
    val models = mutableListOf<Pair<String, String>>()
    for (model in downloadedModels) {
        val path = model.getPath(context)
        if (File(path).exists()) {
            models.add(Pair(model.name, path))
        }
    }
    try {
        val tmpDir = File("/data/local/tmp")
        if (tmpDir.exists()) {
            tmpDir.listFiles()?.filter { it.name.endsWith(".litertlm") }?.forEach { file ->
                val name = file.nameWithoutExtension
                if (models.none { it.first == name }) {
                    models.add(Pair(name, file.absolutePath))
                }
            }
        }
    } catch (_: Exception) {}
    return models
}

private fun startServer(context: Context, modelPath: String, port: Int) {
    val intent = Intent(context, LlmServerService::class.java).apply {
        putExtra("model_path", modelPath)
        putExtra("port", port)
    }
    context.startForegroundService(intent)
    Toast.makeText(context, "Starting server...", Toast.LENGTH_SHORT).show()
}

private fun stopServer(context: Context) {
    context.stopService(Intent(context, LlmServerService::class.java))
    Toast.makeText(context, "Server stopped", Toast.LENGTH_SHORT).show()
}
