package com.easybox.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.easybox.app.data.model.AppItem
import com.easybox.app.data.model.GameType
import kotlin.math.roundToInt

private val implementedApps = setOf("chinese_chess", "international_chess", "dou_di_zhu", "spinner")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel, onAppClick: (String) -> Unit, onPluginClick: (String) -> Unit = {}) {
    val appItems by viewModel.appItems.collectAsState()
    val showNicknameDialog by viewModel.showNicknameDialog.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddMenu by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var newAppName by remember { mutableStateOf("") }
    var newPluginUrl by remember { mutableStateOf("") }
    var newPluginDesc by remember { mutableStateOf("") }
    var isDragging by remember { mutableStateOf(false) }
    var dragOverDelete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.showNicknamePrompt() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("EasyBox", fontWeight = FontWeight.Bold) },
                actions = {
                    Box {
                        IconButton(onClick = { if (!isDragging) showAddMenu = true }) {
                            Icon(
                                imageVector = if (isDragging && dragOverDelete) Icons.Default.Delete else Icons.Default.Add,
                                contentDescription = if (isDragging) "拖到此处删除" else "添加",
                                tint = if (isDragging && dragOverDelete) Color.Red
                                else MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                            DropdownMenuItem(text = { Text("添加拓展插件") }, onClick = {
                                showAddMenu = false; showAddDialog = true
                            }, leadingIcon = { Icon(Icons.Default.Widgets, null) })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        if (appItems.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            DraggableAppGrid(
                items = appItems,
                onAppClick = { id ->
                    if (id in implementedApps) onAppClick(id)
                    else {
                        val item = appItems.firstOrNull { it.id == id }
                        if (item?.type == "plugin") onPluginClick(item.id)
                        else coroutineScope.launch { snackbarHostState.showSnackbar("即将上线") }
                    }
                },
                onReorder = { viewModel.updateSortOrders(it) },
                onDelete = { viewModel.removeApp(it) },
                isDragging = isDragging,
                onDraggingChanged = { isDragging = it },
                dragOverDelete = dragOverDelete,
                onDragOverDeleteChanged = { dragOverDelete = it },
                modifier = Modifier.padding(padding)
            )
        }
    }

    if (showNicknameDialog) NicknameDialog(
        onConfirm = { viewModel.setNickname(it) },
        onDismiss = { viewModel.dismissNicknameDialog() }
    )
    if (showAddDialog) AlertDialog(
        onDismissRequest = { showAddDialog = false },
        title = { Text("添加拓展插件") },
        text = {
            Column {
                OutlinedTextField(newAppName, { if (it.length <= 16) newAppName = it },
                    label = { Text("插件名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(newPluginUrl, { newPluginUrl = it },
                    label = { Text("网址（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(newPluginDesc, { if (it.length <= 50) newPluginDesc = it },
                    label = { Text("描述（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (newAppName.isNotBlank()) {
                    val name = newAppName.trim()
                    val metaJson = buildString {
                        append("{")
                        append("\"url\":\"${newPluginUrl.trim()}\"")
                        append(",\"desc\":\"${newPluginDesc.trim()}\"")
                        append("}")
                    }
                    viewModel.addPlugin(AppItem(
                        id = "plugin_" + java.util.UUID.randomUUID().toString().take(8),
                        name = name, type = "plugin", category = "tool",
                        iconName = "extension", sortOrder = 99, isBuiltIn = false,
                        pluginPath = metaJson
                    ))
                    newAppName = ""; newPluginUrl = ""; newPluginDesc = ""; showAddDialog = false
                    coroutineScope.launch { snackbarHostState.showSnackbar("已添加插件: $name") }
                }
            }) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("取消") } }
    )
}

@Composable
fun DraggableAppGrid(
    items: List<AppItem>,
    onAppClick: (String) -> Unit,
    onReorder: (List<AppItem>) -> Unit,
    onDelete: (String) -> Unit,
    isDragging: Boolean,
    onDraggingChanged: (Boolean) -> Unit,
    dragOverDelete: Boolean,
    onDragOverDeleteChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var itemList by remember(items) { mutableStateOf(items) }
    var draggedIdx by remember { mutableStateOf<Int?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val padPx = with(density) { 12.dp.toPx() }
    val gapPx = with(density) { 6.dp.toPx() }
    val deleteBarH = with(density) { 56.dp.toPx() }

    LaunchedEffect(items) { if (draggedIdx == null) itemList = items }

    Box(modifier = modifier.fillMaxSize()) {
        // DELETE BAR - visible during drag at top
        if (isDragging) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .align(Alignment.TopCenter)
                    .zIndex(10f)
                    .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
                    .background(if (dragOverDelete) Color(0xFFD32F2F) else Color(0xFF757575)),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Delete, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (dragOverDelete) "松手即可删除" else "拖到此处删除",
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp
                    )
                }
            }
        }

        // Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .then(if (isDragging) Modifier.padding(top = 56.dp) else Modifier),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(itemList, key = { _, it -> it.id }) { _, item ->
                GridItemCard(item)
            }
        }

        // Touch overlay for tap + drag
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(itemList.size) {
                    detectTapGestures { offset ->
                        if (isDragging) return@detectTapGestures
                        val totalW = size.width.toFloat()
                        val cellSize = (totalW - padPx * 2 - gapPx * 2) / 3f
                        val x = offset.x - padPx; val y = offset.y - padPx
                        val col = ((x + gapPx) / (cellSize + gapPx)).toInt().coerceIn(0, 2)
                        val row = ((y + gapPx) / (cellSize + gapPx)).toInt()
                        val idx = row * 3 + col
                        if (idx in 0 until itemList.size) onAppClick(itemList[idx].id)
                    }
                }
                .pointerInput(itemList.size) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            val totalW = size.width.toFloat()
                            val cellSize = (totalW - padPx * 2 - gapPx * 2) / 3f
                            val x = offset.x - padPx; val y = offset.y - padPx
                            val col = ((x + gapPx) / (cellSize + gapPx)).toInt().coerceIn(0, 2)
                            val row = ((y + gapPx) / (cellSize + gapPx)).toInt()
                            val idx = row * 3 + col
                            if (idx in 0 until itemList.size) {
                                draggedIdx = idx; dragPos = offset
                                onDraggingChanged(true)
                            }
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            dragPos += amount
                            val di = draggedIdx ?: return@detectDragGesturesAfterLongPress
                            val inZone = dragPos.y < deleteBarH + 20.dp.toPx()
                            onDragOverDeleteChanged(inZone)
                            if (!inZone) {
                                val totalW = size.width.toFloat()
                                val cellSize = (totalW - padPx * 2 - gapPx * 2) / 3f
                                val x = dragPos.x - padPx; val y = dragPos.y - padPx
                                val col = ((x + gapPx) / (cellSize + gapPx)).toInt().coerceIn(0, 2)
                                val row = ((y + gapPx) / (cellSize + gapPx)).toInt().coerceIn(0, 99)
                                val target = (row * 3 + col).coerceIn(0, itemList.size - 1)
                                if (target != di) {
                                    val m = itemList.toMutableList()
                                    val dr = m.removeAt(di)
                                    m.add(target, dr)
                                    itemList = m; draggedIdx = target
                                }
                            }
                        },
                        onDragEnd = {
                            val di = draggedIdx
                            // Check delete zone directly, not via parent state (timing issue)
                            val inDelete = dragPos.y < deleteBarH + 20.dp.toPx()
                            if (inDelete && di != null) {
                                val item = itemList[di]
                                if (!item.isBuiltIn) {
                                    itemList = itemList.toMutableList().also { it.removeAt(di) }
                                    onDelete(item.id)
                                }
                            } else if (!inDelete && di != null) {
                                onReorder(itemList)
                            }
                            draggedIdx = null; dragPos = Offset.Zero
                            onDraggingChanged(false); onDragOverDeleteChanged(false)
                        },
                        onDragCancel = {
                            val di = draggedIdx
                            val inDelete = dragPos.y < deleteBarH + 20.dp.toPx()
                            if (inDelete && di != null) {
                                val item = itemList.getOrNull(di)
                                if (item != null && !item.isBuiltIn) {
                                    itemList = itemList.toMutableList().also { it.removeAt(di) }
                                    onDelete(item.id)
                                }
                            } else if (!inDelete && di != null) {
                                onReorder(itemList)
                            }
                            draggedIdx = null; dragPos = Offset.Zero
                            onDraggingChanged(false); onDragOverDeleteChanged(false)
                        }
                    )
                }
        )
    }
}

@Composable
private fun GridItemCard(item: AppItem) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isBuiltIn) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(2.dp)
    ) {
        Column(Modifier.fillMaxSize().padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Icon(getAppIcon(item), item.name, Modifier.size(34.dp),
                tint = if (item.category == "game") MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(4.dp))
            Text(item.name, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center, maxLines = 2,
                overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

fun getAppIcon(item: AppItem): ImageVector = when (item.type) {
    GameType.CHINESE_CHESS.id -> Icons.Default.Casino
    GameType.INTERNATIONAL_CHESS.id -> Icons.Default.Casino
    GameType.FLIGHT_CHESS.id -> Icons.Default.Flight
    GameType.MILITARY_CHESS.id -> Icons.Default.Shield
    GameType.DOU_DI_ZHU.id -> Icons.Default.Style
    GameType.MAHJONG.id -> Icons.Default.GridView
    GameType.SPINNER.id -> Icons.Default.Adjust
    else -> Icons.Default.Extension
}

@Composable
fun NicknameDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("欢迎使用 EasyBox") },
        text = {
            Column {
                Text("请输入你的昵称，方便朋友在联机时认出你：")
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(name, { if (it.length <= 12) name = it },
                    label = { Text("昵称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("跳过") } }
    )
}
