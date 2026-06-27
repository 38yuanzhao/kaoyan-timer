package com.kaoyan.timer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kaoyan.timer.audio.AudioEngine
import com.kaoyan.timer.data.Store
import com.kaoyan.timer.model.AppState
import com.kaoyan.timer.model.Item
import com.kaoyan.timer.model.Pomo
import com.kaoyan.timer.model.Subject
import com.kaoyan.timer.model.Templates
import com.kaoyan.timer.util.TimeUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.ceil

class KaoyanViewModel(app: Application) : AndroidViewModel(app) {

    private val store = Store(app.applicationContext)
    val audio = AudioEngine(app.applicationContext)

    private val _state = MutableStateFlow(store.load())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private val _now = MutableStateFlow(System.currentTimeMillis())
    val now: StateFlow<Long> = _now.asStateFlow()

    // itemId -> Item 的索引,结构变化后重建
    private var itemById: Map<String, Item> = buildIndex(_state.value)

    init {
        reconcile()
        viewModelScope.launch {
            while (true) {
                val n = System.currentTimeMillis()
                _now.value = n
                tickPomo(n)
                delay(1000)
            }
        }
    }

    // ---------------------------------------------------------------
    // 内部工具
    // ---------------------------------------------------------------
    private fun buildIndex(s: AppState): Map<String, Item> {
        val m = HashMap<String, Item>()
        for (sub in s.subjects) for (it in sub.items) m[it.id] = it
        return m
    }

    /** 任何修改后:重建索引、发布新引用、持久化。 */
    private fun publish(s: AppState) {
        itemById = buildIndex(s)
        _state.value = s
        store.save(s)
    }

    /** 复制当前 state(深拷贝 subjects/items 以便修改可变字段后发布新引用)。 */
    private fun copyState(src: AppState): AppState {
        val newSubjects = src.subjects.map { sub ->
            Subject(
                name = sub.name,
                items = sub.items.map { it ->
                    Item(
                        id = it.id,
                        name = it.name,
                        seconds = it.seconds,
                        runningSince = it.runningSince
                    )
                }
            )
        }
        return AppState(
            examDate = src.examDate,
            startDate = src.startDate,
            subjects = newSubjects,
            daily = HashMap(src.daily),
            dailySub = HashMap(src.dailySub.mapValues { HashMap(it.value) }),
            pomo = src.pomo?.let { Pomo(it.itemId, it.phase, it.startAt, it.endsAt, it.pausedAt) },
            focusMin = src.focusMin,
            breakMin = src.breakMin,
            template = src.template,
            sel = HashMap(src.sel)
        )
    }

    /** 结算秒数:同时记到当日总时长 daily 与当日该科 dailySub。subject 为空则只记总量。 */
    private fun addDaily(s: AppState, key: String, secs: Double, subject: String) {
        val cur = s.daily[key] ?: 0.0
        s.daily[key] = (cur + secs).coerceAtLeast(0.0)
        if (subject.isNotEmpty()) {
            val m = s.dailySub.getOrPut(key) { mutableMapOf() }
            m[subject] = ((m[subject] ?: 0.0) + secs).coerceAtLeast(0.0)
        }
    }

    /** 找 itemId 所属科目名,用于按科记账。 */
    private fun subjectNameOf(s: AppState, itemId: String): String =
        s.subjects.firstOrNull { sub -> sub.items.any { it.id == itemId } }?.name ?: ""

    // ---------------------------------------------------------------
    // 动作
    // ---------------------------------------------------------------
    fun applyTemplate(key: String) {
        val s = copyState(_state.value)
        val tpl = Templates.all[key] ?: return
        @Suppress("UNUSED_VARIABLE")
        val unused = tpl
        val newState = AppState(
            examDate = s.examDate,
            startDate = s.startDate,
            subjects = Templates.build(key),
            daily = HashMap(s.daily), // 保留 daily
            dailySub = HashMap(s.dailySub.mapValues { HashMap(it.value) }), // 保留按科历史
            pomo = null,
            focusMin = s.focusMin,
            breakMin = s.breakMin,
            template = key,
            sel = HashMap()
        )
        publish(newState)
    }

