package com.yeobosay.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class CallSessionResponse(
    val id: String,
    val status: String,
    val mode: String,
    val currentStep: String?,
    val turnCount: Int,
    val targetTurnCount: Int,
    val riskFlag: Boolean,
    val riskType: String?,
    val startedAt: String?,
    val endedAt: String?,
    val expiresAt: String,
    val audioPolicy: AudioPolicyResponse?,
    val conversationPolicy: ConversationPolicyResponse?,
)

data class AudioPolicyResponse(
    val silenceTimeoutMs: Int,
    val maxUtteranceMs: Int,
    val uploadMimeType: String,
    val bargeInEnabled: Boolean,
)

data class ConversationPolicyResponse(
    val firstGreetingText: String,
    val noResponsePromptText: String,
    val maxDurationClosingText: String,
    val targetTurnCount: Int,
    val maxDurationSeconds: Int,
)

data class ConversationTurnResponse(
    val id: String,
    val role: String,
    val text: String,
    val failed: Boolean,
    val riskFlag: Boolean,
    val riskType: String?,
)

data class VoiceTurnResponse(
    val callSessionId: String,
    val userText: String,
    val assistantText: String,
    val audioMimeType: String?,
    val audioBase64: String?,
    val failed: Boolean,
    val riskFlag: Boolean,
    val riskType: String?,
)

data class CallInvitationResponse(
    val id: String,
    val status: String,
    val callerName: String,
    val message: String,
    val createdAt: String,
    val expiresAt: String,
    val acceptedAt: String?,
    val declinedAt: String?,
)

enum class CallSessionMode(val apiValue: String) {
    ManualRecording("manual_recording"),
    AutoConversation("auto_conversation"),
}

