package com.google.ai.edge.gallery.server

import java.util.concurrent.ConcurrentLinkedDeque

object ServerLog {
    private val buffer = ConcurrentLinkedDeque<String>()
    private const val MAX_LINES = 100

    fun add(msg: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        buffer.addLast("[$ts] $msg")
        while (buffer.size > MAX_LINES) buffer.pollFirst()
    }

    fun get(): String = buffer.joinToString("\n")

    fun clear() = buffer.clear()
}
