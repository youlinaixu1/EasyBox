package com.easybox.app.ui.doudizhu

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.easybox.app.data.local.PreferencesManager
import com.easybox.app.domain.doudizhu.*
import com.easybox.app.network.MqttMultiplayerManager
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class DuiZhuUiState(
    val game: DGameState = DouDiZhuEngine().initGame(),
    val myPlayer: DPlayer = DPlayer.PLAYER_1,
    val selectedCards: Set<Int> = emptySet(),
    val mode: DMode = DMode.AI,
    val isThinking: Boolean = false,
    val message: String = "",
    val myName: String = "",
    val roomId: String = "",
    val opponentName2: String = "",
    val opponentName3: String = "",
    val waitingOpponents: Boolean = false,
    val isMyTurn: Boolean = true
)

enum class DMode { AI, MULTIPLAYER }

class DouDiZhuViewModel(application: Application) : AndroidViewModel(application) {
    private val engine = DouDiZhuEngine()
    private val ai = DouDiZhuAI()
    private val prefs = PreferencesManager(application)
    private val multiplayer = MqttMultiplayerManager()
    private val gson = Gson()

    private val _uiState = MutableStateFlow(DuiZhuUiState())
    val uiState: StateFlow<DuiZhuUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { prefs.nickname.first().let { n ->
            _uiState.value = _uiState.value.copy(myName = n.ifBlank { "玩家" })
        }}
    }

    fun toggleCard(cardId: Int) {
        val state = _uiState.value
        val sel = state.selectedCards.toMutableSet()
        if (cardId in sel) sel.remove(cardId) else sel.add(cardId)
        _uiState.value = state.copy(selectedCards = sel)
    }

    fun playSelectedCards() {
        val state = _uiState.value
        val myHand = state.game.hands[state.myPlayer] ?: return
        val selected = myHand.filter { it.id in state.selectedCards }
        val play = detectPlay(selected)
        if (play == null) { _uiState.value = state.copy(message = "无效牌型"); return }

        val lastPlay = if (state.game.lastPlayer == state.myPlayer) null else state.game.lastPlay
        val isNewRound = state.game.lastPlay == null || state.game.lastPlayer == state.myPlayer

        if (!isPlayValid(play, if (isNewRound) null else lastPlay, isNewRound)) {
            _uiState.value = state.copy(message = "管不上，请重新选择")
            return
        }

        val newGame = engine.playCards(state.game, state.myPlayer, play)
        _uiState.value = state.copy(
            game = newGame, selectedCards = emptySet(),
            message = if (newGame.winner != null) getWinMessage(newGame) else ""
        )

        if (state.mode == DMode.AI && newGame.phase == DPhase.PLAYING) makeAiMoves()
    }

    fun pass() {
        val state = _uiState.value
        val newGame = engine.playCards(state.game, state.myPlayer, null)
        _uiState.value = state.copy(game = newGame, selectedCards = emptySet())
        if (state.mode == DMode.AI && newGame.phase == DPhase.PLAYING && newGame.currentPlayer != state.myPlayer)
            makeAiMoves()
    }

    fun bid(bid: DBid) {
        val state = _uiState.value
        val newGame = engine.placeBid(state.game, state.myPlayer, bid)
        _uiState.value = state.copy(game = newGame, message = if (newGame.landlord != null) {
            "地主: ${if (newGame.landlord == state.myPlayer) "你" else "AI"}"
        } else "")

        if (state.mode == DMode.AI && newGame.phase == DPhase.PLAYING &&
            newGame.currentPlayer != state.myPlayer) makeAiMoves()
        if (state.mode == DMode.AI && newGame.phase == DPhase.BIDDING &&
            newGame.bidTurn != state.myPlayer) makeAiBid()
    }

    private fun makeAiBid() {
        val state = _uiState.value
        val aiPlayer = state.game.bidTurn ?: return
        val hand = state.game.hands[aiPlayer] ?: return
        val aiBid = ai.shouldBid(hand)
        viewModelScope.launch {
            delay(500)
            val newGame = engine.placeBid(state.game, aiPlayer, aiBid)
            _uiState.value = _uiState.value.copy(game = newGame,
                message = if (newGame.landlord != null) "地主: ${if (newGame.landlord == state.myPlayer) "你" else "AI"}" else "")
            if (newGame.phase == DPhase.PLAYING && newGame.currentPlayer != state.myPlayer) makeAiMoves()
            if (newGame.phase == DPhase.BIDDING && newGame.bidTurn != state.myPlayer) makeAiBid()
        }
    }

    private fun makeAiMoves() {
        _uiState.value = _uiState.value.copy(isThinking = true)
        viewModelScope.launch(Dispatchers.Default) {
            delay(600)
            var safety = 0
            while (safety < 50) {
                safety++
                val state = _uiState.value
                if (state.game.phase != DPhase.PLAYING) break
                val cur = state.game.currentPlayer
                if (cur == state.myPlayer) break

                val hand = state.game.hands[cur] ?: break
                val isNewRound = state.game.lastPlay == null || state.game.lastPlayer == cur
                val play = ai.pickPlay(hand, if (isNewRound) null else state.game.lastPlay, isNewRound, engine)
                    ?: engine.getAllValidPlays(hand, null, true).firstOrNull { it.type != PlayType.PASS }
                    ?: Play(emptyList(), PlayType.PASS, 0)

                val newGame = engine.playCards(state.game, cur, play)
                // If state didn't change, force skip to next player to break deadlock
                if (newGame.currentPlayer == cur && newGame.phase == DPhase.PLAYING) {
                    _uiState.value = state.copy(
                        game = state.game.copy(
                            currentPlayer = engine.nextPlayer(cur),
                            lastPlay = null, lastPlayer = null, passCount = 0
                        ),
                        isThinking = true
                    )
                    delay(300)
                    continue
                }

                val msg = when {
                    play.type == PlayType.PASS -> "${cur.name.replace("_"," ")} 不出"
                    newGame.winner != null -> getWinMessage(newGame)
                    else -> ""
                }
                _uiState.value = state.copy(game = newGame, isThinking = true, message = msg)

                if (newGame.currentPlayer == state.myPlayer || newGame.phase != DPhase.PLAYING) break
                delay(400)
            }

            _uiState.value = _uiState.value.copy(isThinking = false,
                message = if (_uiState.value.game.winner != null) getWinMessage(_uiState.value.game) else "")
        }
    }

    private fun getWinMessage(game: DGameState): String {
        val landlord = game.landlord ?: return ""
        val winner = game.winner ?: return ""
        val me = _uiState.value.myPlayer
        return if (winner == me) "你赢了!"
        else if (winner == landlord) "地主赢了!" else "农民赢了!"
    }

    fun resetGame() {
        _uiState.value = DuiZhuUiState(myPlayer = _uiState.value.myPlayer,
            mode = _uiState.value.mode, myName = _uiState.value.myName)
    }

    fun setMode(mode: DMode) { _uiState.value = _uiState.value.copy(mode = mode); resetGame() }

    fun startAiGame() {
        val state = _uiState.value
        if (state.game.phase == DPhase.BIDDING && state.game.bidTurn != state.myPlayer) makeAiBid()
        if (state.game.phase == DPhase.PLAYING && state.game.currentPlayer != state.myPlayer) makeAiMoves()
    }
}