    fun toggleItem(id: String) {
        val s = copyState(_state.value)
        val now = System.currentTimeMillis()
        val item = s.subjects.flatMap { it.items }.firstOrNull { it.id == id } ?: return
        val rs = item.runningSince
        if (rs == null) {
            // 开始
            item.runningSince = now
        } else {
            // 停止:结算
            val elapsed = (now - rs) / 1000.0
            if (elapsed > 0) {
                item.seconds += elapsed
                addDaily(s, TimeUtil.todayKey(now), elapsed, subjectNameOf(s, id))
            }
            item.runningSince = null
        }
        publish(s)
    }

    fun manualAdd(id: String, minutes: Double) {
        val s = copyState(_state.value)
        val now = System.currentTimeMillis()
        val item = s.subjects.flatMap { it.items }.firstOrNull { it.id == id } ?: return
        val secs = minutes * 60.0
        item.seconds = (item.seconds + secs).coerceAtLeast(0.0)
        addDaily(s, TimeUtil.todayKey(now), secs, subjectNameOf(s, id))
        publish(s)
    }

    fun selectSubItem(subjectName: String, itemId: String) {
        val s = copyState(_state.value)
        val now = System.currentTimeMillis()
        val subject = s.subjects.firstOrNull { it.name == subjectName }
        if (subject != null) {
            // 先把同科目所有在跑 item 结算暂停
            for (it in subject.items) {
                val rs = it.runningSince
                if (rs != null) {
                    val elapsed = (now - rs) / 1000.0
                    if (elapsed > 0) {
                        it.seconds += elapsed
                        addDaily(s, TimeUtil.todayKey(now), elapsed, subject.name)
                    }
                    it.runningSince = null
                }
            }
        }
        s.sel[subjectName] = itemId
        publish(s)
    }

    fun startPomo(itemId: String) {
        val s = copyState(_state.value)
        val now = System.currentTimeMillis()
        val focusMs = s.focusMin.toLong() * 60_000L
        s.pomo = Pomo(
            itemId = itemId,
            phase = "focus",
            startAt = now,
            endsAt = now + focusMs
        )
        publish(s)
    }

    fun pausePomo() {
        val cur = _state.value.pomo ?: return
        if (cur.pausedAt != null) return
        val s = copyState(_state.value)
        val now = System.currentTimeMillis()
        s.pomo = s.pomo?.copy(pausedAt = minOf(now, cur.endsAt))
        publish(s)
    }

    fun resumePomo() {
        val cur = _state.value.pomo ?: return
        val pausedAt = cur.pausedAt ?: return
        val s = copyState(_state.value)
        val now = System.currentTimeMillis()
        val delta = now - pausedAt
        s.pomo = s.pomo?.copy(
            startAt = cur.startAt + delta,
            endsAt = cur.endsAt + delta,
            pausedAt = null
        )
        publish(s)
    }

    fun stopPomo() {
        val s = copyState(_state.value)
        val p = s.pomo
        if (p != null && p.phase == "focus") {
            // 中途停止也结算已专注时间(暂停时取冻结点),避免白干
            val now = System.currentTimeMillis()
            val end = p.pausedAt ?: minOf(now, p.endsAt)
            val elapsed = (end - p.startAt) / 1000.0
            if (elapsed > 0) {
                val item = s.subjects.flatMap { it.items }.firstOrNull { it.id == p.itemId }
                if (item != null) {
                    item.seconds += elapsed
                    addDaily(s, TimeUtil.todayKey(end), elapsed, subjectNameOf(s, p.itemId))
                }
            }
        }
        s.pomo = null
        publish(s)
    }

    fun setExamDate(s: String) {
        val st = copyState(_state.value)
        st.examDate = s
        publish(st)
    }

    fun setStartDate(s: String) {
        val st = copyState(_state.value)
        st.startDate = s
        publish(st)
    }

    fun setFocusMin(v: Int) {
        val st = copyState(_state.value)
        st.focusMin = v.coerceAtLeast(1)
        publish(st)
    }

    fun setBreakMin(v: Int) {
        val st = copyState(_state.value)
        st.breakMin = v.coerceAtLeast(1)
        publish(st)
    }

