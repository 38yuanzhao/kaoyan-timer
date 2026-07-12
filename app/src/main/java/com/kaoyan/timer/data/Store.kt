package com.kaoyan.timer.data

import android.content.Context
import com.kaoyan.timer.model.AppState
import com.kaoyan.timer.util.TimeUtil
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class Store(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(): AppState {
        val raw = prefs.getString(KEY_STATE, null)
        val state = if (raw.isNullOrBlank()) {
            AppState()
        } else {
            try {
                json.decodeFromString<AppState>(raw)
            } catch (e: Exception) {
                AppState()
            }
        }
        normalize(state)
        if (state.startDate.isBlank()) {
            state.startDate = TimeUtil.todayKey(System.currentTimeMillis())
        }
        return state
    }

    fun save(s: AppState) {
        try {
            val raw = json.encodeToString(s)
            prefs.edit().putString(KEY_STATE, raw).apply()
        } catch (e: Exception) {
            // ignore serialization failures
        }
    }

    /** 导出:AppState → JSON 字符串(供备份/同步)。 */
    fun serialize(s: AppState): String = json.encodeToString(s)

    /** 导入:JSON 字符串 → AppState,解析失败返回 null。 */
    fun deserialize(text: String): AppState? = try {
        json.decodeFromString<AppState>(text).also(::normalize)
    } catch (e: Exception) {
        null
    }

    private fun normalize(state: AppState) {
        TimeUtil.canonicalDayKey(state.examDate)?.let { state.examDate = it }
        if (state.startDate.isNotBlank()) {
            TimeUtil.canonicalDayKey(state.startDate)?.let { state.startDate = it }
        }

        val daily = mutableMapOf<String, Double>()
        for ((rawKey, secs) in state.daily) {
            val key = TimeUtil.canonicalDayKey(rawKey) ?: rawKey
            daily[key] = (daily[key] ?: 0.0) + secs
        }
        state.daily = daily

        val dailySub = mutableMapOf<String, MutableMap<String, Double>>()
        for ((rawKey, values) in state.dailySub) {
            val key = TimeUtil.canonicalDayKey(rawKey) ?: rawKey
            val target = dailySub.getOrPut(key) { mutableMapOf() }
            for ((subject, secs) in values) target[subject] = (target[subject] ?: 0.0) + secs
        }
        state.dailySub = dailySub
        state.sessions = state.sessions.map { session ->
            if (session.dayKey.isBlank()) session else session.copy(
                dayKey = TimeUtil.canonicalDayKey(session.dayKey) ?: session.dayKey
            )
        }.toMutableList()

        state.sessionLedger?.let { ledger ->
            ledger.dailyBase = ledger.dailyBase.entries.groupBy {
                TimeUtil.canonicalDayKey(it.key) ?: it.key
            }.mapValues { (_, entries) -> entries.sumOf { it.value } }.toMutableMap()
            val normalizedSub = mutableMapOf<String, MutableMap<String, Double>>()
            for ((rawKey, values) in ledger.dailySubBase) {
                val key = TimeUtil.canonicalDayKey(rawKey) ?: rawKey
                val target = normalizedSub.getOrPut(key) { mutableMapOf() }
                for ((subject, secs) in values) target[subject] = (target[subject] ?: 0.0) + secs
            }
            ledger.dailySubBase = normalizedSub
        }

        if (state.itemGenerationId.isBlank()) state.itemGenerationId = UUID.randomUUID().toString()
        ensureSessionLedger(state)
    }

    companion object {
        private const val PREFS_NAME = "kaoyan"
        private const val KEY_STATE = "state"
    }
}
