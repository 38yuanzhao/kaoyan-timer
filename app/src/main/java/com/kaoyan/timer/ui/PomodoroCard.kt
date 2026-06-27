package com.kaoyan.timer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    if (pomoRunning && state.pomo?.itemId != null) {
        selectedId = state.pomo?.itemId
    }
    var expanded by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    val selectedLabel = options.firstOrNull { it.id == selectedId }?.label ?: "选择子项"

    val isBreak = state.pomo?.phase == "break"
    val remainSec: Long = if (pomoRunning) {
        ((state.pomo!!.endsAt - now) / 1000L).coerceAtLeast(0L)
    } else {
        state.focusMin.toLong() * 60L
    }
    val totalSec: Long = if (pomoRunning) {
        ((state.pomo!!.endsAt - state.pomo!!.startAt) / 1000L).coerceAtLeast(1L)
    } else {
        state.focusMin.toLong() * 60L
    }
    val progress = if (pomoRunning) 1f - remainSec.toFloat() / totalSec.toFloat() else 0f
    val ringColor = if (isBreak) ColorAccent2 else ColorGood
    val phaseLabel = when (state.pomo?.phase) {
        "focus" -> "专注中"
        "break" -> "休息中"
        else -> "待开始"
    }

    val runningBg = if (state.pomo?.phase == "focus") ColorGoodContainer else ColorCard
    SectionCard(modifier = modifier, containerColor = runningBg) {
        SectionTitle("番茄钟")
        Spacer(Modifier.height(12.dp))

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

        Spacer(Modifier.height(20.dp))
        val timeStr = mmss(remainSec)
        BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val ringD = if (maxWidth < 260.dp) maxWidth else 260.dp
            val fs = ringD.value * 0.205f
            Box(modifier = Modifier.size(ringD), contentAlignment = Alignment.Center) {
                RingProgress(
                    progress = progress,
                    color = ringColor,
                    strokeWidth = 12.dp,
                    modifier = Modifier.fillMaxSize()
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        timeStr,
                        color = ringColor,
                        fontSize = fs.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(phaseLabel, color = if (pomoRunning) ColorFg else ColorMuted, fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        if (!pomoRunning) {
            TextButton(onClick = { showSettings = !showSettings }) {
                Text(
                    "⚙ 时长  专注 ${state.focusMin} / 休息 ${state.breakMin} 分",
                    color = ColorMuted,
                    fontSize = 13.sp
                )
            }
            AnimatedVisibility(visible = showSettings) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WheelMinutePicker(
                        label = "专注(分)",
                        value = state.focusMin,
                        range = 1..180,
                        enabled = true,
                        onChange = { vm.setFocusMin(it) },
                        modifier = Modifier.weight(1f)
                    )
                    WheelMinutePicker(
                        label = "休息(分)",
                        value = state.breakMin,
                        range = 1..60,
                        enabled = true,
                        onChange = { vm.setBreakMin(it) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { selectedId?.let { vm.startPomo(it) } },
                enabled = selectedId != null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ColorGood,
                    contentColor = ColorBg
                )
            ) { Text("开始 ▶", fontWeight = FontWeight.SemiBold) }
        } else {
            Button(
                onClick = { vm.stopPomo() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ColorAccent,
                    contentColor = ColorBg
                )
            ) { Text("停止", fontWeight = FontWeight.SemiBold) }
        }
    }
}
