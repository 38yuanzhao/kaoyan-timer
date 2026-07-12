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
data class FocusSegment(
    val startAt: Long,
    val endAt: Long
)

@Serializable
data class Pomo(
    val itemId: String,
    val phase: String, // "focus" | "break"
    val startAt: Long,
    val endsAt: Long,
    val pausedAt: Long? = null, // 非空=暂停,剩余时间冻结在该时刻
    val subtaskId: String? = null, // 非空=拆解链里的一段(决定休息按 20% 算 + 自动接力);空=直接开始的普通番茄
    // null=旧版连续区间；非 null=新版实际专注区间，暂停空档不会被计入或跨日挪动。
    val focusSegments: List<FocusSegment>? = null,
    val activeFocusStartedAt: Long? = null
)

@Serializable
data class Session(
    val id: String,
    val startAt: Long,      // 毫秒;manual 记录 startAt==endAt
    val endAt: Long,
    val secs: Double,       // 本次结算秒数;manual 可为负(扣减)
    val subject: String,    // 结算时所属科目名(冗余存下,换模板后仍可显示)
    val itemId: String,
    val itemName: String,
    val kind: String,       // "timer" 秒表 | "pomo" 番茄 | "chain" 拆解链 | "manual" 手动
    // 新记录显式保存归属日。旧备份缺省时由调用方回退到 endAt 所在日。
    val dayKey: String = "",
    // 手动负调整可能分别触及三个已被钳制的非负聚合；保存实际增量才能精确撤销。
    // null 表示旧记录/普通计时，三个聚合均沿用 secs。
    val itemDeltaSecs: Double? = null,
    val dailyDeltaSecs: Double? = null,
    val subjectDeltaSecs: Double? = null,
    // 删除后重放用户原始意图；null 的旧记录回退到各聚合的实际增量/secs。
    val requestedDeltaSecs: Double? = null,
    // 模板每次生成时的身份。空值为旧记录，禁止借 deterministic itemId 回滚新模板 item。
    val itemGenerationId: String = ""
)

@Serializable
data class SessionLedger(
    // 基线已包含被 500 条上限淘汰的历史事件；保留事件始终从旧到新重放。
    var itemBase: MutableMap<String, Double> = mutableMapOf(),
    var dailyBase: MutableMap<String, Double> = mutableMapOf(),
    var dailySubBase: MutableMap<String, MutableMap<String, Double>> = mutableMapOf()
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
    var sel: MutableMap<String, String> = mutableMapOf(),
    var lastPomoItemId: String? = null, // 番茄钟上次选中的子项,用于重开后恢复选择
    var dailyGoalMin: Int = 0, // 每日目标时长(分);0=未设置
    var sessions: MutableList<Session> = mutableListOf(), // 学习记录明细,新在前;保留最近 MAX_SESSIONS 条
    var itemGenerationId: String = "",
    var sessionLedger: SessionLedger? = null
)
