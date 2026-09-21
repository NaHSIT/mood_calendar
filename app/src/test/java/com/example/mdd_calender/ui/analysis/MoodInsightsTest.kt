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

    private fun record(date: String, mood: String) = MoodRecord(
        date = date,
        time = "12:00",
        moodType = mood,
        note = null,
        createdAt = 1,
        updatedAt = 1,
    )
}
