package com.example.mdd_calender.ui.analysis

import com.example.mdd_calender.data.MoodRecord
import com.example.mdd_calender.ui.components.MoodType
import java.time.LocalDate

data class DailyMoodPoint(val date: LocalDate, val score: Float, val count: Int)

data class MoodInsight(
    val recordDays: Int,
    val dominantMood: MoodType?,
    val points: List<DailyMoodPoint>,
    val weeklySummary: String,
    val monthlySummary: String,
    val changeNotice: String,
    val reflectionPrompt: String,
)

fun buildMoodInsight(records: List<MoodRecord>, today: LocalDate = LocalDate.now()): MoodInsight {
    val valid = records.mapNotNull { record ->
        runCatching { LocalDate.parse(record.date) }.getOrNull()?.let { it to record }
    }
    val points = valid.groupBy({ it.first }, { it.second }).toSortedMap().map { (date, entries) ->
        DailyMoodPoint(date, entries.map { it.moodType.toMoodScore() }.average().toFloat(), entries.size)
    }
    val counts = records.groupingBy { it.moodType }.eachCount()
    val dominant = counts.maxByOrNull { it.value }?.key?.let(MoodType::fromLabel)
    val recent = points.filter { !it.date.isBefore(today.minusDays(6)) && !it.date.isAfter(today) }
    val previous = points.filter { it.date in today.minusDays(13)..today.minusDays(7) }
    val weekly = when {
        recent.isEmpty() -> "近 7 天还没有记录，先写下一次真实感受。"
        recent.size < 3 -> "近 7 天记录了 ${recent.size} 天；继续记录后，周趋势会更可靠。"
        else -> "近 7 天记录 ${recent.size} 天，平均情绪指数 ${recent.map { it.score }.average().format1()} / 5。"
    }
    val monthly = when {
        points.isEmpty() -> "本月暂无可分析记录。"
        points.size < 5 -> "本月已记录 ${points.size} 天，样本较少，暂不做趋势结论。"
        else -> "本月已记录 ${points.size} 天，共 ${records.size} 条；主导情绪为${dominant?.label ?: "未识别"}。"
    }
    val notice = if (recent.size >= 3 && previous.size >= 3) {
        val delta = recent.map { it.score }.average() - previous.map { it.score }.average()
        when {
            delta <= -0.8 -> "近 7 天较前一周明显回落。建议回看事件记录，必要时向可信任的人求助。"
            delta >= 0.8 -> "近 7 天较前一周有所回升，可以回顾哪些行动带来了帮助。"
            else -> "近两周整体变化平稳，继续记录有助于识别长期规律。"
        }
    } else "至少连续记录两个周期（每周 3 天以上）后，才能显示变化提醒。"
    val prompt = when (dominant) {
        MoodType.BAD, MoodType.AWFUL -> "哪些时刻最消耗你？有没有一件可减轻负担的小事？"
        MoodType.AMAZING, MoodType.GOOD -> "哪些人或行动支持了这份好心情？如何把它带到明天？"
        else -> if (points.isEmpty()) "今天发生了什么？它让你有什么感受？" else "回看最近的记录，哪一次情绪变化最值得继续了解？"
    }
    return MoodInsight(points.size, dominant, points, weekly, monthly, notice, prompt)
}

private fun String.toMoodScore(): Float = when (MoodType.fromLabel(this)) {
    MoodType.AMAZING -> 5f
    MoodType.GOOD -> 4f
    MoodType.MEH -> 3f
    MoodType.BAD -> 2f
    MoodType.AWFUL -> 1f
}

private fun Double.format1(): String = String.format(java.util.Locale.CHINA, "%.1f", this)
