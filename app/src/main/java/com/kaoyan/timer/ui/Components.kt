package com.kaoyan.timer.ui

import android.widget.NumberPicker
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/** 共用卡片容器:零阴影,靠亮度分层;可选染底(运行中高亮)与描边。 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    containerColor: Color = ColorCard,
    border: BorderStroke? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(0.dp),
        border = border
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = ColorMuted,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier
    )
}

/** 共享环形进度:底环 + 进度弧(番茄环 / 仪表盘进度 tile 复用)。progress ∈ 0..1。 */
@Composable
fun RingProgress(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidth: androidx.compose.ui.unit.Dp = 10.dp,
    trackColor: Color = ColorCard2
) {
    val p = progress.coerceIn(0f, 1f)
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        val inset = strokeWidth.toPx() / 2f
        val arcSize = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
        val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
        drawArc(
            color = trackColor,
            startAngle = 0f, sweepAngle = 360f, useCenter = false,
            topLeft = topLeft, size = arcSize, style = stroke
        )
        if (p > 0f) {
            drawArc(
                color = color,
                startAngle = -90f, sweepAngle = 360f * p, useCenter = false,
                topLeft = topLeft, size = arcSize, style = stroke
            )
        }
    }
}

/** 滚轮选择分钟数:原生 NumberPicker 包进 Compose,番茄页折叠区与设置页共用。 */
@Composable
fun WheelMinutePicker(
    label: String,
    value: Int,
    range: IntRange,
    enabled: Boolean,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = ColorMuted, fontSize = 13.sp)
        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 3.dp))
        AndroidView(
            factory = { ctx ->
                NumberPicker(ctx).apply {
                    minValue = range.first
                    maxValue = range.last
                    this.value = value.coerceIn(range)
                    wrapSelectorWheel = false
                    setOnValueChangedListener { _, _, newVal -> onChange(newVal) }
                }
            },
            update = { picker ->
                picker.minValue = range.first
                picker.maxValue = range.last
                if (picker.value != value) picker.value = value.coerceIn(range)
                picker.isEnabled = enabled
            }
        )
    }
}

/** 滚轮选择(自定义档位文案):NumberPicker 的 displayedValues 版,给每日目标等非纯数字档位用。 */
@Composable
fun WheelLabelPicker(
    label: String,
    index: Int,
    labels: Array<String>,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = ColorMuted, fontSize = 13.sp)
        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 3.dp))
        AndroidView(
            factory = { ctx ->
                NumberPicker(ctx).apply {
                    minValue = 0
                    displayedValues = labels // 先设文案再设 maxValue,否则档位数不匹配会崩
                    maxValue = labels.size - 1
                    value = index.coerceIn(0, labels.size - 1)
                    wrapSelectorWheel = false
                    setOnValueChangedListener { _, _, newVal -> onChange(newVal) }
                }
            },
            update = { picker ->
                val v = index.coerceIn(0, labels.size - 1)
                if (picker.value != v) picker.value = v
            }
        )
    }
}

/** 手动加时间对话框,分钟数(可负) */
@Composable
fun ManualAddDialog(
    title: String,
    todayDate: String,
    onDismiss: () -> Unit,
    onConfirm: (Double, String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf(todayDate) }
    var showDatePicker by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = ColorFg) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("输入分钟数(可为负数,用于扣减)", color = ColorMuted, fontSize = 13.sp)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text("例如 30 或 -15", color = ColorMuted) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        if (selectedDate == todayDate) "日期：今天" else "日期：$selectedDate",
                        color = ColorMuted
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val v = text.trim().toDoubleOrNull()
                if (v != null) onConfirm(v, selectedDate)
            }) { Text("确定", color = ColorGood) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = ColorMuted) }
        },
        containerColor = ColorCard2,
        titleContentColor = ColorFg,
        textContentColor = ColorFg
    )

    if (showDatePicker) {
        KaoyanDatePickerDialog(
            initialDate = selectedDate,
            latestDate = todayDate,
            onDismiss = { showDatePicker = false },
            onConfirm = { date ->
                selectedDate = date
                showDatePicker = false
            }
        )
    }
}

/** 简单确认对话框 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "确定",
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = ColorFg) },
        text = { Text(message, color = ColorFg) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmText, color = ColorAccent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = ColorMuted) }
        },
        containerColor = ColorCard2,
        titleContentColor = ColorFg,
        textContentColor = ColorFg
    )
}
