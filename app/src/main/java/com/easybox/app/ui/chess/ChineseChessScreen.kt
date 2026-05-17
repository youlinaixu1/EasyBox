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
import com.easybox.app.domain.chess.model.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChineseChessScreen(viewModel: ChineseChessViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    var showJoinDialog by remember { mutableStateOf(false) }
    var joinCode by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("中国象棋") },
                navigationIcon = { IconButton(onClick = {
                    viewModel.leaveMultiplayer(); onBack()
                }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // Controls
            GameControls(uiState, { viewModel.setGameMode(it) }, { viewModel.setDifficulty(it) },
                { viewModel.setPlayerColor(it) }, { viewModel.resetGame() },
                { viewModel.createRoom() }, { viewModel.joinRoom(it) }, { viewModel.leaveMultiplayer() })

            // Multiplayer info
            if (uiState.gameMode == GameMode.MULTIPLAYER && uiState.roomId.isNotEmpty()) {
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("房间号: ${uiState.roomId}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            IconButton(onClick = { clipboard.setText(AnnotatedString(uiState.roomId)) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.ContentCopy, "复制", Modifier.size(16.dp))
                            }
                        }
                        if (uiState.waitingOpponent) {
                            Text("等待对手加入...", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        } else {
                            Text("对手: ${uiState.opponentName}", fontSize = 13.sp)
                        }
                        TextButton(onClick = { viewModel.leaveMultiplayer() }) { Text("退出房间") }
                    }
                }
            }

            if (uiState.gameMode == GameMode.MULTIPLAYER && uiState.roomId.isEmpty()) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.createRoom() }, modifier = Modifier.weight(1f)) { Text("创建房间") }
                    OutlinedButton(onClick = { showJoinDialog = true }, modifier = Modifier.weight(1f)) { Text("加入房间") }
                }
            }

            Spacer(Modifier.height(4.dp))

            // Board
            Box(Modifier.fillMaxWidth().aspectRatio(0.92f), contentAlignment = Alignment.Center) {
                ChineseChessBoard(uiState.board, uiState.selectedPosition, uiState.validMoves, uiState.lastMove) {
                    viewModel.onCellClicked(it)
                }
            }

            // Status
            StatusBar(uiState)
        }
    }

    // Join dialog
    if (showJoinDialog) {
        AlertDialog(
            onDismissRequest = { showJoinDialog = false },
            title = { Text("加入房间") },
            text = {
                OutlinedTextField(joinCode, { joinCode = it.uppercase().take(6) },
                    label = { Text("房间号") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = {
                    if (joinCode.length == 6) { viewModel.joinRoom(joinCode); showJoinDialog = false; joinCode = "" }
                }) { Text("加入") }
            },
            dismissButton = { TextButton(onClick = { showJoinDialog = false }) { Text("取消") } }
        )
    }
}

@Composable
fun GameControls(
    uiState: ChineseChessUiState,
    onGameModeChange: (GameMode) -> Unit,
    onDifficultyChange: (AIDifficulty) -> Unit,
    onPlayerColorChange: (PieceColor) -> Unit,
    onReset: () -> Unit,
    onCreateRoom: () -> Unit,
    onJoinRoom: (String) -> Unit,
    onLeaveRoom: () -> Unit
) {
    val canChange = !uiState.isThinking
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            FilterChip(uiState.gameMode == GameMode.AI, { if (canChange) onGameModeChange(GameMode.AI) }, label = { Text("人机", fontSize = 12.sp) })
            FilterChip(uiState.gameMode == GameMode.MULTIPLAYER, { if (canChange) onGameModeChange(GameMode.MULTIPLAYER) }, label = { Text("联机", fontSize = 12.sp) })
            if (uiState.gameMode == GameMode.AI) {
                FilterChip(uiState.playerColor == PieceColor.RED, { if (canChange) onPlayerColorChange(PieceColor.RED) }, label = { Text("执红", fontSize = 12.sp) })
                FilterChip(uiState.playerColor == PieceColor.BLACK, { if (canChange) onPlayerColorChange(PieceColor.BLACK) }, label = { Text("执黑", fontSize = 12.sp) })
            }
        }
        if (uiState.gameMode == GameMode.AI) {
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                AIDifficulty.entries.forEach { diff ->
                    FilterChip(uiState.difficulty == diff, { if (canChange) onDifficultyChange(diff) },
                        label = { Text(diff.label, fontSize = 11.sp) }, modifier = Modifier.padding(horizontal = 2.dp))
                }
            }
        }
        if (uiState.gameStatus != GameStatus.PLAYING) {
            Spacer(Modifier.height(4.dp))
            Button(onClick = onReset, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                Text("再来一局")
            }
        }
    }
}