class YeoboSayApi(
    private val baseUrl: String = "http://10.0.2.2:3000",
) {
    suspend fun createCallSession(
        mode: CallSessionMode = CallSessionMode.ManualRecording,
        source: String? = null,
        callInvitationId: String? = null,
    ): CallSessionResponse = withContext(Dispatchers.IO) {
        val requestBody = JSONObject().apply {
            put("mode", mode.apiValue)
            source?.let { put("source", it) }
            callInvitationId?.let { put("callInvitationId", it) }
        }
        requestJson("POST", "/call-sessions", requestBody).toCallSessionResponse()
    }

    suspend fun getTurns(callSessionId: String): List<ConversationTurnResponse> =
        withContext(Dispatchers.IO) {
            val response = requestJson("GET", "/call-sessions/$callSessionId/turns")
            val turns = response.optJSONArray("turns") ?: JSONArray()
            List(turns.length()) { index ->
                val item = turns.getJSONObject(index)
                ConversationTurnResponse(
                    id = item.getString("id"),
                    role = item.getString("role"),
                    text = item.getString("text"),
                    failed = item.optBoolean("failed", false),
                    riskFlag = item.optBoolean("riskFlag", false),
                    riskType = item.optString("riskType").ifBlank { null },
                )
            }
        }

    suspend fun endCallSession(callSessionId: String): CallSessionResponse =
        withContext(Dispatchers.IO) {
            requestJson("POST", "/call-sessions/$callSessionId/end").toCallSessionResponse()
        }

    suspend fun createTestCallInvitation(): CallInvitationResponse = withContext(Dispatchers.IO) {
        requestJson("POST", "/call-invitations/test").toCallInvitationResponse()
    }

    suspend fun acceptCallInvitation(callInvitationId: String): CallInvitationResponse =
        withContext(Dispatchers.IO) {
            requestJson("POST", "/call-invitations/$callInvitationId/accept")
                .toCallInvitationResponse()
        }

    suspend fun declineCallInvitation(callInvitationId: String): CallInvitationResponse =
        withContext(Dispatchers.IO) {
            requestJson("POST", "/call-invitations/$callInvitationId/decline")
                .toCallInvitationResponse()
        }

    suspend fun uploadAudioTurn(callSessionId: String, audioFile: File): VoiceTurnResponse =
        withContext(Dispatchers.IO) {
            val boundary = "YeoboSayBoundary${System.currentTimeMillis()}"
            val connection = openConnection(
                method = "POST",
                path = "/call-sessions/$callSessionId/turns/audio",
            ).apply {
                doOutput = true
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            }

            BufferedOutputStream(connection.outputStream).use { output ->
                output.write("--$boundary\r\n".toByteArray())
                output.write(
                    "Content-Disposition: form-data; name=\"audio\"; filename=\"speech.m4a\"\r\n"
                        .toByteArray(),
                )
                output.write("Content-Type: audio/mp4\r\n\r\n".toByteArray())
                audioFile.inputStream().use { it.copyTo(output) }
                output.write("\r\n--$boundary--\r\n".toByteArray())
            }

            val response = readResponse(connection)
            VoiceTurnResponse(
                callSessionId = response.getString("callSessionId"),
                userText = response.optString("userText"),
                assistantText = response.optString("assistantText"),
                audioMimeType = response.optString("audioMimeType").ifBlank { null },
                audioBase64 = response.optString("audioBase64").ifBlank { null },
                failed = response.optBoolean("failed", false),
                riskFlag = response.optBoolean("riskFlag", false),
                riskType = response.optString("riskType").ifBlank { null },
            )
        }

    private fun JSONObject.toCallInvitationResponse(): CallInvitationResponse =
        CallInvitationResponse(
            id = getString("id"),
            status = getString("status"),
            callerName = optString("callerName", "YeoboSay"),
            message = optString("message", "전화가 왔어요."),
            createdAt = optString("createdAt"),
            expiresAt = optString("expiresAt"),
            acceptedAt = optString("acceptedAt").ifBlank { null },
            declinedAt = optString("declinedAt").ifBlank { null },
        )

    private fun JSONObject.toCallSessionResponse(): CallSessionResponse =
        CallSessionResponse(
            id = getString("id"),
            status = getString("status"),
            mode = optString("mode", CallSessionMode.ManualRecording.apiValue),
            currentStep = optString("currentStep").ifBlank { null },
            turnCount = optInt("turnCount", 0),
            targetTurnCount = optInt("targetTurnCount", 5),
            riskFlag = optBoolean("riskFlag", false),
            riskType = optString("riskType").ifBlank { null },
            startedAt = optString("startedAt").ifBlank { null },
            endedAt = optString("endedAt").ifBlank { null },
            expiresAt = getString("expiresAt"),
            audioPolicy = optJSONObject("audioPolicy")?.toAudioPolicyResponse(),
            conversationPolicy = optJSONObject("conversationPolicy")?.toConversationPolicyResponse(),
        )

    private fun JSONObject.toAudioPolicyResponse(): AudioPolicyResponse =
        AudioPolicyResponse(
            silenceTimeoutMs = optInt("silenceTimeoutMs", 3_000),
            maxUtteranceMs = optInt("maxUtteranceMs", 30_000),
            uploadMimeType = optString("uploadMimeType", "audio/mp4"),
            bargeInEnabled = optBoolean("bargeInEnabled", true),
        )

    private fun JSONObject.toConversationPolicyResponse(): ConversationPolicyResponse =
        ConversationPolicyResponse(
            firstGreetingText = optString("firstGreetingText"),
            noResponsePromptText = optString("noResponsePromptText"),
            maxDurationClosingText = optString("maxDurationClosingText"),
            targetTurnCount = optInt("targetTurnCount", 5),
            maxDurationSeconds = optInt("maxDurationSeconds", 600),
        )

    private fun requestJson(method: String, path: String, body: JSONObject? = null): JSONObject {
        val connection = openConnection(method, path)
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.bufferedWriter().use { it.write(body.toString()) }
        }
        return readResponse(connection)
    }

    private fun openConnection(method: String, path: String): HttpURLConnection {
        val normalizedBaseUrl = baseUrl.trimEnd('/')
        return (URL("$normalizedBaseUrl$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("Accept", "application/json")
        }
    }

    private fun readResponse(connection: HttpURLConnection): JSONObject {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            val message = runCatching { JSONObject(body).optString("message") }.getOrNull()
            throw IllegalStateException(message?.takeIf { it.isNotBlank() } ?: "HTTP $code")
        }
        return JSONObject(body)
    }
}
