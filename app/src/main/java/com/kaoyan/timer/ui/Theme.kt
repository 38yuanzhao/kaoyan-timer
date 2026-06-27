package com.kaoyan.timer.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// ── 深色配色「石墨蓝」──────────────────────────────────────────────
// 参考主流生产力/专注 App(Material 深色 + 蓝强调、Linear Midnight 中性面、去饱和白字)。
// 纪律:一屏内最多一个东西用满 ColorGood 蓝(运行中/今日/进度/选中);青绿给休息/次要;
// 红只在危险操作出现。零阴影,靠中性近黑 Bg<Card<Card2 三档亮度分层。
val ColorBg = Color(0xFF0F1115)            // 全屏最底:中性近黑(冷),护眼防光晕
val ColorCard = Color(0xFF1A1D24)          // 主卡片 / tile / 导航栏容器(亮一档,无边也分得清)
val ColorCard2 = Color(0xFF242833)         // 卡内嵌块 / 进度槽底 / 环底
val ColorFg = Color(0xFFE6E8EC)            // 主文字、大数字(去饱和白)
val ColorMuted = Color(0xFF8A909C)         // 次文字、单位、未选 Tab
val ColorLine = Color(0xFF2E343F)          // 仅用于分隔线 / 导航栏顶边(不再给卡片描边)
val ColorGood = Color(0xFF4D8DFF)          // 唯一主强调蓝:运行中/今日/进度/选中
val ColorGoodContainer = Color(0xFF18304F) // Tab 选中药丸底 / 运行中卡微染底(深蓝)
val ColorAccent2 = Color(0xFF2DD4BF)       // 次强调青绿:休息相位 / 链接
val ColorAccent = Color(0xFFFF6B5E)        // 警示:停止 / 删除 / 切换模板

private val KaoyanColorScheme = darkColorScheme(
    background = ColorBg,
    surface = ColorCard,
    surfaceVariant = ColorCard2,
    primary = ColorGood,
    onPrimary = ColorBg,
    secondary = ColorAccent2,
    secondaryContainer = ColorGoodContainer,
    onSecondaryContainer = ColorGood,
    onBackground = ColorFg,
    onSurface = ColorFg,
    onSurfaceVariant = ColorMuted,
    outline = ColorLine,
    error = ColorAccent
)

private val KaoyanShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp)
)

@Composable
fun KaoyanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KaoyanColorScheme,
        shapes = KaoyanShapes,
        content = content
    )
}
