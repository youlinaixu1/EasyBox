package com.easybox.app.ui.doudizhu

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easybox.app.domain.doudizhu.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DouDiZhuScreen(viewModel: DouDiZhuViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Lock landscape orientation
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val originalOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose {
            originalOrientation?.let { activity?.requestedOrientation = it }
        }
    }

    LaunchedEffect(uiState.game.phase) {
        if (uiState.mode == DMode.AI) viewModel.startAiGame()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("斗地主", fontSize = 16.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(6.dp)) {
            // Controls row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                FilterChip(uiState.mode == DMode.AI, { viewModel.setMode(DMode.AI) }, label = { Text("人机", fontSize = 11.sp) })
                FilterChip(uiState.mode == DMode.MULTIPLAYER, { viewModel.setMode(DMode.MULTIPLAYER) }, label = { Text("联机", fontSize = 11.sp) })
                if (uiState.game.phase == DPhase.FINISHED) {
                    Button(onClick = { viewModel.resetGame() }, modifier = Modifier.height(32.dp)) { Text("再来一局", fontSize = 12.sp) }
                }
                if (uiState.message.isNotEmpty()) {
                    Text(uiState.message, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary, maxLines = 1)
                }
            }

            // Bidding UI
            if (uiState.game.phase == DPhase.BIDDING && uiState.game.bidTurn == uiState.myPlayer) {
                Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.Center) {
                    Button(onClick = { viewModel.bid(DBid.THREE) }, Modifier.padding(2.dp).height(36.dp)) { Text("叫地主", fontSize = 13.sp) }
                    OutlinedButton(onClick = { viewModel.bid(DBid.NONE) }, Modifier.padding(2.dp).height(36.dp)) { Text("不叫", fontSize = 13.sp) }
                }
            }

            if (uiState.isThinking) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("AI 思考中...", fontSize = 12.sp)
                }
            }

            // Main game area: opponents + table
            Row(Modifier.fillMaxWidth().weight(0.45f)) {
                // Left: Player 2
                PlayerInfoBox(uiState, DPlayer.PLAYER_2, Modifier.weight(0.12f))

                // Center: Table
                Column(Modifier.weight(0.76f).padding(horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    // Bottom cards
                    if (uiState.game.landlord != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("底牌:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF795548))
                            Spacer(Modifier.width(4.dp))
                            CardRow(uiState.game.bottomCards, emptySet(), small = true) {}
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    // Last played cards
                    if (uiState.game.lastPlay != null) {
                        if (uiState.game.lastPlay!!.type == PlayType.PASS) {
                            Text("${uiState.game.lastPlayer?.name?.replace("_", " ") ?: ""} 不出",
                                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${uiState.game.lastPlayer?.name?.replace("_", " ") ?: ""} 出牌:",
                                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                                Spacer(Modifier.width(4.dp))
                                CardRow(uiState.game.lastPlay!!.cards, emptySet(), small = false) {}
                            }
                        }
                    }
                }

                // Right: Player 3
                PlayerInfoBox(uiState, DPlayer.PLAYER_3, Modifier.weight(0.12f))
            }

            // Action buttons
            if (uiState.game.phase == DPhase.PLAYING && uiState.game.currentPlayer == uiState.myPlayer) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.Center) {
                    Button(onClick = { viewModel.playSelectedCards() }, Modifier.padding(2.dp).height(36.dp),
                        enabled = uiState.selectedCards.isNotEmpty()) { Text("出牌", fontSize = 13.sp) }
                    Spacer(Modifier.width(12.dp))
                    OutlinedButton(onClick = { viewModel.pass() }, Modifier.padding(2.dp).height(36.dp),
                        enabled = uiState.game.lastPlay != null && uiState.game.lastPlayer != uiState.myPlayer) { Text("不出", fontSize = 13.sp) }
                }
                if (uiState.game.lastPlay != null && uiState.game.lastPlayer != uiState.myPlayer) {
                    Text("提示: 选牌后点\"出牌\"，或点\"不出\"", fontSize = 10.sp, color = Color.Gray,
                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
            }

            Spacer(Modifier.height(2.dp))

            // My hand cards - largest area
            val myHand = uiState.game.hands[uiState.myPlayer] ?: emptyList()
            Box(Modifier.fillMaxWidth().weight(0.5f).padding(horizontal = 2.dp)) {
                CardRow(myHand, uiState.selectedCards, small = false) { cardId ->
                    viewModel.toggleCard(cardId)
                }
            }
        }
    }
}

@Composable
fun PlayerInfoBox(uiState: DuiZhuUiState, player: DPlayer, modifier: Modifier = Modifier) {
    val hand = uiState.game.hands[player] ?: return
    val isLandlord = uiState.game.landlord == player
    val isCurrent = uiState.game.currentPlayer == player
    val borderColor = when {
        isCurrent && uiState.game.phase == DPhase.PLAYING -> MaterialTheme.colorScheme.primary
        isLandlord -> Color(0xFFFF9800)
        else -> Color.Transparent
    }

    Card(
        modifier.padding(2.dp),
        shape = RoundedCornerShape(6.dp),
        border = if (borderColor != Color.Transparent)
            androidx.compose.foundation.BorderStroke(1.5.dp, borderColor) else null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(4.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                when (player) {
                    DPlayer.PLAYER_1 -> "你"
                    DPlayer.PLAYER_2 -> "玩家2"
                    DPlayer.PLAYER_3 -> "玩家3"
                } + if (isLandlord) " 地主" else "",
                fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1
            )
            Text("${hand.size}张", fontSize = 10.sp)
        }
    }
}

@Composable
fun CardRow(cards: List<Card>, selected: Set<Int>, small: Boolean, onTap: (Int) -> Unit) {
    if (cards.isEmpty()) return

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(cards.sortedBy { it.rank }, key = { it.id }) { card ->
            CardView(card, selected = card.id in selected, small = small,
                modifier = Modifier.clickable { onTap(card.id) })
        }
    }
}

@Composable
fun CardView(card: Card, selected: Boolean, small: Boolean, modifier: Modifier = Modifier) {
    val isRed = card.suit == 1 || card.suit == 2 || card.id >= 52
    val cardColor = if (isRed) Color(0xFFC62828) else Color(0xFF212121)
    val cardW = if (small) 22.dp else 32.dp
    val cardH = if (small) 30.dp else 44.dp
    val rankSize = if (small) 9.sp else 12.sp
    val suitSize = if (small) 6.sp else 8.sp

    Box(
        modifier = modifier
            .width(cardW).height(cardH)
            .offset(y = if (selected) (-8).dp else 0.dp)
            .padding(horizontal = 1.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(if (selected) Color(0xFFBBDEFB) else Color.White),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color(0xFFBDBDBD), size = Size(size.width, size.height),
                style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (card.id >= 52) card.display else card.display.drop(1),
                fontSize = rankSize, fontWeight = FontWeight.Bold, color = cardColor,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (card.id < 52) {
                Text(card.display.take(1), fontSize = suitSize, color = cardColor)
            }
        }
    }
}
