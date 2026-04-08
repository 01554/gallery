package com.google.ai.edge.gallery.server

import android.util.Log
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "LlmHttpServer"

class LlmHttpServer(
    private val engine: Engine,
    port: Int = 8080,
) : NanoHTTPD(port) {

    private fun addLog(msg: String) = ServerLog.add(msg)

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        if (uri != "/logs" && uri != "/health") {
            addLog("${method.name} $uri")
        }

        // CORS preflight
        if (method == Method.OPTIONS) {
            return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "").apply {
                addHeader("Access-Control-Allow-Origin", "*")
                addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
                addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
            }
        }

        return try {
            when {
                uri == "/health" || uri == "/v1/health" -> handleHealth()
                uri == "/v1/models" -> handleModels()
                (uri == "/v1/chat/completions" || uri == "/chat/completions") && method == Method.POST -> handleChatCompletions(session)
                uri == "/" -> handleRoot()
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json", """{"error":"not found"}""")
            }.apply {
                addHeader("Access-Control-Allow-Origin", "*")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling request", e)
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                """{"error":"${e.message?.replace("\"", "\\\"") ?: "unknown error"}"}"""
            )
        }
    }

    private fun handleRoot(): Response {
        return newFixedLengthResponse(Response.Status.OK, "application/json",
            """{"status":"ok","message":"LLM Server (LiteRT + GPU)","endpoints":["/v1/chat/completions","/v1/models","/health"]}""")
    }

    private fun handleHealth(): Response {
        return newFixedLengthResponse(Response.Status.OK, "application/json",
            """{"status":"ok"}""")
    }


    private fun handleModels(): Response {
        val json = JSONObject().apply {
            put("object", "list")
            put("data", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", "gemma")
                    put("object", "model")
                })
            })
        }
        return newFixedLengthResponse(Response.Status.OK, "application/json", json.toString())
    }

    private fun handleChatCompletions(session: IHTTPSession): Response {
        // Read body
        val contentLength = session.headers["content-length"]?.toIntOrNull() ?: 0
        val buf = ByteArray(contentLength)
        session.inputStream.read(buf, 0, contentLength)
        val body = String(buf)

        val requestJson = JSONObject(body)
        val messages = requestJson.getJSONArray("messages")

        // Build contents from messages (text + images)
        val (prompt, contents) = buildContents(messages)
        val imageCount = contents.count { it is Content.ImageBytes }
        val promptPreview = if (prompt.length > 60) prompt.take(60) + "..." else prompt
        addLog("prompt: \"$promptPreview\"" + if (imageCount > 0) " +${imageCount} image(s)" else "")

        // Parse sampling parameters from request (OpenAI-compatible names)
        val temperature = requestJson.optDouble("temperature", 0.7)
        val topK = requestJson.optInt("top_k", 64)
        val topP = requestJson.optDouble("top_p", 0.95)
        val enableThinking = requestJson.optBoolean("enable_thinking", false)

        // Estimate prompt tokens (rough: 1 token ~ 4 chars, + 256 per image)
        val estimatedPromptTokens = prompt.length / 4 + imageCount * 256

        // Create a fresh conversation for each request (stateless API)
        val conversation = engine.createConversation(
            ConversationConfig(
                samplerConfig = SamplerConfig(
                    topK = topK,
                    topP = topP,
                    temperature = temperature,
                )
            )
        )

        val extraContext = if (enableThinking) mapOf("enable_thinking" to "true") else emptyMap()

        // Run inference synchronously, always close conversation when done
        val result = StringBuilder()
        val thinking = StringBuilder()
        val latch = CountDownLatch(1)
        val tokenCount = AtomicInteger(0)
        var inferenceError: String? = null

        try {
            conversation.sendMessageAsync(
                Contents.of(contents),
                object : MessageCallback {
                    override fun onMessage(message: Message) {
                        val thought = message.channels["thought"]
                        if (thought != null) {
                            thinking.append(thought)
                        }
                        result.append(message.toString())
                        tokenCount.incrementAndGet()
                    }

                    override fun onDone() {
                        latch.countDown()
                    }

                    override fun onError(throwable: Throwable) {
                        Log.e(TAG, "Inference error", throwable)
                        inferenceError = throwable.message
                        latch.countDown()
                    }
                },
                extraContext,
            )

            // Wait for completion (timeout: 5 minutes)
            latch.await(5, TimeUnit.MINUTES)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to run inference", e)
            inferenceError = e.message
        } finally {
            // Always close conversation to prevent session leak
            try {
                conversation.close()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to close conversation", e)
            }
        }

        if (inferenceError != null) {
            addLog("ERROR: $inferenceError")
            return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                """{"error":"inference failed: $inferenceError"}"""
            )
        }

        val completionTokens = tokenCount.get()
        addLog("done: ${completionTokens} tokens")
        val responseJson = JSONObject().apply {
            put("id", "chatcmpl-${UUID.randomUUID().toString().take(8)}")
            put("object", "chat.completion")
            put("model", requestJson.optString("model", "gemma"))
            put("choices", JSONArray().apply {
                put(JSONObject().apply {
                    put("index", 0)
                    put("message", JSONObject().apply {
                        put("role", "assistant")
                        put("content", result.toString())
                        if (thinking.isNotEmpty()) {
                            put("reasoning_content", thinking.toString())
                        }
                    })
                    put("finish_reason", "stop")
                })
            })
            put("usage", JSONObject().apply {
                put("prompt_tokens", estimatedPromptTokens)
                put("completion_tokens", completionTokens)
                put("total_tokens", estimatedPromptTokens + completionTokens)
            })
        }

        return newFixedLengthResponse(Response.Status.OK, "application/json", responseJson.toString())
    }

    /**
     * Build a list of Content objects from OpenAI-compatible messages.
     * Returns (textPrompt, contentList) where textPrompt is the raw text for token estimation.
     *
     * Supports:
     * - Simple text: {"role": "user", "content": "Hello"}
     * - Multimodal:  {"role": "user", "content": [
     *     {"type": "text", "text": "What's in this image?"},
     *     {"type": "image_url", "image_url": {"url": "data:image/png;base64,..."}}
     *   ]}
     */
    private fun buildContents(messages: JSONArray): Pair<String, List<Content>> {
        val contents = mutableListOf<Content>()
        var textPrompt = ""

        // Find the last user message
        for (i in messages.length() - 1 downTo 0) {
            val msg = messages.getJSONObject(i)
            if (msg.getString("role") != "user") continue

            val contentField = msg.get("content")

            if (contentField is String) {
                // Simple text message
                textPrompt = contentField
                contents.add(Content.Text(contentField))
            } else if (contentField is JSONArray) {
                // Multimodal message with text and images
                // Add images first, then text (same order as gallery app)
                val textParts = mutableListOf<String>()
                val imageParts = mutableListOf<ByteArray>()

                for (j in 0 until contentField.length()) {
                    val part = contentField.getJSONObject(j)
                    when (part.getString("type")) {
                        "text" -> textParts.add(part.getString("text"))
                        "image_url" -> {
                            val imageUrl = part.getJSONObject("image_url").getString("url")
                            val imageBytes = decodeImageUrl(imageUrl)
                            if (imageBytes != null) {
                                imageParts.add(imageBytes)
                            }
                        }
                    }
                }

                // Images first, then text
                for (bytes in imageParts) {
                    contents.add(Content.ImageBytes(bytes))
                }
                textPrompt = textParts.joinToString("\n")
                if (textPrompt.isNotEmpty()) {
                    contents.add(Content.Text(textPrompt))
                }
            }
            break
        }

        if (contents.isEmpty()) {
            contents.add(Content.Text(""))
        }

        return Pair(textPrompt, contents)
    }

    private fun decodeImageUrl(url: String): ByteArray? {
        return try {
            if (url.startsWith("data:")) {
                // data:image/png;base64,iVBOR...
                val base64Data = url.substringAfter(",")
                android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
            } else {
                // Regular URL - download it
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.inputStream.readBytes()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode image: ${e.message}")
            null
        }
    }
}
