package com.example.mdd_calender.domain.policy

import com.example.mdd_calender.domain.model.ActorContext
import com.example.mdd_calender.domain.model.ActorRole
import com.example.mdd_calender.domain.model.CareResult
import com.example.mdd_calender.domain.model.DataDomain
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessPolicyTest {
    @Test fun studentCannotReadAnotherStudentOrAnotherDomain() {
        val actor = ActorContext("student-a", ActorRole.STUDENT, DataDomain.DEMO)
        assertTrue(AccessPolicy.requireOwner(actor, "student-a", DataDomain.DEMO) is CareResult.Success)
        assertTrue(AccessPolicy.requireOwner(actor, "student-b", DataDomain.DEMO) is CareResult.Failure)
        assertTrue(AccessPolicy.requireOwner(actor, "student-a", DataDomain.PRODUCTION) is CareResult.Failure)
    }

    @Test fun teacherNeverGetsPrivateStudentResources() {
        assertFalse(AccessPolicy.permits(ActorRole.TEACHER, ProtectedResource.OWN_ASSESSMENT))
        assertFalse(AccessPolicy.permits(ActorRole.TEACHER, ProtectedResource.OWN_RAW_HEALTH))
        assertTrue(AccessPolicy.permits(ActorRole.TEACHER, ProtectedResource.TEACHER_ALERT_SUMMARY))
    }

    @Test fun internalChannelIsNotAvailableToTeacher() {
        val teacher = ActorContext("teacher-a", ActorRole.TEACHER, DataDomain.DEMO)
        assertTrue(AccessPolicy.requireSystem(teacher, DataDomain.DEMO) is CareResult.Failure)
    }
}
