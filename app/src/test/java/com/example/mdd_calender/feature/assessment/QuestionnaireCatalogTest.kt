package com.example.mdd_calender.feature.assessment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionnaireCatalogTest {
    @Test
    fun phq9ContainsNineAnswerableItemsAndSafetyItem() {
        val questionnaire = QuestionnaireCatalog.phq9

        assertEquals(9, questionnaire.items.size)
        assertEquals((0..3).toList(), questionnaire.responseOptions.map { it.score })
        assertTrue(questionnaire.items[8].contains("伤害自己"))
        assertFalse(questionnaire.items.any { it.contains("待专业审核") })
    }

    @Test
    fun gad7ContainsSevenAnswerableItems() {
        val questionnaire = QuestionnaireCatalog.gad7

        assertEquals(7, questionnaire.items.size)
        assertEquals(listOf("完全没有", "有几天", "超过一半的天数", "几乎每天"),
            questionnaire.responseOptions.map { it.label })
        assertFalse(questionnaire.items.any { it.contains("待专业审核") })
    }

    @Suppress("DEPRECATION")
    @Test
    fun legacyIntegrationPropertyReadsTheReviewedItems() {
        assertSame(QuestionnaireCatalog.phq9.items, QuestionnaireCatalog.phq9.itemPlaceholders)
        assertSame(QuestionnaireCatalog.gad7.items, QuestionnaireCatalog.gad7.itemPlaceholders)
    }
}
