package com.google.ai.edge.gallery.server

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.data.Model
import java.io.File

private const val DEFAULT_PORT = 8080

@Composable
fun ServerFloatingButton(
    modifier: Modifier = Modifier,
    downloadedModels: List<Model> = emptyList(),
    port: Int = DEFAULT_PORT,
) {
    val context = LocalContext.current
    val serverState by LlmServerService.state.collectAsState()
    val serverPort by LlmServerService.port.collectAsState()
    val modelName by LlmServerService.modelName.collectAsState()

    var showModelPicker by remember { mutableStateOf(false) }

    val buttonColor by animateColorAsState(
        targetValue = when (serverState) {
            ServerState.STOPPED -> MaterialTheme.colorScheme.surfaceVariant
            ServerState.LOADING -> Color(0xFFFFB74D) // orange
            ServerState.RUNNING -> Color(0xFF66BB6A) // green
            ServerState.ERROR -> Color(0xFFEF5350)   // red
        },
        animationSpec = tween(500),
        label = "serverButtonColor",
    )

    val iconTint = when (serverState) {
        ServerState.STOPPED -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> Color.White
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        FloatingActionButton(
            onClick = {
                when (serverState) {
                    ServerState.STOPPED, ServerState.ERROR -> {
                        // Collect available models: downloaded via app + files in /data/local/tmp
                        val available = getAvailableModels(context, downloadedModels)
                        if (available.size == 1) {
                            startServer(context, available.first().second, port)
                        } else if (available.isNotEmpty()) {
                            showModelPicker = true
                        } else {
                            Toast.makeText(context, "No models found. Download a model first or push .litertlm to /data/local/tmp/", Toast.LENGTH_LONG).show()
                        }
                    }
                    ServerState.RUNNING -> stopServer(context)
                    ServerState.LOADING -> {
                        Toast.makeText(context, "Model is loading...", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            containerColor = buttonColor,
            shape = CircleShape,
            modifier = Modifier.size(56.dp),
        ) {
            Icon(
                painter = painterResource(
                    if (serverState == ServerState.RUNNING) android.R.drawable.ic_media_pause
                    else android.R.drawable.ic_media_play
                ),
                contentDescription = "Server toggle",
                tint = iconTint,
            )
        }
        if (serverState == ServerState.RUNNING) {
            Text(
                ":$serverPort",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (serverState == ServerState.LOADING) {
            Text(
                modelName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showModelPicker) {
        val available = getAvailableModels(context, downloadedModels)
        ModelPickerDialog(
            models = available,
            onSelect = { path ->
                showModelPicker = false
                startServer(context, path, port)
            },
            onDismiss = { showModelPicker = false },
        )
    }
}

@Composable
private fun ModelPickerDialog(
    models: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Model") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for ((name, path) in models) {
                    TextButton(onClick = { onSelect(path) }) {
                        Text(name, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun getAvailableModels(context: Context, downloadedModels: List<Model>): List<Pair<String, String>> {
    val models = mutableListOf<Pair<String, String>>()

    // 1. Models downloaded by the gallery app
    for (model in downloadedModels) {
        val path = model.getPath(context)
        if (File(path).exists()) {
            models.add(Pair(model.name, path))
        }
    }

    // 2. Models in /data/local/tmp/
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
