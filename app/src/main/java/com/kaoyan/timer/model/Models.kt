package com.kaoyan.timer.model

import kotlinx.serialization.Serializable

@Serializable
data class Subtask(
    val id: String,
    val name: String,
    val estMin: Int,        // 预估专注时长(分),= 这一段实际跑的 focus 时长
    var done: Boolean = false
)

@Serializable
data class Item(
    val id: String,
    val name: String,
    var seconds: Double = 0.0,
    var runningSince: Long? = null,
    var subtasks: List<Subtask> = emptyList() // 拆解出的小任务;空=未拆解
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
    val pausedAt: Long? = null, // 非空=暂停,剩余时间冻结在该时刻
    val subtaskId: String? = null // 非空=拆解链里的一段(决定休息按 20% 算 + 自动接力);空=直接开始的普通番茄
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
