package com.kaoyan.timer.ui

import android.widget.NumberPicker
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.kaoyan.timer.KaoyanViewModel
import com.kaoyan.timer.model.AppState
import com.kaoyan.timer.util.mmss

private data class PomoOption(val id: String, val label: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PomodoroCard(
    vm: KaoyanViewModel,
    state: AppState,
    now: Long,
    modifier: Modifier = Modifier
) {
    val options = remember(state.subjects) {
        state.subjects.flatMap { s ->
            s.items.map { it -> PomoOption(it.id, "${s.name} · ${it.name}") }
        }
    }

    val pomoRunning = state.pomo != null
    var selectedId by remember(state.subjects) {
        mutableStateOf(state.pomo?.itemId ?: options.firstOrNull()?.id)
    }
    // pomo 运行时强制跟随当前 pomo item
    if (pomoRunning && state.pomo?.itemId != null) {
        selectedId = state.pomo?.itemId
    }
    var expanded by remember { mutableStateOf(false) }

    val selectedLabel = options.firstOrNull { it.id == selectedId }?.label ?: "选择子项"

    // 计算 mm:ss
    val remainSec: Long = if (pomoRunning) {
        ((state.pomo!!.endsAt - now) / 1000L).coerceAtLeast(0L)
    } else {
        state.focusMin.toLong() * 60L
    }
    val phaseLabel = when (state.pomo?.phase) {
        "focus" -> "专注中"
        "break" -> "休息中"
        else -> "待开始"
    }

    SectionCard(modifier = modifier) {
        SectionTitle("番茄钟 · $phaseLabel")
        Spacer(Modifier.height(10.dp))

        ExposedDropdownMenuBox(
            expanded = expanded && !pomoRunning,
            onExpandedChange = { if (!pomoRunning) expanded = it }
        ) {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                enabled = !pomoRunning,
                label = { Text("子项") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && !pomoRunning)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = expanded && !pomoRunning,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt.label, color = ColorFg) },
                        onClick = {
                            selectedId = opt.id
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                mmss(remainSec),
                color = if (state.pomo?.phase == "break") ColorAccent2 else ColorGood,
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WheelMinutePicker(
                label = "专注(分)",
                value = state.focusMin,
                range = 1..180,
                enabled = !pomoRunning,
                onChange = { vm.setFocusMin(it) },
                modifier = Modifier.weight(1f)
            )
            WheelMinutePicker(
                label = "休息(分)",
                value = state.breakMin,
                range = 1..60,
                enabled = !pomoRunning,
                onChange = { vm.setBreakMin(it) },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(14.dp))

        if (!pomoRunning) {
            Button(
                onClick = { selectedId?.let { vm.startPomo(it) } },
                enabled = selectedId != null,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ColorGood,
                    contentColor = ColorBg
                )
            ) { Text("开始", fontWeight = FontWeight.SemiBold) }
        } else {
            Button(
                onClick = { vm.stopPomo() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ColorAccent,
                    contentColor = ColorBg
                )
            ) { Text("停止", fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** 滚轮选择分钟数:用原生 NumberPicker(本身就是滚轮),嵌进 Compose */
@Composable
private fun WheelMinutePicker(
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
        Spacer(Modifier.height(6.dp))
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
