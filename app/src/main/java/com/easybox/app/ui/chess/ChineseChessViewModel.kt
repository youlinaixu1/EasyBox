package com.easybox.app.ui.chess

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.easybox.app.data.local.PreferencesManager
import com.easybox.app.domain.chess.AIDifficulty
import com.easybox.app.domain.chess.ChineseChessAI
import com.easybox.app.domain.chess.ChineseChessEngine
import com.easybox.app.domain.chess.model.*
import com.easybox.app.network.MqttMultiplayerManager
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class GameMode { AI, MULTIPLAYER }
enum class GameStatus { PLAYING, RED_WIN, BLACK_WIN, DRAW }

data class ChineseChessUiState(
    val board: Board = ChineseChessEngine.createInitialBoard(),
    val currentPlayer: PieceColor = PieceColor.RED,
    val selectedPosition: Position? = null,
    val validMoves: List<Position> = emptyList(),
    val gameMode: GameMode = GameMode.AI,
    val gameStatus: GameStatus = GameStatus.PLAYING,
    val difficulty: AIDifficulty = AIDifficulty.MEDIUM,
    val lastMove: Move? = null,
    val playerColor: PieceColor = PieceColor.RED,
    val isThinking: Boolean = false,
    // Multiplayer
    val myName: String = "",
    val roomId: String = "",
    val opponentName: String = "",
    val opponentColor: PieceColor = PieceColor.BLACK,
    val isMyTurn: Boolean = true,
    val waitingOpponent: Boolean = false
)

class ChineseChessViewModel(application: Application) : AndroidViewModel(application) {
    private val engine = ChineseChessEngine()
    private val prefs = PreferencesManager(application)
    private val multiplayer = MqttMultiplayerManager()
    private val gson = Gson()

