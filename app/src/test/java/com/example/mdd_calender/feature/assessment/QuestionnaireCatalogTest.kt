package com.example.mdd_calender.feature.assessment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionnaireCatalogTest {
    @Test
    fun `catalog exposes answerable items instead of placeholders`() {
        listOf(QuestionnaireCatalog.phq9, QuestionnaireCatalog.gad7).forEach { descriptor ->
            assertEquals(descriptor.type.itemCount, descriptor.itemPlaceholders.size)
            assertEquals(listOf("完全没有", "有几天", "一半以上天数", "几乎每天"), descriptor.responseOptions)
            assertFalse(descriptor.itemPlaceholders.any { "待专业审核" in it || "第 1 题" in it })
            assertTrue(descriptor.itemPlaceholders.all { it.isNotBlank() })
        }
    }

    @Test
    fun `phq safety item remains the ninth item`() {
        assertTrue("伤害自己" in QuestionnaireCatalog.phq9.itemPlaceholders[8])
    }
}
