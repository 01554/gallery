package com.google.ai.edge.gallery.server

import android.util.Log
import com.google.ai.edge.gallery.data.Accelerator
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.RuntimeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "CommunityModelBrowser"
private const val HF_API_BASE = "https://huggingface.co/api/models"

data class CommunityModel(
    val id: String,
    val name: String,
    val fileName: String,
    val downloadUrl: String,
    val sizeInBytes: Long,
)

object CommunityModelBrowser {

    suspend fun fetchLitertModels(): List<CommunityModel> = withContext(Dispatchers.IO) {
        val models = mutableListOf<CommunityModel>()
        try {
            // Fetch all models from litert-community
            val listUrl = "$HF_API_BASE?author=litert-community&limit=100"
            val modelsJson = httpGet(listUrl) ?: return@withContext models
            val modelsArray = JSONArray(modelsJson)

            for (i in 0 until modelsArray.length()) {
                val modelObj = modelsArray.getJSONObject(i)
                val modelId = modelObj.getString("id")

                // Check if this model has .litertlm files
                try {
                    val treeUrl = "$HF_API_BASE/$modelId/tree/main"
                    val treeJson = httpGet(treeUrl) ?: continue
                    val files = JSONArray(treeJson)

                    val litertlmFiles = mutableListOf<Pair<String, Long>>()
                    for (j in 0 until files.length()) {
                        val file = files.getJSONObject(j)
                        val path = file.getString("path")
                        if (path.endsWith(".litertlm")) {
                            litertlmFiles.add(Pair(path, file.getLong("size")))
                        }
                    }

                    for ((path, size) in litertlmFiles) {
                        if (path.endsWith(".litertlm")) {
                            val baseName = modelId.removePrefix("litert-community/")
                            // If multiple .litertlm files exist, use filename to distinguish
                            val name = if (litertlmFiles.size > 1) {
                                path.removeSuffix(".litertlm")
                            } else {
                                baseName
                            }
                            val downloadUrl = "https://huggingface.co/$modelId/resolve/main/$path"
                            models.add(
                                CommunityModel(
                                    id = modelId,
                                    name = name,
                                    fileName = path,
                                    downloadUrl = downloadUrl,
                                    sizeInBytes = size,
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to check files for $modelId", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch litert-community models", e)
        }
        models.sortedBy { it.name }
    }

    fun toGalleryModel(community: CommunityModel): Model {
        return Model(
            name = community.name,
            displayName = community.name,
            url = community.downloadUrl,
            downloadFileName = community.fileName,
            sizeInBytes = community.sizeInBytes,
            isLlm = true,
            runtimeType = RuntimeType.LITERT_LM,
            info = "From litert-community on HuggingFace",
        )
    }

    private fun httpGet(urlStr: String): String? {
        return try {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader().readText()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "HTTP GET failed: $urlStr", e)
            null
        }
    }
}
