package com.easybox.app.ui.chess

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easybox.app.domain.chess.AIDifficulty
import com.easybox.app.domain.international.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InternationalChessScreen(viewModel: InternationalChessViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    var showJoinDialog by remember { mutableStateOf(false) }
    var joinCode by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("国际象棋") },
                navigationIcon = { IconButton(onClick = { viewModel.leaveMultiplayer(); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary, navigationIconContentColor = MaterialTheme.colorScheme.onPrimary)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            IGameControls(uiState, { viewModel.setGameMode(it) }, { viewModel.setDifficulty(it) },
                { viewModel.setPlayerColor(it) }, { viewModel.resetGame() },
                { viewModel.createRoom() }, { viewModel.joinRoom(it) }, { viewModel.leaveMultiplayer() })

            if (uiState.gameMode == IGameMode.MULTIPLAYER && uiState.roomId.isNotEmpty()) {
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("房间号: ${uiState.roomId}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            IconButton(onClick = { clipboard.setText(AnnotatedString(uiState.roomId)) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.ContentCopy, "复制", Modifier.size(16.dp))
                            }
                        }
                        if (uiState.waitingOpponent) Text("等待对手加入...", fontSize = 13.sp)
                        else Text("对手: ${uiState.opponentName}", fontSize = 13.sp)
                        TextButton(onClick = { viewModel.leaveMultiplayer() }) { Text("退出房间") }
                    }
                }
            }

            if (uiState.gameMode == IGameMode.MULTIPLAYER && uiState.roomId.isEmpty()) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.createRoom() }, modifier = Modifier.weight(1f)) { Text("创建房间") }
                    OutlinedButton(onClick = { showJoinDialog = true }, modifier = Modifier.weight(1f)) { Text("加入房间") }
                }
            }

            Spacer(Modifier.height(4.dp))

            Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                IChessBoard(uiState.board, uiState.selectedPosition, uiState.validMoves, uiState.lastMove) { viewModel.onCellClicked(it) }
            }

            IStatusBar(uiState)
        }
    }

    if (showJoinDialog) {
        AlertDialog(
            onDismissRequest = { showJoinDialog = false },
            title = { Text("加入房间") },
            text = { OutlinedTextField(joinCode, { joinCode = it.uppercase().take(6) }, label = { Text("房间号") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { if (joinCode.length == 6) { viewModel.joinRoom(joinCode); showJoinDialog = false; joinCode = "" } }) { Text("加入") } },
            dismissButton = { TextButton(onClick = { showJoinDialog = false }) { Text("取消") } }
        )
    }
}

@Composable
fun IGameControls(
    uiState: InternationalChessUiState,
    onGameModeChange: (IGameMode) -> Unit, onDifficultyChange: (AIDifficulty) -> Unit, onPlayerColorChange: (IColor) -> Unit,
    onReset: () -> Unit, onCreateRoom: () -> Unit, onJoinRoom: (String) -> Unit, onLeaveRoom: () -> Unit
) {
    val can = !uiState.isThinking
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            FilterChip(uiState.gameMode == IGameMode.AI, { if (can) onGameModeChange(IGameMode.AI) }, label = { Text("人机", fontSize = 12.sp) })
            FilterChip(uiState.gameMode == IGameMode.MULTIPLAYER, { if (can) onGameModeChange(IGameMode.MULTIPLAYER) }, label = { Text("联机", fontSize = 12.sp) })
            if (uiState.gameMode == IGameMode.AI) {
                FilterChip(uiState.playerColor == IColor.WHITE, { if (can) onPlayerColorChange(IColor.WHITE) }, label = { Text("执白", fontSize = 12.sp) })
                FilterChip(uiState.playerColor == IColor.BLACK, { if (can) onPlayerColorChange(IColor.BLACK) }, label = { Text("执黑", fontSize = 12.sp) })
            }
        }
        if (uiState.gameMode == IGameMode.AI) {
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                AIDifficulty.entries.forEach { d ->
                    FilterChip(uiState.difficulty == d, { if (can) onDifficultyChange(d) }, label = { Text(d.label, fontSize = 11.sp) }, modifier = Modifier.padding(horizontal = 2.dp))
                }
            }
        }
        if (uiState.gameStatus != IGameStatus.PLAYING) {
            Spacer(Modifier.height(4.dp))
            Button(onClick = onReset, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text("再来一局") }
        }
    }
}

