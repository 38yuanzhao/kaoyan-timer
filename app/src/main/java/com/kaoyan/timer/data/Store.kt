package com.kaoyan.timer.data

import android.content.Context
import com.kaoyan.timer.model.AppState
import com.kaoyan.timer.util.TimeUtil
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
        json.decodeFromString<AppState>(text)
    } catch (e: Exception) {
        null
    }

    companion object {
        private const val PREFS_NAME = "kaoyan"
        private const val KEY_STATE = "state"
    }
}
