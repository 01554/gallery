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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.R

private const val DEFAULT_MODEL_PATH = "/data/local/tmp/gemma-4-E2B-it.litertlm"
private const val DEFAULT_PORT = 8080

@Composable
fun ServerFloatingButton(
    modifier: Modifier = Modifier,
    modelPath: String = DEFAULT_MODEL_PATH,
    port: Int = DEFAULT_PORT,
) {
    val context = LocalContext.current
    val serverState by LlmServerService.state.collectAsState()
    val serverPort by LlmServerService.port.collectAsState()
    val modelName by LlmServerService.modelName.collectAsState()

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
                    ServerState.STOPPED, ServerState.ERROR -> startServer(context, modelPath, port)
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
    }
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
