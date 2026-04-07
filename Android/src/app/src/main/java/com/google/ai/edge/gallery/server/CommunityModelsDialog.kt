package com.google.ai.edge.gallery.server

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun CommunityModelsDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var models by remember { mutableStateOf<List<CommunityModel>?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        models = CommunityModelBrowser.fetchLitertModels()
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Community Models (litert-community)") },
        text = {
            if (loading) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Loading models from HuggingFace...", style = MaterialTheme.typography.bodySmall)
                }
            } else if (models.isNullOrEmpty()) {
                Text("No .litertlm models found.")
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(400.dp),
                ) {
                    items(models!!) { model ->
                        CommunityModelRow(model = model, context = context)
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

@Composable
private fun CommunityModelRow(
    model: CommunityModel,
    context: Context,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                model.name,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "%.1f GB".format(model.sizeInBytes / 1e9),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedButton(
            onClick = {
                downloadWithManager(context, model)
            },
        ) {
            Text("DL", style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun downloadWithManager(context: Context, model: CommunityModel) {
    try {
        val request = DownloadManager.Request(Uri.parse(model.downloadUrl))
            .setTitle(model.name)
            .setDescription("Downloading ${model.fileName}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, model.fileName)

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
        Toast.makeText(
            context,
            "Downloading ${model.name}. After download, push to /data/local/tmp/ or use from Downloads.",
            Toast.LENGTH_LONG,
        ).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