@Composable
fun IChessBoard(board: IBoard, selectedPosition: IPosition?, validMoves: List<IPosition>, lastMove: IMove?, onCellClick: (IPosition) -> Unit) {
    val light = Color(0xFFF0D9B5); val dark = Color(0xFFB58863)
    val highlight = Color(0x6666BB6A); val lastMv = Color(0x6690CAF9); val valid = Color(0x88FF9800)
    var bs by remember { mutableStateOf(IntSize.Zero) }

    Box(Modifier.fillMaxSize().onSizeChanged { bs = it }.clip(RoundedCornerShape(8.dp))
        .pointerInput(bs) {
            if (bs == IntSize.Zero) return@pointerInput
            detectTapGestures { tap ->
                val cs = bs.width / 8f
                onCellClick(IPosition((tap.y / cs).toInt().coerceIn(0, 7), (tap.x / cs).toInt().coerceIn(0, 7)))
            }
        }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (bs == IntSize.Zero) return@Canvas
            val cs = size.width / 8f
            for (r in 0..7) for (c in 0..7) drawRect(if ((r + c) % 2 == 0) light else dark, Offset(c * cs, r * cs), Size(cs, cs))
            lastMove?.let {
                for (p in listOf(it.from, it.to)) drawRect(lastMv, Offset(p.col * cs, p.row * cs), Size(cs, cs))
            }
            for (p in validMoves) {
                val cx = p.col * cs + cs / 2; val cy = p.row * cs + cs / 2
                if (board.pieceAt(p) != null) drawCircle(valid, cs * 0.42f, Offset(cx, cy), style = Stroke(3f))
                else drawCircle(valid, cs * 0.13f, Offset(cx, cy))
            }
            selectedPosition?.let { drawRect(highlight, Offset(it.col * cs, it.row * cs), Size(cs, cs)) }
        }
        if (bs != IntSize.Zero) {
            val cs = bs.width / 8f; val density = LocalDensity.current
            for (r in 0..7) for (c in 0..7) {
                board.pieceAt(r, c)?.let { piece ->
                    val ps = with(density) { (cs * 0.85f).toDp() }
                    Box(Modifier.offset(x = with(density) { (c * cs + cs / 2).toDp() } - ps / 2,
                        y = with(density) { (r * cs + cs / 2).toDp() } - ps / 2).size(ps).clip(CircleShape),
                        contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawCircle(Color.White, size.minDimension / 2 - 3f)
                            drawCircle(Color(0xFF555555), size.minDimension / 2, style = Stroke(2f))
                        }
                        Text(piece.symbol, fontSize = with(density) { (cs * 0.42f).toSp() }, textAlign = TextAlign.Center, color = Color(0xFF212121))
                    }
                }
            }
        }
    }
}

@Composable
fun IStatusBar(uiState: InternationalChessUiState) {
    Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        when {
            uiState.gameStatus != IGameStatus.PLAYING -> Text(
                when (uiState.gameStatus) { IGameStatus.WHITE_WIN -> "白方获胜!"; IGameStatus.BLACK_WIN -> "黑方获胜!"; else -> "平局!" },
                fontSize = 16.sp, fontWeight = FontWeight.Bold)
            uiState.isThinking -> { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("AI 思考中...", fontSize = 14.sp) }
            uiState.gameMode == IGameMode.MULTIPLAYER -> Text(if (uiState.isMyTurn) "你的回合" else "等待对手走棋", fontSize = 14.sp)
            else -> Text("${if (uiState.currentPlayer == IColor.WHITE) "白方" else "黑方"} 走棋", fontSize = 14.sp)
        }
    }
}
