package com.easybox.app.ui.spinner

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easybox.app.data.local.PreferencesManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import org.json.JSONArray
import org.json.JSONObject

data class SpinnerItem(
    val id: Int,
    val label: String,
    val weight: Float = 1f
)

data class SpinnerPreset(
    val name: String,
    val items: List<SpinnerItem>
)

private fun presetsToJson(presets: List<SpinnerPreset>): String {
    val arr = JSONArray()
    for (p in presets) {
        val obj = JSONObject()
        obj.put("name", p.name)
        val itemsArr = JSONArray()
        for (item in p.items) {
            val io = JSONObject()
            io.put("id", item.id)
            io.put("label", item.label)
            io.put("weight", item.weight.toDouble())
            itemsArr.put(io)
        }
        obj.put("items", itemsArr)
        arr.put(obj)
    }
    return arr.toString()
}

private fun presetsFromJson(json: String): List<SpinnerPreset> {
    val result = mutableListOf<SpinnerPreset>()
    try {
        val arr = JSONArray(json)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val name = obj.getString("name")
            val itemsArr = obj.getJSONArray("items")
            val items = mutableListOf<SpinnerItem>()
            for (j in 0 until itemsArr.length()) {
                val io = itemsArr.getJSONObject(j)
                items.add(SpinnerItem(
                    id = io.getInt("id"),
                    label = io.getString("label"),
                    weight = io.getDouble("weight").toFloat()
                ))
            }
            result.add(SpinnerPreset(name = name, items = items))
        }
    } catch (_: Exception) { }
    return result
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpinnerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }
    val scope = rememberCoroutineScope()

    var items by remember { mutableStateOf(listOf(
        SpinnerItem(0, "选项A", 1f), SpinnerItem(1, "选项B", 1f),
        SpinnerItem(2, "选项C", 1f), SpinnerItem(3, "选项D", 1f)
    )) }
    var isSpinning by remember { mutableStateOf(false) }
    var currentRotation by remember { mutableFloatStateOf(0f) }
    var result by remember { mutableStateOf<String?>(null) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var editText by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var newItemText by remember { mutableStateOf("") }
    var showSaveDialog by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }
    var presets by remember { mutableStateOf<List<SpinnerPreset>>(emptyList()) }

    LaunchedEffect(Unit) {
        prefs.spinnerPresets.collect { json -> presets = presetsFromJson(json) }
    }

    fun savePresets() {
        scope.launch { prefs.setSpinnerPresets(presetsToJson(presets)) }
    }

    val segmentColors = listOf(
        Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF43A047),
        Color(0xFFFB8C00), Color(0xFF8E24AA), Color(0xFF00ACC1),
        Color(0xFFD81B60), Color(0xFF3949AB), Color(0xFF689F38),
        Color(0xFFF4511E), Color(0xFF6D4C41), Color(0xFF546E7A)
    )

    val animatedRotation by animateFloatAsState(
        targetValue = currentRotation,
        animationSpec = tween(durationMillis = if (isSpinning) 3000 else 0, easing = FastOutSlowInEasing),
        label = "rotation"
    )

    fun doSpin() {
        if (items.isEmpty() || isSpinning) return
        isSpinning = true; result = null
        val totalW = items.sumOf { it.weight.toDouble() }
        val r = Random.nextDouble() * totalW
        var cum = 0.0; var idx = 0
        for ((i, it) in items.withIndex()) { cum += it.weight; if (r <= cum) { idx = i; break } }
        var angle = -90f
        for (i in 0 until idx) angle += (items[i].weight / totalW.toFloat()) * 360f
        angle += (items[idx].weight / totalW.toFloat()) * 360f / 2f
        val sweep = (items[idx].weight / totalW.toFloat()) * 360f
        val off = (Random.nextFloat() - 0.5f) * sweep * 0.7f
        val adj = angle + off
        var delta = (270f - adj - currentRotation) % 360f
        if (delta < 0f) delta += 360f
        currentRotation += 360f * (5 + Random.nextInt(3)) + delta
        scope.launch { delay(3200); result = items[idx].label; isSpinning = false }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("随机转盘") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))

            // Wheel
            Box(Modifier.fillMaxWidth().aspectRatio(1f).padding(24.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val sz = size.minDimension; val arcSz = Size(sz, sz)
                    val ox = (size.width - sz) / 2f; val oy = (size.height - sz) / 2f
                    val tl = Offset(ox, oy); val ctr = Offset(size.width / 2f, size.height / 2f)
                    val tw = items.sumOf { it.weight.toDouble() }.toFloat()
                    if (tw <= 0f) return@Canvas
                    rotate(animatedRotation, ctr) {
                        var sa = -90f
                        items.forEachIndexed { i, item ->
                            val sw = (item.weight / tw) * 360f
                            drawArc(segmentColors[i % segmentColors.size], sa, sw, true, tl, arcSz)
                            drawArc(Color.White, sa, sw, true, tl, arcSz, style = Stroke(3f))
                            // Draw text label using Android native canvas
                            val rad = Math.toRadians((sa + sw / 2).toDouble())
                            val labelX = ctr.x + sz * 0.32f * cos(rad).toFloat()
                            val labelY = ctr.y + sz * 0.32f * sin(rad).toFloat()
                            drawIntoCanvas { canvas ->
                                val paint = android.graphics.Paint().apply {
                                    color = android.graphics.Color.WHITE
                                    textSize = sz * 0.1f
                                    textAlign = android.graphics.Paint.Align.CENTER
                                    isAntiAlias = true
                                    isFakeBoldText = true
                                }
                                canvas.nativeCanvas.drawText(
                                    item.label.take(4),
                                    labelX,
                                    labelY + sz * 0.035f,
                                    paint
                                )
                            }
                            sa += sw
                        }
                    }
                    drawCircle(Color(0xFF424242), sz / 2f, ctr, style = Stroke(5f))
                    drawCircle(Color(0xFF616161), sz * 0.07f, ctr)
                    drawCircle(Color.White, sz * 0.04f, ctr)
                    val pth = androidx.compose.ui.graphics.Path().apply {
                        moveTo(ctr.x - 14f, oy - 8f); lineTo(ctr.x + 14f, oy - 8f); lineTo(ctr.x, oy + 18f); close()
                    }
                    drawPath(pth, Color(0xFF212121))
                }
                if (result != null && !isSpinning) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(8.dp)) {
                        Text("结果: $result", Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }

            Button(onClick = { doSpin() }, enabled = !isSpinning && items.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text(if (isSpinning) "转动中..." else "开始转动", fontSize = 16.sp)
            }

            Spacer(Modifier.height(16.dp))

            // Options
            Text("选项列表", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))

            items.forEachIndexed { i, item ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.size(20.dp), RoundedCornerShape(4.dp), color = segmentColors[i % segmentColors.size]) {}
                    Spacer(Modifier.width(10.dp))
                    Text(item.label, Modifier.weight(1f), fontSize = 15.sp)
                    Text("权重:${"%.0f".format(item.weight)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                    IconButton(onClick = { editingIndex = i; editText = item.label }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, "编辑", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { items = items.toMutableList().also { it.removeAt(i) } }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, "删除", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            OutlinedButton(onClick = { showAddDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("添加选项")
            }

            Spacer(Modifier.height(20.dp))

            // Presets
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("预设", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                TextButton(onClick = {
                    presetName = "预设${presets.size + 1}"; showSaveDialog = true
                }) {
                    Icon(Icons.Default.Save, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("保存当前")
                }
            }

            if (presets.isEmpty()) {
                Text("暂无预设，设置好转盘后点击'保存当前'", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
            }

            presets.forEachIndexed { i, preset ->
                Card(Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(preset.name, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("${preset.items.size}项", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                        IconButton(onClick = {
                            items = preset.items.map { it.copy() }; currentRotation = 0f; result = null
                        }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.FolderOpen, "加载", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = {
                            presets = presets.toMutableList().also { it.removeAt(i) }; savePresets()
                        }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Delete, "删除", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    // Edit dialog
    if (editingIndex != null) {
        AlertDialog(
            onDismissRequest = { editingIndex = null },
            title = { Text("编辑选项") },
            text = {
                OutlinedTextField(editText, { if (it.length <= 20) editText = it },
                    label = { Text("选项名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editText.isNotBlank() && editingIndex != null) {
                        items = items.toMutableList().also {
                            it[editingIndex!!] = it[editingIndex!!].copy(label = editText.trim())
                        }
                        editingIndex = null
                    }
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { editingIndex = null }) { Text("取消") } }
        )
    }

    // Add dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false; newItemText = "" },
            title = { Text("添加选项") },
            text = {
                OutlinedTextField(newItemText, { if (it.length <= 20) newItemText = it },
                    label = { Text("选项名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newItemText.isNotBlank()) {
                        items = items + SpinnerItem((items.maxOfOrNull { it.id } ?: -1) + 1, newItemText.trim())
                        newItemText = ""; showAddDialog = false
                    }
                }) { Text("添加") }
            },
            dismissButton = { TextButton(onClick = { newItemText = ""; showAddDialog = false }) { Text("取消") } }
        )
    }

    // Save preset dialog
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("保存预设") },
            text = {
                OutlinedTextField(presetName, { if (it.length <= 20) presetName = it },
                    label = { Text("预设名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = presetName.trim().ifBlank { "预设${presets.size + 1}" }
                    presets = presets + SpinnerPreset(name, items.map { it.copy() })
                    savePresets(); showSaveDialog = false
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showSaveDialog = false }) { Text("取消") } }
        )
    }
}