@Composable
fun ChineseChessBoard(
    board: Board, selectedPosition: Position?, validMoves: List<Position>, lastMove: Move?,
    onCellClick: (Position) -> Unit
) {
    val boardColor = Color(0xFFF5DEB3); val lineColor = Color(0xFF4A3728)
    val highlightColor = Color(0x6666BB6A); val lastMoveColor = Color(0x6690CAF9); val validMoveColor = Color(0x88FF9800)
    var boardSize by remember { mutableStateOf(IntSize.Zero) }

    Box(Modifier.fillMaxSize().onSizeChanged { boardSize = it }.background(boardColor, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp))
        .pointerInput(boardSize) {
            if (boardSize == IntSize.Zero) return@pointerInput
            detectTapGestures { tap ->
                val cw = boardSize.width / 9f; val ch = boardSize.height / 10f
                val px = cw / 2f; val py = ch / 2f
                onCellClick(Position(((tap.y - py) / ch).roundToInt().coerceIn(0, 9), ((tap.x - px) / cw).roundToInt().coerceIn(0, 8)))
            }
        }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (boardSize == IntSize.Zero) return@Canvas
            val cw = size.width / 9f; val ch = size.height / 10f; val px = cw / 2; val py = ch / 2
            for (col in 0..8) {
                val x = px + col * cw; val rt = py + 4 * ch; val rb = py + 5 * ch
                if (col == 0 || col == 8) drawLine(lineColor, Offset(x, py), Offset(x, py + 9 * ch), 2f)
                else { drawLine(lineColor, Offset(x, py), Offset(x, rt), 1.5f); drawLine(lineColor, Offset(x, rb), Offset(x, py + 9 * ch), 1.5f) }
            }
            for (row in 0..9) drawLine(lineColor, Offset(px, py + row * ch), Offset(px + 8 * cw, py + row * ch), 1.5f)
            // Palace
            val pl = px + 3 * cw; val pr = px + 5 * cw
            drawLine(lineColor, Offset(pl, py), Offset(pr, py + 2 * ch), 1f); drawLine(lineColor, Offset(pr, py), Offset(pl, py + 2 * ch), 1f)
            drawLine(lineColor, Offset(pl, py + 7 * ch), Offset(pr, py + 9 * ch), 1f); drawLine(lineColor, Offset(pr, py + 7 * ch), Offset(pl, py + 9 * ch), 1f)
            // Last move
            lastMove?.let {
                for (p in listOf(it.from, it.to)) drawCircle(lastMoveColor, cw * 0.38f, Offset(px + p.col * cw, py + p.row * ch))
            }
            for (p in validMoves) {
                val cx = px + p.col * cw; val cy = py + p.row * ch
                if (board.pieceAt(p) != null) drawCircle(validMoveColor, cw * 0.4f, Offset(cx, cy), style = Stroke(2.5f))
                else drawCircle(validMoveColor, cw * 0.12f, Offset(cx, cy))
            }
            selectedPosition?.let { drawCircle(highlightColor, cw * 0.4f, Offset(px + it.col * cw, py + it.row * ch), style = Stroke(2.5f)) }
        }
        if (boardSize != IntSize.Zero) {
            val cw = boardSize.width / 9f; val ch = boardSize.height / 10f; val px = cw / 2; val py = ch / 2
            val density = LocalDensity.current
            for (row in 0..9) for (col in 0..8) {
                board.pieceAt(row, col)?.let { piece ->
                    val cx = px + col * cw; val cy = py + row * ch
                    val ps = with(density) { (cw * 0.75f).toDp() }
                    Box(Modifier.offset(x = with(density) { cx.toDp() } - ps / 2, y = with(density) { cy.toDp() } - ps / 2).size(ps).clip(CircleShape).background(Color(0xFFFDF5E6), CircleShape), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawCircle(Color(0xFF8B7355), size.minDimension / 2, style = Stroke(1.5f))
                            drawCircle(Color(0xFFE8D5B7), size.minDimension / 2 - 2f)
                        }
                        Text(piece.symbol, fontSize = with(density) { (cw * 0.38f).toSp() }, fontWeight = FontWeight.Bold,
                            color = if (piece.color == PieceColor.RED) Color(0xFFC62828) else Color(0xFF212121), textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBar(uiState: ChineseChessUiState) {
    Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        when {
            uiState.gameStatus != GameStatus.PLAYING -> Text(
                when (uiState.gameStatus) { GameStatus.RED_WIN -> "红方获胜!"; GameStatus.BLACK_WIN -> "黑方获胜!"; else -> "平局!" },
                fontSize = 16.sp, fontWeight = FontWeight.Bold,
                color = if (uiState.gameStatus == GameStatus.RED_WIN) Color(0xFFC62828) else Color(0xFF212121)
            )
            uiState.isThinking -> { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("AI 思考中...", fontSize = 14.sp) }
            uiState.gameMode == GameMode.MULTIPLAYER -> {
                Text(if (uiState.isMyTurn) "你的回合" else "等待对手走棋", fontSize = 14.sp)
            }
            else -> Text("${if (uiState.currentPlayer == PieceColor.RED) "红方" else "黑方"} 走棋", fontSize = 14.sp)
        }
    }
}
