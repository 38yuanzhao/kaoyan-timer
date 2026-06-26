package com.kaoyan.timer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaoyan.timer.KaoyanViewModel
import com.kaoyan.timer.model.AppState
import com.kaoyan.timer.util.fmt

@Composable
fun TodayTotalRow(
    vm: KaoyanViewModel,
    state: AppState,
    now: Long,
    modifier: Modifier = Modifier
) {
    val todayHours = vm.todaySeconds(now) / 3600.0
    // 累计:所有 item 的 seconds 之和(含在跑)
    val totalSecs = state.subjects.sumOf { s ->
        s.items.sumOf { it -> vm.itemSeconds(it, now) }
    }

    SectionCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("今日", color = ColorMuted, fontSize = 12.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    "%.1fh".format(todayHours),
                    color = ColorGood,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("累计", color = ColorMuted, fontSize = 12.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    fmt(totalSecs),
                    color = ColorFg,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
