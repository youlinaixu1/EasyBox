package com.easybox.app.data.model

data class RoomInfo(
    val roomId: String = "",
    val gameType: String = "",
    val player1Name: String = "",
    val player1Color: String = "",
    val player2Name: String = "",
    val player2Color: String = "",
    val boardJson: String = "",
    val currentPlayer: String = "",
    val lastMoveJson: String = "",
    val status: String = "waiting"  // waiting, playing, finished
)
