package com.google.ai.edge.gallery.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "LlmServerService"
private const val CHANNEL_ID = "llm_server_channel"
private const val NOTIFICATION_ID = 1
private const val DEFAULT_PORT = 8080

enum class ServerState { STOPPED, LOADING, RUNNING, ERROR }

class LlmServerService : Service() {

    private var httpServer: LlmHttpServer? = null
    private var engine: Engine? = null

    companion object {
        private val _state = MutableStateFlow(ServerState.STOPPED)
        val state: StateFlow<ServerState> = _state.asStateFlow()

        private val _port = MutableStateFlow(DEFAULT_PORT)
        val port: StateFlow<Int> = _port.asStateFlow()

        private val _modelName = MutableStateFlow("")
        val modelName: StateFlow<String> = _modelName.asStateFlow()

        private val _errorMessage = MutableStateFlow("")
        val errorMessage: StateFlow<String> = _errorMessage.asStateFlow()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val modelPath = intent?.getStringExtra("model_path") ?: run {
            Log.e(TAG, "No model_path provided")
            stopSelf()
            return START_NOT_STICKY
        }
        val port = intent.getIntExtra("port", DEFAULT_PORT)

        _state.value = ServerState.LOADING
        _port.value = port
        _modelName.value = modelPath.substringAfterLast("/")

        val notification = buildNotification("Loading model...")
        startForeground(NOTIFICATION_ID, notification)

        Thread {
            try {
                Log.i(TAG, "Loading model: $modelPath")
                val engineConfig = EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.GPU(),
                    maxNumTokens = 1024,
                    cacheDir = getExternalFilesDir(null)?.absolutePath,
                )
                val eng = Engine(engineConfig)
                eng.initialize()
                engine = eng

                val server = LlmHttpServer(eng, port)
                server.start()
                httpServer = server

                Log.i(TAG, "Server started on port $port")
                updateNotification("Server running on port $port")
                _state.value = ServerState.RUNNING
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start server", e)
                updateNotification("Error: ${e.message}")
                _state.value = ServerState.ERROR
                _errorMessage.value = e.message ?: "Unknown error"
            }
        }.start()

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        httpServer?.stop()
        engine?.close()
        httpServer = null
        engine = null
        _state.value = ServerState.STOPPED
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "LLM Server",
                NotificationManager.IMPORTANCE_LOW,
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("LLM Server")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = buildNotification(text)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }
}
