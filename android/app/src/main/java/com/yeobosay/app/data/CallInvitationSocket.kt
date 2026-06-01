package com.yeobosay.app.data

import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject

data class IncomingCallEvent(
    val callInvitationId: String,
    val callerName: String,
    val message: String,
    val createdAt: String,
    val expiresAt: String,
)

class CallInvitationSocket(
    private val baseUrl: String = "http://10.0.2.2:3000",
) {
    private var socket: Socket? = null

    fun connect(
        onConnected: () -> Unit,
        onDisconnected: () -> Unit,
        onIncomingCall: (IncomingCallEvent) -> Unit,
        onError: (String) -> Unit,
    ) {
        if (socket?.connected() == true) return

        val options = IO.Options().apply {
            transports = arrayOf("websocket")
            reconnection = true
            reconnectionDelay = 1_000
        }
        val namespaceUrl = "${baseUrl.trimEnd('/')}/call-invitations"

        socket = IO.socket(namespaceUrl, options).apply {
            on(Socket.EVENT_CONNECT) { onConnected() }
            on(Socket.EVENT_DISCONNECT) { onDisconnected() }
            on(Socket.EVENT_CONNECT_ERROR) { args ->
                onError(args.firstOrNull()?.toString() ?: "전화 수신 서버에 연결할 수 없습니다.")
            }
            on("incoming_call") { args ->
                val payload = args.firstOrNull() as? JSONObject ?: return@on
                onIncomingCall(payload.toIncomingCallEvent())
            }
            connect()
        }
    }

    fun disconnect() {
        socket?.disconnect()
        socket?.off()
        socket = null
    }

    private fun JSONObject.toIncomingCallEvent(): IncomingCallEvent =
        IncomingCallEvent(
            callInvitationId = getString("callInvitationId"),
            callerName = optString("callerName", "YeoboSay"),
            message = optString("message", "전화가 왔어요."),
            createdAt = optString("createdAt"),
            expiresAt = optString("expiresAt"),
        )
}
