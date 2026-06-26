package com.kaoyan.timer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaoyan.timer.KaoyanViewModel

@Composable
fun ProgressCard(
    vm: KaoyanViewModel,
    startDate: String,
    now: Long,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }
    val pct = vm.progressPct(now)
    val info = vm.countdown(now)

    SectionCard(modifier = modifier) {
        SectionTitle("备考进度")
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { (pct / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp),
            color = ColorGood,
            trackColor = ColorCard2
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "${"%.1f".format(pct)}% · 剩 ${info.days} 天",
            color = ColorFg,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("备考起点 $startDate", color = ColorMuted, fontSize = 13.sp)
            TextButton(onClick = { showPicker = true }) {
                Text("修改", color = ColorAccent2)
            }
        }
    }

    if (showPicker) {
        KaoyanDatePickerDialog(
            initialDate = startDate.ifBlank { "2026-01-01" },
            onDismiss = { showPicker = false },
            onConfirm = {
                vm.setStartDate(it)
                showPicker = false
            }
        )
    }
}
