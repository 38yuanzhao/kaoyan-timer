package com.kaoyan.timer.model

import kotlinx.serialization.Serializable

@Serializable
data class Item(
    val id: String,
    val name: String,
    var seconds: Double = 0.0,
    var runningSince: Long? = null
)

@Serializable
data class Subject(
    val name: String,
    val items: List<Item>
)

@Serializable
data class Pomo(
    val itemId: String,
    val phase: String, // "focus" | "break"
    val startAt: Long,
    val endsAt: Long,
    val pausedAt: Long? = null // 非空=暂停,剩余时间冻结在该时刻
)

@Serializable
data class AppState(
    var examDate: String = "2026-12-19",
    var startDate: String = "",
    var subjects: List<Subject> = emptyList(),
    var daily: MutableMap<String, Double> = mutableMapOf(),
    // 按天×按科累计秒数:日期"yyyy-MM-dd" -> 科目名 -> 秒;供各科占比饼图按今日/近7天/累计取数
    var dailySub: MutableMap<String, MutableMap<String, Double>> = mutableMapOf(),
    var pomo: Pomo? = null,
    var focusMin: Int = 25,
    var breakMin: Int = 5,
    var template: String? = null,
    var sel: MutableMap<String, String> = mutableMapOf()
)
