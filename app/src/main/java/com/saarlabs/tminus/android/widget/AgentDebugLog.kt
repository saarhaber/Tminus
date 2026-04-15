package com.saarlabs.tminus.android.widget

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Session debug NDJSON via host ingest (emulator: 10.0.2.2; device + adb reverse: 127.0.0.1).
 */
internal object AgentDebugLog {
    private const val SESSION_ID = "98223b"
    private const val PATH = "/ingest/01d16e3d-efd5-4d3c-bcc6-26e9472ab260"
    private val baseUrls =
        listOf(
            "http://10.0.2.2:7603",
            "http://127.0.0.1:7603",
        )
    private val executor = Executors.newSingleThreadExecutor()

    fun log(
        location: String,
        message: String,
        hypothesisId: String,
        data: Map<String, Any?>,
    ) {
        executor.execute {
            // #region agent log
            try {
                val payload =
                    JSONObject().apply {
                        put("sessionId", SESSION_ID)
                        put("location", location)
                        put("message", message)
                        put("timestamp", System.currentTimeMillis())
                        put("hypothesisId", hypothesisId)
                        put("data", JSONObject(data))
                    }
                val body = payload.toString().toByteArray(Charsets.UTF_8)
                for (host in baseUrls) {
                    try {
                        val conn =
                            (URL("$host$PATH").openConnection() as HttpURLConnection).apply {
                                requestMethod = "POST"
                                connectTimeout = 2500
                                readTimeout = 2500
                                setRequestProperty("Content-Type", "application/json")
                                setRequestProperty("X-Debug-Session-Id", SESSION_ID)
                                doOutput = true
                                outputStream.use { it.write(body) }
                            }
                        val code = conn.responseCode
                        conn.disconnect()
                        if (code in 200..299) return@execute
                    } catch (_: Exception) {
                        // try next host
                    }
                }
            } catch (_: Exception) {
            }
            // #endregion
        }
    }
}
