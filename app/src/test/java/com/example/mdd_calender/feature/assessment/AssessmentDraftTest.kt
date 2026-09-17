package com.example.mdd_calender.feature.assessment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AssessmentDraftTest {
    @Test
    fun answeringIsImmutableAndProgressUsesAnsweredItems() {
        val empty = AssessmentDraft.empty(AssessmentType.GAD_7)
        val answered = empty.answer(itemIndex = 2, value = 3)

        assertNotSame(empty, answered)
        assertEquals(0, empty.answeredCount)
        assertEquals(1, answered.answeredCount)
        assertEquals(1f / 7f, answered.progress)
        assertFalse(answered.isComplete)
    }

    @Test
    fun restoredDraftCanBeCompletedAndScored() {
        val restored = AssessmentDraft.restore(
            AssessmentType.GAD_7,
            listOf(1, 1, 1, 1, 1, 1, null),
        )
        val complete = restored.answer(6, 1)

        assertTrue(complete.isComplete)
        assertEquals(1f, complete.progress)
        assertEquals(7, (complete.score() as ScoreResult.Complete).total)
    }

    @Test
    fun clearingAnAnswerMakesDraftIncompleteAgain() {
        val complete = AssessmentDraft.restore(AssessmentType.GAD_7, List(7) { 0 })
        val cleared = complete.clear(0)

        assertFalse(cleared.isComplete)
        assertEquals(ScoreResult.Incomplete(listOf(0)), cleared.score())
    }

    @Test
    fun corruptSavedDraftIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            AssessmentDraft.restore(AssessmentType.PHQ_9, List(9) { -1 })
        }
        assertThrows(IllegalArgumentException::class.java) {
            AssessmentDraft.restore(AssessmentType.PHQ_9, List(7) { 0 })
        }
    }
}
