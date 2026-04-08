package com.google.ai.edge.gallery.server

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun ServerLogsScreen(
    onClose: () -> Unit,
) {
    val serverState by LlmServerService.state.collectAsState()
    val serverPort by LlmServerService.port.collectAsState()
    val modelName by LlmServerService.modelName.collectAsState()
    var logs by remember { mutableStateOf(listOf<String>()) }
    val listState = rememberLazyListState()

    // Poll logs from shared ServerLog (no network needed)
    LaunchedEffect(Unit) {
        while (true) {
            val text = ServerLog.get()
            logs = text.split("\n").filter { it.isNotBlank() }
            delay(500)
        }
    }

    // Auto-scroll
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                val dotColor = when (serverState) {
                    ServerState.STOPPED -> Color.Gray
                    ServerState.LOADING -> Color(0xFFFFB74D)
                    ServerState.RUNNING -> Color(0xFF66BB6A)
                    ServerState.ERROR -> Color(0xFFEF5350)
                }
                Icon(
                    Icons.Rounded.Circle,
                    contentDescription = null,
                    tint = dotColor,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Server Logs",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (serverState == ServerState.RUNNING) {
                    Text(
                        ":$serverPort",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close")
                }
            }

            // Model name
            if (modelName.isNotEmpty() && serverState != ServerState.STOPPED) {
                Text(
                    modelName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 36.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Log area - fills remaining space
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                if (logs.isEmpty()) {
                    Text(
                        "No logs yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                    ) {
                        items(logs) { line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                ),
                                color = when {
                                    line.contains("ERROR") -> MaterialTheme.colorScheme.error
                                    line.contains("done:") -> Color(0xFF66BB6A)
                                    line.contains("Server listening") -> Color(0xFF42A5F5)
                                    line.contains("Model loaded") -> Color(0xFF42A5F5)
                                    line.contains("Loading model") -> Color(0xFFFFB74D)
                                    line.contains("Server stopped") -> Color.Gray
                                    line.contains("prompt:") -> MaterialTheme.colorScheme.onSurface
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
