package com.example.mdd_calender.integration.school

import com.example.mdd_calender.domain.model.CareResult
import com.example.mdd_calender.domain.model.DeliveryStatus
import com.example.mdd_calender.domain.port.SchoolAlertDto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SimulatedSchoolPlatformGatewayTest {
    private val alert = SchoolAlertDto(
        eventId = "alert-1",
        studentCode = "DEMO-S001",
        concernLevel = "MODERATE",
        minimalReasonTags = setOf("ASSESSMENT_REVIEW_REQUIRED"),
        occurredAtEpochMillis = 100L,
        simulated = true,
    )

    @Test
    fun deliveryIsIdempotentAndReceiptCanBeQueried() = runBlocking {
        val gateway = SimulatedSchoolPlatformGateway("teacher-1") { 123L }

        val first = gateway.deliver(alert, "alert-1:r1") as CareResult.Success
        val duplicate = gateway.deliver(alert, "alert-1:r1") as CareResult.Success

        assertEquals(DeliveryStatus.SIMULATED_DELIVERED, first.value.status)
        assertEquals(first.value.deliveryId, duplicate.value.deliveryId)
        assertEquals(first.value.receipt, gateway.queryReceipt(first.value.deliveryId).success().receipt)
    }

    @Test
    fun retryAndPermanentFailureAreExplicitSimulationStates() = runBlocking {
        val gateway = SimulatedSchoolPlatformGateway("teacher-1") { 1_000L }
        gateway.setNextOutcome(SimulatedSchoolPlatformGateway.NextOutcome.RETRY)
        val retry = gateway.deliver(alert, "alert-1:retry").success()
        assertEquals(DeliveryStatus.RETRY_PENDING, retry.status)
        assertNotNull(retry.nextRetryAtEpochMillis)

        gateway.setNextOutcome(SimulatedSchoolPlatformGateway.NextOutcome.FAIL)
        val failed = gateway.deliver(alert, "alert-1:failed").success()
        assertEquals(DeliveryStatus.FAILED, failed.status)
    }

    private fun <T> CareResult<T>.success(): T = when (this) {
        is CareResult.Success -> value
        is CareResult.Failure -> error("Expected success but got $error")
    }
}
