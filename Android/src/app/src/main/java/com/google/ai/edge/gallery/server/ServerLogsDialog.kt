package com.google.ai.edge.gallery.server

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun ServerLogsDialog(onDismiss: () -> Unit) {
    val serverState by LlmServerService.state.collectAsState()
    val serverPort by LlmServerService.port.collectAsState()
    var logs by remember { mutableStateOf(listOf<String>()) }
    var fetchError by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Poll logs from the HTTP server every second
    LaunchedEffect(serverState, serverPort) {
        if (serverState == ServerState.RUNNING) {
            while (true) {
                try {
                    val text = fetchLogs(serverPort)
                    if (text != null) {
                        logs = text.split("\n").filter { it.isNotBlank() }
                        fetchError = ""
                    } else {
                        fetchError = "Failed to fetch logs from :$serverPort"
                    }
                } catch (e: Exception) {
                    fetchError = "Error: ${e.message}"
                }
                delay(1000)
            }
        }
    }

    // Auto-scroll to bottom when new logs arrive
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Server Logs")
                val stateText = when (serverState) {
                    ServerState.STOPPED -> "Stopped"
                    ServerState.LOADING -> "Loading..."
                    ServerState.RUNNING -> "Running"
                    ServerState.ERROR -> "Error"
                }
                Text(
                    stateText,
                    style = MaterialTheme.typography.labelMedium,
                    color = when (serverState) {
                        ServerState.RUNNING -> MaterialTheme.colorScheme.primary
                        ServerState.ERROR -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        },
        text = {
            if (serverState != ServerState.RUNNING) {
                Text(
                    "Server is not running. Start the server from the API Server button to see logs.",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else if (logs.isEmpty()) {
                Text(
                    if (fetchError.isNotEmpty()) fetchError
                    else "No logs yet. Send a request to see activity.",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .height(400.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(8.dp),
                ) {
                    items(logs) { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                            ),
                            color = when {
                                line.contains("ERROR") -> MaterialTheme.colorScheme.error
                                line.contains("done:") -> MaterialTheme.colorScheme.primary
                                line.contains("prompt:") -> MaterialTheme.colorScheme.onSurface
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

private fun fetchLogs(port: Int): String? {
    return try {
        val conn = java.net.URL("http://127.0.0.1:$port/logs").openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 2000
        conn.readTimeout = 2000
        if (conn.responseCode == 200) {
            conn.inputStream.bufferedReader().readText()
        } else null
    } catch (_: Exception) { null }
}
