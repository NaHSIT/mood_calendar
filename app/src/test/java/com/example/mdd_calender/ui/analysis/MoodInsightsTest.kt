package com.example.mdd_calender.ui.analysis

import com.example.mdd_calender.data.MoodRecord
import com.example.mdd_calender.ui.components.MoodType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoodInsightsTest {
    @Test
    fun `record days deduplicate multiple entries on same date`() {
        val insight = buildMoodInsight(
            listOf(record("2026-09-21", "开心"), record("2026-09-21", "惊喜"), record("2026-09-22", "平淡")),
            LocalDate.of(2026, 9, 22),
        )

        assertEquals(2, insight.recordDays)
        assertEquals(2, insight.points.size)
        assertEquals(2, insight.points.first().count)
    }

    @Test
    fun `recent decline creates supportive non diagnostic notice`() {
        val today = LocalDate.of(2026, 9, 22)
        val records = buildList {
            repeat(3) { add(record(today.minusDays(10L - it).toString(), MoodType.AMAZING.label)) }
            repeat(3) { add(record(today.minusDays(it.toLong()).toString(), MoodType.AWFUL.label)) }
        }

        val insight = buildMoodInsight(records, today)

        assertTrue(insight.changeNotice.contains("回落"))
        assertTrue(insight.changeNotice.contains("求助"))
    }

    @Test
    fun `reflection guide focuses lowest day without reading diary text`() {
        val records = listOf(
            record("2026-09-20", MoodType.GOOD.label).copy(content = "任意私人文字"),
            record("2026-09-21", MoodType.AWFUL.label).copy(content = "另一段文字"),
        )

        val insight = buildMoodInsight(records, LocalDate.of(2026, 9, 22))

        assertEquals(LocalDate.of(2026, 9, 21), insight.reflectionGuide.focusDate)
        assertEquals(3, insight.reflectionGuide.questions.size)
        assertTrue(insight.reflectionGuide.observation.contains("1.0 / 5"))
        assertTrue(insight.reflectionGuide.observation.contains("私人文字").not())
    }

    @Test
    fun `record pattern calculates streak time period and high low dates`() {
        val records = listOf(
            record("2026-09-18", MoodType.MEH.label).copy(time = "08:10"),
            record("2026-09-20", MoodType.AWFUL.label).copy(time = "09:15"),
            record("2026-09-21", MoodType.MEH.label).copy(time = "08:40"),
            record("2026-09-22", MoodType.AMAZING.label).copy(time = "19:00"),
        )

        val pattern = buildMoodInsight(records, LocalDate.of(2026, 9, 22)).recordPattern

        assertEquals(3, pattern.latestStreak)
        assertEquals(3, pattern.longestStreak)
        assertEquals("早晨", pattern.activePeriod)
        assertEquals(LocalDate.of(2026, 9, 22), pattern.highPointDate)
        assertEquals(LocalDate.of(2026, 9, 20), pattern.lowPointDate)
        assertEquals("波动较明显", pattern.variabilityLabel)
    }

    private fun record(date: String, mood: String) = MoodRecord(
        date = date,
        time = "12:00",
        moodType = mood,
        note = null,
        createdAt = 1,
        updatedAt = 1,
    )
}
