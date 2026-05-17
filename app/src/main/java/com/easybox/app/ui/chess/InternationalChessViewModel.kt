package com.easybox.app.ui.chess

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.easybox.app.data.local.PreferencesManager
import com.easybox.app.domain.chess.AIDifficulty
import com.easybox.app.domain.international.InternationalChessAI
import com.easybox.app.domain.international.InternationalChessEngine
import com.easybox.app.domain.international.model.*
import com.easybox.app.network.MqttMultiplayerManager
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class IGameMode { AI, MULTIPLAYER }
enum class IGameStatus { PLAYING, WHITE_WIN, BLACK_WIN, DRAW }

data class InternationalChessUiState(
    val board: IBoard = InternationalChessEngine.createInitialBoard(),
    val currentPlayer: IColor = IColor.WHITE,
    val selectedPosition: IPosition? = null,
    val validMoves: List<IPosition> = emptyList(),
    val gameMode: IGameMode = IGameMode.AI,
    val gameStatus: IGameStatus = IGameStatus.PLAYING,
    val difficulty: AIDifficulty = AIDifficulty.MEDIUM,
    val lastMove: IMove? = null,
    val playerColor: IColor = IColor.WHITE,
    val isThinking: Boolean = false,
    val myName: String = "",
    val roomId: String = "",
    val opponentName: String = "",
    val opponentColor: IColor = IColor.BLACK,
    val isMyTurn: Boolean = true,
    val waitingOpponent: Boolean = false
)

class InternationalChessViewModel(application: Application) : AndroidViewModel(application) {
    private val engine = InternationalChessEngine()
    private val prefs = PreferencesManager(application)
    private val multiplayer = MqttMultiplayerManager()
    private val gson = Gson()

