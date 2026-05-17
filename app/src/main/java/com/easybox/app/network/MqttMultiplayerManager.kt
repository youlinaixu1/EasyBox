package com.easybox.app.network

import com.easybox.app.data.model.RoomInfo
import com.google.gson.Gson
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class MqttMultiplayerManager {
    private val gson = Gson()
    private var client: Mqtt3AsyncClient? = null
    private var roomId: String = ""
    private var myName: String = ""

    private val _room = MutableStateFlow<RoomInfo?>(null)
    val room: StateFlow<RoomInfo?> = _room.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private fun connect(callback: () -> Unit) {
        client = MqttClient.builder()
            .useMqttVersion3()
            .identifier("easybox_" + UUID.randomUUID().toString().take(8))
            .serverHost("broker.emqx.io")
            .serverPort(1883)
            .buildAsync()
        client!!.connect().whenComplete { _, th ->
            if (th != null) { _error.value = "连接失败: ${th.message}"; return@whenComplete }
            callback()
        }
    }

    fun createRoom(gameType: String, myName: String, myColor: String, boardJson: String, currentPlayer: String) {
        this.myName = myName
        roomId = UUID.randomUUID().toString().take(6).uppercase()
        connect {
            val info = RoomInfo(roomId = roomId, gameType = gameType, player1Name = myName,
                player1Color = myColor, boardJson = boardJson, currentPlayer = currentPlayer)
            // Subscribe to room topic
            client!!.subscribeWith()
                .topicFilter("easybox/$roomId/#")
                .callback { msg -> handleMessage(msg.topic.toString(), String(msg.payloadAsBytes)) }
                .send()
            // Publish room info
            publish("easybox/$roomId/room", gson.toJson(info))
            _room.value = info
        }
    }

    fun joinRoom(code: String, myName: String) {
        this.myName = myName
        roomId = code.uppercase()
        connect {
            client!!.subscribeWith()
                .topicFilter("easybox/$roomId/#")
                .callback { msg -> handleMessage(msg.topic.toString(), String(msg.payloadAsBytes)) }
                .send()
            // Request room info by publishing join request
            publish("easybox/$roomId/join", gson.toJson(mapOf("name" to myName)))
        }
    }

    fun sendMove(boardJson: String, currentPlayer: String, moveJson: String) {
        publish("easybox/$roomId/move", gson.toJson(mapOf(
            "boardJson" to boardJson, "currentPlayer" to currentPlayer, "moveJson" to moveJson,
            "from" to myName)))
    }

    fun leaveRoom() {
        client?.disconnect()
        client = null; roomId = ""
        _room.value = null
    }

    fun clearError() { _error.value = null }

    private fun handleMessage(topic: String, payload: String) {
        try {
            when {
                topic.endsWith("/room") -> {
                    val info = gson.fromJson(payload, RoomInfo::class.java)
                    _room.value = info
                }
                topic.endsWith("/join") -> {
                    val data = gson.fromJson(payload, Map::class.java)
                    val name = data["name"] as? String ?: return
                    val info = _room.value ?: return
                    val myColor = if (info.player1Color == "red") "black"
                        else if (info.player1Color == "black") "red"
                        else if (info.player1Color == "white") "black" else "white"
                    val updated = info.copy(player2Name = name, player2Color = myColor, status = "playing")
                    publish("easybox/$roomId/room", gson.toJson(updated))
                    _room.value = updated
                }
                topic.endsWith("/move") -> {
                    val data = gson.fromJson(payload, Map::class.java)
                    val info = _room.value ?: return
                    val sender = data["from"] as? String ?: return
                    if (sender == myName) return  // Own move, already applied locally
                    val updated = info.copy(
                        boardJson = data["boardJson"] as? String ?: info.boardJson,
                        currentPlayer = data["currentPlayer"] as? String ?: info.currentPlayer,
                        lastMoveJson = data["moveJson"] as? String ?: "",
                        status = "playing"
                    )
                    _room.value = updated
                }
            }
        } catch (_: Exception) {}
    }

    private fun publish(topic: String, payload: String) {
        client?.publishWith()?.topic(topic)?.payload(payload.toByteArray())?.send()
    }
}