    private val _uiState = MutableStateFlow(ChineseChessUiState())
    val uiState: StateFlow<ChineseChessUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { prefs.nickname.first().let { n ->
            _uiState.value = _uiState.value.copy(myName = n.ifBlank { "玩家" })
        }}
        viewModelScope.launch {
            multiplayer.room.collect { room ->
                if (room == null) return@collect
                val state = _uiState.value
                if (state.gameMode != GameMode.MULTIPLAYER) return@collect

                // Opponent joined
                if (state.waitingOpponent && room.status == "playing") {
                    _uiState.value = state.copy(
                        opponentName = room.player2Name,
                        opponentColor = if (room.player2Color == "red") PieceColor.RED else PieceColor.BLACK,
                        waitingOpponent = false
                    )
                }

                // Opponent made a move
                if (room.lastMoveJson.isNotEmpty() && room.currentPlayer == state.playerColor.name.lowercase()) {
                    try {
                        val moveData = gson.fromJson(room.lastMoveJson, Map::class.java)
                        val fromRow = (moveData["fromRow"] as Double).toInt()
                        val fromCol = (moveData["fromCol"] as Double).toInt()
                        val toRow = (moveData["toRow"] as Double).toInt()
                        val toCol = (moveData["toCol"] as Double).toInt()
                        val move = Move(Position(fromRow, fromCol), Position(toRow, toCol))
                        engine.makeMove(move)
                        _uiState.value = _uiState.value.copy(
                            board = engine.getBoard(),
                            currentPlayer = engine.currentPlayer,
                            lastMove = move,
                            gameStatus = checkGameEnd(),
                            isMyTurn = true
                        )
                    } catch (_: Exception) {}
                }
            }
        }
    }

    fun resetGame() {
        engine.reset()
        multiplayer.leaveRoom()
        _uiState.value = _uiState.value.copy(
            board = engine.getBoard(), gameMode = _uiState.value.gameMode,
            playerColor = _uiState.value.playerColor, difficulty = _uiState.value.difficulty,
            selectedPosition = null, validMoves = emptyList(), lastMove = null,
            gameStatus = GameStatus.PLAYING, isThinking = false,
            roomId = "", opponentName = "", waitingOpponent = false
        )
    }

    fun setGameMode(mode: GameMode) {
        _uiState.value = _uiState.value.copy(gameMode = mode)
        resetGame()
    }

    fun setDifficulty(d: AIDifficulty) {
        _uiState.value = _uiState.value.copy(difficulty = d)
        resetGame()
    }

    fun setPlayerColor(color: PieceColor) {
        val opp = if (color == PieceColor.RED) PieceColor.BLACK else PieceColor.RED
        _uiState.value = _uiState.value.copy(playerColor = color, opponentColor = opp)
        resetGame()
        if (color == PieceColor.BLACK && _uiState.value.gameMode == GameMode.AI) makeAiMove()
    }

    // ── Multiplayer ──
    fun createRoom() {
        val state = _uiState.value
        val colorName = state.playerColor.name.lowercase()
        multiplayer.createRoom("chinese_chess", state.myName, colorName,
            boardToJson(engine.getBoard()), engine.currentPlayer.name.lowercase())
        _uiState.value = state.copy(
            roomId = multiplayer.room.value?.roomId ?: "",
            waitingOpponent = true, isMyTurn = state.playerColor == PieceColor.RED
        )
        // Refresh roomId
        viewModelScope.launch {
            multiplayer.room.collect { r ->
                if (r != null) _uiState.value = _uiState.value.copy(roomId = r.roomId)
                return@collect
            }
        }
    }

    fun joinRoom(code: String) {
        multiplayer.joinRoom(code, _uiState.value.myName)
        _uiState.value = _uiState.value.copy(isMyTurn = false)
        viewModelScope.launch {
            multiplayer.room.collect { r ->
                if (r != null) {
                    val myColor = if (r.player2Color == "red") PieceColor.RED else PieceColor.BLACK
                    _uiState.value = _uiState.value.copy(
                        roomId = r.roomId,
                        playerColor = myColor,
                        opponentColor = if (myColor == PieceColor.RED) PieceColor.BLACK else PieceColor.RED,
                        opponentName = r.player1Name
                    )
                    if (r.boardJson.isNotEmpty()) {
                        val b = boardFromJson(r.boardJson)
                        engine.setBoard(b, PieceColor.valueOf(r.currentPlayer.uppercase()))
                        _uiState.value = _uiState.value.copy(
                            board = b, currentPlayer = engine.currentPlayer,
                            isMyTurn = _uiState.value.currentPlayer == _uiState.value.playerColor
                        )
                    }
                    return@collect
                }
            }
        }
    }

    fun leaveMultiplayer() {
        multiplayer.leaveRoom()
        _uiState.value = _uiState.value.copy(roomId = "", opponentName = "", waitingOpponent = false)
    }

    // ── Board click ──
    fun onCellClicked(position: Position) {
        val state = _uiState.value
        if (state.isThinking) return
        if (state.gameStatus != GameStatus.PLAYING) return
        if (state.gameMode == GameMode.AI && state.currentPlayer != state.playerColor) return
        if (state.gameMode == GameMode.MULTIPLAYER && (!state.isMyTurn || state.waitingOpponent)) return

        val piece = state.board.pieceAt(position)

        if (state.selectedPosition == null) {
            if (piece != null && piece.color == state.currentPlayer) {
                val moves = engine.getValidMoves(position)
                _uiState.value = state.copy(selectedPosition = position, validMoves = moves)
            }
        } else {
            if (position in state.validMoves) {
                val captured = state.board.pieceAt(position)
                val move = Move(state.selectedPosition, position, captured)
                engine.makeMove(move)
                val newStatus = checkGameEnd()

                _uiState.value = state.copy(
                    board = engine.getBoard(), currentPlayer = engine.currentPlayer,
                    selectedPosition = null, validMoves = emptyList(), lastMove = move,
                    gameStatus = newStatus, isMyTurn = state.gameMode == GameMode.AI || !state.isMyTurn
                )

                // Multiplayer: send move
                if (state.gameMode == GameMode.MULTIPLAYER && state.roomId.isNotEmpty()) {
                    val moveData = mapOf("fromRow" to move.from.row, "fromCol" to move.from.col,
                        "toRow" to move.to.row, "toCol" to move.to.col)
                    multiplayer.sendMove(boardToJson(engine.getBoard()),
                        engine.currentPlayer.name.lowercase(), gson.toJson(moveData))
                    _uiState.value = _uiState.value.copy(isMyTurn = false)
                }

                // AI response
                if (newStatus == GameStatus.PLAYING && state.gameMode == GameMode.AI) makeAiMove()
            } else if (piece != null && piece.color == state.currentPlayer) {
                val moves = engine.getValidMoves(position)
                _uiState.value = state.copy(selectedPosition = position, validMoves = moves)
            } else {
                _uiState.value = state.copy(selectedPosition = null, validMoves = emptyList())
            }
        }
    }

    private fun makeAiMove() {
        _uiState.value = _uiState.value.copy(isThinking = true)
        viewModelScope.launch(Dispatchers.Default) {
            delay(300)
            val ai = ChineseChessAI(_uiState.value.difficulty)
            val move = ai.getBestMove(engine)
            if (move != null) {
                engine.makeMove(move)
                _uiState.value = _uiState.value.copy(
                    board = engine.getBoard(), currentPlayer = engine.currentPlayer,
                    lastMove = move, isThinking = false, gameStatus = checkGameEnd()
                )
            } else {
                _uiState.value = _uiState.value.copy(isThinking = false)
            }
        }
    }

    private fun checkGameEnd(): GameStatus = when {
        engine.isGameOver() && engine.currentPlayer == PieceColor.RED -> GameStatus.BLACK_WIN
        engine.isGameOver() && engine.currentPlayer == PieceColor.BLACK -> GameStatus.RED_WIN
        else -> GameStatus.PLAYING
    }

    companion object {
        fun boardToJson(board: Board): String {
            val pieces = mutableListOf<Map<String, Any>>()
            for (r in 0..9) for (c in 0..8) board.grid[r][c]?.let { p ->
                pieces.add(mapOf("row" to r, "col" to c, "type" to p.type.name, "color" to p.color.name))
            }
            return Gson().toJson(pieces)
        }

        fun boardFromJson(json: String): Board {
            val board = Board()
            try {
                val list = Gson().fromJson(json, List::class.java) as? List<*> ?: return board
                for (item in list) {
                    val map = item as? Map<*, *> ?: continue
                    val r = (map["row"] as? Double)?.toInt() ?: continue
                    val c = (map["col"] as? Double)?.toInt() ?: continue
                    val type = PieceType.valueOf(map["type"] as? String ?: continue)
                    val color = PieceColor.valueOf(map["color"] as? String ?: continue)
                    board.grid[r][c] = Piece(type, color)
                }
            } catch (_: Exception) {}
            return board
        }
    }
}
