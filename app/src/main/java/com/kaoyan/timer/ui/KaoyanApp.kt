package com.kaoyan.timer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kaoyan.timer.KaoyanViewModel

@Composable
fun KaoyanApp(vm: KaoyanViewModel) {
    val state by vm.state.collectAsState()
    val now by vm.now.collectAsState()
    val audio = vm.audio

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    // 噪音状态上提到顶层:切 tab / 重组都不丢
    var noiseType by rememberSaveable { mutableStateOf<String?>(null) }
    var volume by rememberSaveable { mutableFloatStateOf(0.5f) }

    val tabs = listOf(
        "仪表盘" to Icons.Filled.Home,
        "专注" to Icons.Filled.PlayArrow,
        "数据" to Icons.Filled.DateRange,
        "设置" to Icons.Filled.Settings
    )

    // 番茄运行时整屏接管;最小化可退回普通界面(计时不停)
    val pomoActive = state.pomo != null
    var pomoMinimized by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(pomoActive) { if (!pomoActive) pomoMinimized = false }
    if (pomoActive && !pomoMinimized) {
        FocusModeScreen(
            vm, state, now,
            onMinimize = { pomoMinimized = true },
            onStop = { vm.stopPomo() }
        )
        return
    }

    Scaffold(
        containerColor = ColorBg,
        bottomBar = {
            NavigationBar(containerColor = ColorCard, tonalElevation = 0.dp) {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = selectedTab == i,
                        onClick = { selectedTab = i },
                        icon = { Icon(t.second, contentDescription = t.first) },
                        label = { Text(t.first) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ColorGood,
                            selectedTextColor = ColorGood,
                            indicatorColor = ColorGoodContainer,
                            unselectedIconColor = ColorMuted,
                            unselectedTextColor = ColorMuted
                        )
                    )
                }
            }
        }
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            when (selectedTab) {
                0 -> DashboardScreen(
                    vm, state, now,
                    onStartFocus = { selectedTab = 1 },
                    onOpenData = { selectedTab = 2 }
                )
                1 -> FocusScreen(
                    vm, state, now, audio, noiseType, volume,
                    onNoiseChange = { t, v -> noiseType = t; volume = v },
                    onExpandPomo = { pomoMinimized = false }
                )
                2 -> DataScreen(vm, state, now)
                3 -> SettingsScreen(vm, state)
            }
        }
    }
}
