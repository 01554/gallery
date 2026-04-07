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

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

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

        // Build prompt from messages
        val prompt = buildPrompt(messages)

        // Parse sampling parameters from request (OpenAI-compatible names)
        val temperature = requestJson.optDouble("temperature", 0.7)
        val topK = requestJson.optInt("top_k", 64)
        val topP = requestJson.optDouble("top_p", 0.95)
        val enableThinking = requestJson.optBoolean("enable_thinking", false)

        // Estimate prompt tokens (rough: 1 token ~ 4 chars)
        val estimatedPromptTokens = prompt.length / 4

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

        // Run inference synchronously
        val result = StringBuilder()
        val thinking = StringBuilder()
        val latch = CountDownLatch(1)
        val tokenCount = AtomicInteger(0)
        var inferenceError: String? = null

        conversation.sendMessageAsync(
            Contents.of(listOf(Content.Text(prompt))),
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

        // Close conversation to free resources
        try {
            conversation.close()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to close conversation", e)
        }

        if (inferenceError != null) {
            return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                """{"error":"inference failed: $inferenceError"}"""
            )
        }

        val completionTokens = tokenCount.get()
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

    private fun buildPrompt(messages: JSONArray): String {
        // Extract the last user message as prompt
        for (i in messages.length() - 1 downTo 0) {
            val msg = messages.getJSONObject(i)
            if (msg.getString("role") == "user") {
                return msg.getString("content")
            }
        }
        return ""
    }
}
