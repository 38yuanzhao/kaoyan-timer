package com.kaoyan.timer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

@Composable
fun CountdownCard(
    vm: KaoyanViewModel,
    examDate: String,
    now: Long,
    modifier: Modifier = Modifier
) {
    var showAsHours by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    val info = vm.countdown(now)

    SectionCard(modifier = modifier) {
        SectionTitle("距离初试")
        Spacer(Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showAsHours = !showAsHours }
        ) {
            if (!showAsHours) {
                Text(
                    "还有 ${info.days} 天",
                    color = ColorFg,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                val totalMinutes = (info.diffMs / 60000L).coerceAtLeast(0L)
                val h = totalMinutes / 60
                val m = totalMinutes % 60
                Text(
                    "${h}时${m}分",
                    color = ColorFg,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "约 ${info.hours} 小时",
            color = ColorMuted,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("初试日期 $examDate", color = ColorMuted, fontSize = 13.sp)
            TextButton(onClick = { showPicker = true }) {
                Text("修改", color = ColorAccent2)
            }
        }
    }

    if (showPicker) {
        KaoyanDatePickerDialog(
            initialDate = examDate,
            onDismiss = { showPicker = false },
            onConfirm = {
                vm.setExamDate(it)
                showPicker = false
            }
        )
    }
}