    private val _uiState = MutableStateFlow(InternationalChessUiState())
    val uiState: StateFlow<InternationalChessUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { prefs.nickname.first().let { n ->
            _uiState.value = _uiState.value.copy(myName = n.ifBlank { "玩家" })
        }}
        viewModelScope.launch {
            multiplayer.room.collect { room ->
                if (room == null) return@collect
                val state = _uiState.value
                if (state.gameMode != IGameMode.MULTIPLAYER) return@collect
                if (state.waitingOpponent && room.status == "playing") {
                    _uiState.value = state.copy(opponentName = room.player2Name,
                        opponentColor = if (room.player2Color == "white") IColor.WHITE else IColor.BLACK, waitingOpponent = false)
                }
                if (room.lastMoveJson.isNotEmpty() && room.currentPlayer == state.playerColor.name.lowercase()) {
                    try {
                        val m = gson.fromJson(room.lastMoveJson, Map::class.java)
                        val move = IMove(IPosition((m["fromRow"] as Double).toInt(), (m["fromCol"] as Double).toInt()),
                            IPosition((m["toRow"] as Double).toInt(), (m["toCol"] as Double).toInt()))
                        engine.makeMove(move)
                        _uiState.value = _uiState.value.copy(board = engine.getBoard(), currentPlayer = engine.currentPlayer,
                            lastMove = move, gameStatus = checkGameEnd(), isMyTurn = true)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    fun resetGame() { engine.reset(); multiplayer.leaveRoom()
        _uiState.value = _uiState.value.copy(board = engine.getBoard(), selectedPosition = null, validMoves = emptyList(),
            lastMove = null, gameStatus = IGameStatus.PLAYING, isThinking = false, roomId = "", opponentName = "", waitingOpponent = false) }

    fun setGameMode(mode: IGameMode) { _uiState.value = _uiState.value.copy(gameMode = mode); resetGame() }
    fun setDifficulty(d: AIDifficulty) { _uiState.value = _uiState.value.copy(difficulty = d); resetGame() }

    fun setPlayerColor(color: IColor) {
        _uiState.value = _uiState.value.copy(playerColor = color, opponentColor = if (color == IColor.WHITE) IColor.BLACK else IColor.WHITE)
        resetGame()
        if (color == IColor.BLACK && _uiState.value.gameMode == IGameMode.AI) makeAiMove()
    }

    fun createRoom() {
        val state = _uiState.value
        multiplayer.createRoom("international_chess", state.myName, state.playerColor.name.lowercase(),
            boardToJson(engine.getBoard()), engine.currentPlayer.name.lowercase())
        _uiState.value = state.copy(roomId = multiplayer.room.value?.roomId ?: "", waitingOpponent = true,
            isMyTurn = state.playerColor == IColor.WHITE)
        viewModelScope.launch {
            multiplayer.room.collect { r -> if (r != null) _uiState.value = _uiState.value.copy(roomId = r.roomId); return@collect }
        }
    }

    fun joinRoom(code: String) {
        multiplayer.joinRoom(code, _uiState.value.myName)
        viewModelScope.launch {
            multiplayer.room.collect { r ->
                if (r != null) {
                    val myColor = if (r.player2Color == "white") IColor.WHITE else IColor.BLACK
                    _uiState.value = _uiState.value.copy(roomId = r.roomId, playerColor = myColor,
                        opponentColor = if (myColor == IColor.WHITE) IColor.BLACK else IColor.WHITE, opponentName = r.player1Name)
                    if (r.boardJson.isNotEmpty()) {
                        val b = boardFromJson(r.boardJson)
                        engine.setBoard(b, IColor.valueOf(r.currentPlayer.uppercase()))
                        _uiState.value = _uiState.value.copy(board = b, currentPlayer = engine.currentPlayer,
                            isMyTurn = _uiState.value.currentPlayer == _uiState.value.playerColor)
                    }
                    return@collect
                }
            }
        }
    }

    fun leaveMultiplayer() { multiplayer.leaveRoom(); _uiState.value = _uiState.value.copy(roomId = "", opponentName = "", waitingOpponent = false) }

    fun onCellClicked(position: IPosition) {
        val state = _uiState.value
        if (state.isThinking || state.gameStatus != IGameStatus.PLAYING) return
        if (state.gameMode == IGameMode.AI && state.currentPlayer != state.playerColor) return
        if (state.gameMode == IGameMode.MULTIPLAYER && (!state.isMyTurn || state.waitingOpponent)) return

        val piece = state.board.pieceAt(position)

        if (state.selectedPosition == null) {
            if (piece != null && piece.color == state.currentPlayer) {
                _uiState.value = state.copy(selectedPosition = position, validMoves = engine.getValidMoves(position))
            }
        } else {
            if (position in state.validMoves) {
                val captured = state.board.pieceAt(position)
                val promo = if (piece != null && piece.type == IType.PAWN && (position.row == 0 || position.row == 7)) IType.QUEEN else null
                val move = IMove(state.selectedPosition, position, captured, promo)
                engine.makeMove(move)
                val newStatus = checkGameEnd()
                _uiState.value = state.copy(board = engine.getBoard(), currentPlayer = engine.currentPlayer,
                    selectedPosition = null, validMoves = emptyList(), lastMove = move, gameStatus = newStatus,
                    isMyTurn = state.gameMode == IGameMode.AI || !state.isMyTurn)

                if (state.gameMode == IGameMode.MULTIPLAYER && state.roomId.isNotEmpty()) {
                    multiplayer.sendMove(boardToJson(engine.getBoard()), engine.currentPlayer.name.lowercase(),
                        gson.toJson(mapOf("fromRow" to move.from.row, "fromCol" to move.from.col, "toRow" to move.to.row, "toCol" to move.to.col)))
                    _uiState.value = _uiState.value.copy(isMyTurn = false)
                }
                if (newStatus == IGameStatus.PLAYING && state.gameMode == IGameMode.AI) makeAiMove()
            } else if (piece != null && piece.color == state.currentPlayer) {
                _uiState.value = state.copy(selectedPosition = position, validMoves = engine.getValidMoves(position))
            } else {
                _uiState.value = state.copy(selectedPosition = null, validMoves = emptyList())
            }
        }
    }

    private fun makeAiMove() {
        _uiState.value = _uiState.value.copy(isThinking = true)
        viewModelScope.launch(Dispatchers.Default) {
            delay(300)
            val move = InternationalChessAI(_uiState.value.difficulty).getBestMove(engine)
            if (move != null) {
                engine.makeMove(move)
                _uiState.value = _uiState.value.copy(board = engine.getBoard(), currentPlayer = engine.currentPlayer,
                    lastMove = move, isThinking = false, gameStatus = checkGameEnd())
            } else _uiState.value = _uiState.value.copy(isThinking = false)
        }
    }

    private fun checkGameEnd(): IGameStatus = when {
        engine.isGameOver() && engine.currentPlayer == IColor.WHITE -> IGameStatus.BLACK_WIN
        engine.isGameOver() && engine.currentPlayer == IColor.BLACK -> IGameStatus.WHITE_WIN
        else -> IGameStatus.PLAYING
    }

    companion object {
        fun boardToJson(board: IBoard): String {
            val pieces = mutableListOf<Map<String, Any>>()
            for (r in 0..7) for (c in 0..7) board.grid[r][c]?.let { p ->
                pieces.add(mapOf("row" to r, "col" to c, "type" to p.type.name, "color" to p.color.name))
            }
            return Gson().toJson(pieces)
        }
        fun boardFromJson(json: String): IBoard {
            val board = IBoard()
            try {
                (Gson().fromJson(json, List::class.java) as? List<*>)?.forEach {
                    val m = it as? Map<*, *> ?: return@forEach
                    val r = (m["row"] as? Double)?.toInt() ?: return@forEach
                    val c = (m["col"] as? Double)?.toInt() ?: return@forEach
                    board.grid[r][c] = IPiece(IType.valueOf(m["type"] as? String ?: return@forEach),
                        IColor.valueOf(m["color"] as? String ?: return@forEach))
                }
            } catch (_: Exception) {}
            return board
        }
    }
}
