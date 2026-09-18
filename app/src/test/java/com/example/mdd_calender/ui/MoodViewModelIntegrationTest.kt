package com.example.mdd_calender.ui

import com.example.mdd_calender.data.MoodRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class MoodViewModelIntegrationTest {

    @Test
    fun editingRecordPreservesOriginalCreatedAt() {
        val originalCreatedAt = 1_700_000_000_000L
        val existing = moodRecord(id = 42, createdAt = originalCreatedAt)

        val result = resolveCreatedAt(
            id = existing.id,
            records = listOf(existing),
            now = originalCreatedAt + 60_000
        )

        assertEquals(originalCreatedAt, result)
    }

    @Test
    fun newRecordUsesCurrentTime() {
        val now = 1_800_000_000_000L

        assertEquals(now, resolveCreatedAt(id = 0, records = emptyList(), now = now))
    }

    private fun moodRecord(id: Int, createdAt: Long) = MoodRecord(
        id = id,
        date = "2026-09-17",
        time = "12:00",
        moodType = "平静",
        note = null,
        createdAt = createdAt,
        updatedAt = createdAt
    )
}