    // ---------------------------------------------------------------
    // tickPomo / reconcile
    // ---------------------------------------------------------------
    private fun tickPomo(now: Long) {
        val pomo = _state.value.pomo ?: return
        if (pomo.pausedAt != null) return // 暂停中不推进,否则墙钟越过 endsAt 会误结算
        if (now < pomo.endsAt) return

        val s = copyState(_state.value)
        val p = s.pomo ?: return
        if (p.phase == "focus") {
            // 结算 focus 到 item + daily
            val item = s.subjects.flatMap { it.items }.firstOrNull { it.id == p.itemId }
            val focusSecs = (p.endsAt - p.startAt) / 1000.0
            if (item != null && focusSecs > 0) {
                item.seconds += focusSecs
                addDaily(s, TimeUtil.todayKey(p.endsAt), focusSecs, subjectNameOf(s, p.itemId))
            }
            audio.beep()
            // 转 break
            val breakMs = s.breakMin.toLong() * 60_000L
            s.pomo = Pomo(
                itemId = p.itemId,
                phase = "break",
                startAt = p.endsAt,
                endsAt = p.endsAt + breakMs
            )
        } else {
            // break 结束
            audio.beep()
            s.pomo = null
        }
        publish(s)
    }

    /** init 里:若 pomo 且 now>=endsAt,focus 补记一次后清空。 */
    private fun reconcile() {
        val pomo = _state.value.pomo ?: return
        if (pomo.pausedAt != null) return // 重启后保持暂停态,不结算
        val now = System.currentTimeMillis()
        if (now < pomo.endsAt) return

        val s = copyState(_state.value)
        val p = s.pomo ?: return
        if (p.phase == "focus") {
            val item = s.subjects.flatMap { it.items }.firstOrNull { it.id == p.itemId }
            val focusSecs = (p.endsAt - p.startAt) / 1000.0
            if (item != null && focusSecs > 0) {
                item.seconds += focusSecs
                addDaily(s, TimeUtil.todayKey(p.endsAt), focusSecs, subjectNameOf(s, p.itemId))
            }
        }
        s.pomo = null
        publish(s)
    }

    // ---------------------------------------------------------------
    // 计算
    // ---------------------------------------------------------------
    fun itemSeconds(it: Item, now: Long): Double {
        val rs = it.runningSince
        return it.seconds + if (rs != null) (now - rs) / 1000.0 else 0.0
    }

    fun subjectSeconds(s: Subject, now: Long): Double {
        var sum = 0.0
        for (it in s.items) sum += itemSeconds(it, now)
        return sum
    }

    /** 所有在跑 item 的在途秒数 + pomo focus 在途秒数。 */
    fun liveExtra(now: Long): Double {
        val s = _state.value
        var extra = 0.0
        for (sub in s.subjects) for (it in sub.items) {
            val rs = it.runningSince
            if (rs != null) extra += (now - rs) / 1000.0
        }
        val pomo = s.pomo
        if (pomo != null && pomo.phase == "focus") {
            val cappedNow = pomo.pausedAt ?: minOf(now, pomo.endsAt)
            val elapsed = (cappedNow - pomo.startAt) / 1000.0
            if (elapsed > 0) extra += elapsed
        }
        return extra
    }

    /** 某科当前在途秒数:该科在跑 item + (若 pomo focus 的 item 属于该科)番茄在途。 */
    private fun liveExtraForSubject(sub: Subject, now: Long): Double {
        var e = 0.0
        for (it in sub.items) {
            val rs = it.runningSince
            if (rs != null) e += (now - rs) / 1000.0
        }
        val pomo = _state.value.pomo
        if (pomo != null && pomo.phase == "focus" && sub.items.any { it.id == pomo.itemId }) {
            val cappedNow = pomo.pausedAt ?: minOf(now, pomo.endsAt)
            val elapsed = (cappedNow - pomo.startAt) / 1000.0
            if (elapsed > 0) e += elapsed
        }
        return e
    }

    data class SubjectSlice(val name: String, val secs: Double)

    /**
     * 各科占比饼图取数。range: "today" 今日 / "week" 近7天 / "all" 累计。
     * 已结算部分 today/week 取 dailySub,all 取 item.seconds 之和;再叠加该科今日在途。
     */
    fun subjectBreakdown(range: String, now: Long): List<SubjectSlice> {
        val s = _state.value
        val keys: List<String> = when (range) {
            "today" -> listOf(TimeUtil.todayKey(now))
            "week" -> (0..6).map { TimeUtil.dayKeyOffset(now, it) }
            else -> emptyList()
        }
        return s.subjects.map { sub ->
            val settled = if (range == "all") {
                sub.items.sumOf { it.seconds }
            } else {
                keys.sumOf { k -> s.dailySub[k]?.get(sub.name) ?: 0.0 }
            }
            SubjectSlice(sub.name, settled + liveExtraForSubject(sub, now))
        }
    }

