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
    val expiresAt: String,
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

class YeoboSayApi(
    private val baseUrl: String = "http://10.0.2.2:3000",
) {
    suspend fun createCallSession(): CallSessionResponse = withContext(Dispatchers.IO) {
        val response = requestJson("POST", "/call-sessions")
        CallSessionResponse(
            id = response.getString("id"),
            status = response.getString("status"),
            expiresAt = response.getString("expiresAt"),
        )
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
            val response = requestJson("POST", "/call-sessions/$callSessionId/end")
            CallSessionResponse(
                id = response.getString("id"),
                status = response.getString("status"),
                expiresAt = response.getString("expiresAt"),
            )
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

    private fun requestJson(method: String, path: String): JSONObject {
        val connection = openConnection(method, path)
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
