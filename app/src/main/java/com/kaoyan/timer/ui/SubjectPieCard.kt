package com.kaoyan.timer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaoyan.timer.KaoyanViewModel
import com.kaoyan.timer.model.AppState
import com.kaoyan.timer.util.fmt
import com.kaoyan.timer.util.hmc

// 各科配色:按科目顺序分配,自定义科目顺延取色(满了循环)
val SubjectPalette = listOf(
    Color(0xFF4D8DFF), // 蓝
    Color(0xFFF2A03D), // 琥珀
    Color(0xFF2DD4BF), // 青
    Color(0xFFFF6B5E), // 珊瑚
    Color(0xFF9085E9), // 紫
    Color(0xFFE879C7), // 粉
    Color(0xFF5BC85A), // 绿
    Color(0xFFE5C04B)  // 黄
)

private val RangeOptions = listOf("today" to "今日", "week" to "近7天", "all" to "累计")

/** 各科时间占比饼图(环形),可切「今日 / 近7天 / 累计」。各科颜色按科目顺序固定。 */
@Composable
fun SubjectPieCard(vm: KaoyanViewModel, state: AppState, now: Long) {
    if (state.subjects.isEmpty()) return
    var range by rememberSaveable { mutableStateOf("today") }

    // 颜色绑科目顺序,与时段/过滤无关,切换不串色
    val colorOf = remember(state.subjects) {
        state.subjects.mapIndexed { i, sub -> sub.name to SubjectPalette[i % SubjectPalette.size] }.toMap()
    }
    val slices = vm.subjectBreakdown(range, now).filter { it.secs > 0.0 }
    val total = slices.sumOf { it.secs }

    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("各科时间占比", color = ColorFg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                RangeOptions.forEach { (key, label) ->
                    val sel = key == range
                    Text(
                        label,
                        color = if (sel) ColorGood else ColorMuted,
                        fontSize = 12.sp,
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (sel) ColorGoodContainer else Color.Transparent)
                            .clickable { range = key }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        if (slices.isEmpty()) {
            Text("该时段暂无数据", color = ColorMuted, fontSize = 13.sp)
            return@SectionCard
        }

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(176.dp)) {
                    val stroke = 34.dp.toPx()
                    val inset = stroke / 2f
                    val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                    val topLeft = Offset(inset, inset)
                    drawArc(ColorCard2, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                    // 扇区上直接标时长(深色字,衬在亮色块上);太小的扇区放不下就只留图例
                    val labelPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.parseColor("#0F1115")
                        textAlign = android.graphics.Paint.Align.CENTER
                        textSize = 12.sp.toPx()
                        isAntiAlias = true
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val labelR = size.minDimension / 2f - stroke / 2f
                    var start = -90f
                    val gapDeg = if (slices.size > 1) 3f else 0f
                    slices.forEach { sl ->
                        val sweep = (sl.secs / total * 360.0).toFloat()
                        if (sweep > 0f) {
                            drawArc(
                                color = colorOf[sl.name] ?: ColorGood,
                                startAngle = start + gapDeg / 2f,
                                sweepAngle = (sweep - gapDeg).coerceAtLeast(0.5f),
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(stroke, cap = StrokeCap.Butt)
                            )
                            if (sl.secs / total >= 0.06) {
                                val rad = Math.toRadians((start + sweep / 2f).toDouble())
                                val lx = cx + (labelR * kotlin.math.cos(rad)).toFloat()
                                val ly = cy + (labelR * kotlin.math.sin(rad)).toFloat()
                                val fm = labelPaint.fontMetrics
                                drawContext.canvas.nativeCanvas.drawText(
                                    hmc(sl.secs),
                                    lx, ly - (fm.descent + fm.ascent) / 2f, labelPaint
                                )
                            }
                            start += sweep
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(hmc(total), color = ColorFg, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(
                        RangeOptions.firstOrNull { it.first == range }?.second ?: "",
                        color = ColorMuted,
                        fontSize = 12.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            slices.forEach { sl ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(colorOf[sl.name] ?: ColorGood)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(sl.name, color = ColorFg, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(fmt(sl.secs), color = ColorMuted, fontSize = 13.sp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "%.0f%%".format(sl.secs / total * 100.0),
                        color = ColorFg,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