    /** 番茄剩余秒数;暂停时以冻结点计,供两个 UI 复用。 */
    fun pomoRemainSec(now: Long): Long {
        val p = _state.value.pomo ?: return 0L
        val ref = p.pausedAt ?: now
        return ((p.endsAt - ref) / 1000L).coerceAtLeast(0L)
    }

    fun todaySeconds(now: Long): Double {
        val s = _state.value
        val base = s.daily[TimeUtil.todayKey(now)] ?: 0.0
        return base + liveExtra(now)
    }

    data class CountInfo(val days: Int, val hours: Int, val diffMs: Long)

    fun countdown(now: Long): CountInfo {
        val examMs = TimeUtil.dateToMillisLocalMidnight(_state.value.examDate)
        val diff = examMs - now
        val safeDiff = diff.coerceAtLeast(0L)
        val days = ceil(safeDiff.toDouble() / TimeUtil.DAY_MS).toInt()
        val hours = ceil(safeDiff.toDouble() / TimeUtil.HOUR_MS).toInt()
        return CountInfo(days = days, hours = hours, diffMs = diff)
    }

    fun progressPct(now: Long): Float {
        val s = _state.value
        val start = TimeUtil.dateToMillisLocalMidnight(s.startDate)
        val exam = TimeUtil.dateToMillisLocalMidnight(s.examDate)
        val total = (exam - start).toDouble()
        if (total <= 0.0) return 0f
        val done = (now - start).toDouble()
        val pct = done / total * 100.0
        return pct.coerceIn(0.0, 100.0).toFloat()
    }

    data class DayBar(val secs: Double, val dow: Int, val today: Boolean)

    /** 近7天(含今天);今天的 secs 含 liveExtra。 */
    fun last7(now: Long): List<DayBar> {
        val s = _state.value
        val todayK = TimeUtil.todayKey(now)
        val result = ArrayList<DayBar>(7)
        for (i in 6 downTo 0) {
            val key = TimeUtil.dayKeyOffset(now, i)
            var secs = s.daily[key] ?: 0.0
            val isToday = key == todayK
            if (isToday) secs += liveExtra(now)
            val cal = Calendar.getInstance()
            cal.timeInMillis = now - i.toLong() * TimeUtil.DAY_MS
            // Calendar.DAY_OF_WEEK: 周日=1..周六=7 -> 转成 0..6 (周日=0)
            val dow = cal.get(Calendar.DAY_OF_WEEK) - 1
            result.add(DayBar(secs = secs, dow = dow, today = isToday))
        }
        return result
    }

    // ---------------------------------------------------------------
    // 备份 / 多端同步:导出已结算快照 / 导入覆盖
    // ---------------------------------------------------------------
    /** 导出此刻的干净快照:把在途时长结算进 seconds/daily,清掉运行中状态与 pomo。 */
    fun exportJson(): String {
        val now = System.currentTimeMillis()
        val today = TimeUtil.todayKey(now)
        val s = copyState(_state.value)
        // 趁 running/pomo 还在,先把今日各科在途折进 dailySub(与 daily[today] 一致)
        val m = s.dailySub.getOrPut(today) { mutableMapOf() }
        for (sub in _state.value.subjects) {
            val extra = liveExtraForSubject(sub, now)
            if (extra > 0) m[sub.name] = (m[sub.name] ?: 0.0) + extra
        }
        for (sub in s.subjects) for (it in sub.items) {
            it.seconds = itemSeconds(it, now)
            it.runningSince = null
        }
        s.daily[today] = todaySeconds(now)
        s.pomo = null
        return store.serialize(s)
    }

    /** 导入备份:覆盖本机数据。解析失败返回 false。 */
    fun importJson(text: String): Boolean {
        val imported = store.deserialize(text) ?: return false
        for (sub in imported.subjects) for (it in sub.items) it.runningSince = null
        imported.pomo = null
        if (imported.startDate.isBlank()) {
            imported.startDate = TimeUtil.todayKey(System.currentTimeMillis())
        }
        publish(imported)
        return true
    }

    override fun onCleared() {
        super.onCleared()
        audio.release()
    }
}
