package com.example.mdd_calender.integration.school

import com.example.mdd_calender.domain.model.CareFailure
import com.example.mdd_calender.domain.model.CareResult
import com.example.mdd_calender.domain.model.DeliveryRecord
import com.example.mdd_calender.domain.model.DeliveryStatus
import com.example.mdd_calender.domain.port.SchoolAlertDto
import com.example.mdd_calender.domain.port.SchoolPlatformGateway
import java.util.UUID

/** Deterministic local gateway. It never contacts a school platform. */
class SimulatedSchoolPlatformGateway(
    private val recipientId: String,
    private val clock: () -> Long = System::currentTimeMillis,
) : SchoolPlatformGateway {
    enum class NextOutcome { DELIVER, RETRY, FAIL }

    private val records = LinkedHashMap<String, DeliveryRecord>()
    private val lock = Any()
    private var nextOutcome = NextOutcome.DELIVER

    fun setNextOutcome(outcome: NextOutcome) {
        nextOutcome = outcome
    }

    override suspend fun deliver(alert: SchoolAlertDto, idempotencyKey: String): CareResult<DeliveryRecord> {
        return synchronized(lock) {
            val previous = records[idempotencyKey]
            previous?.let {
                if (it.status != DeliveryStatus.RETRY_PENDING || (it.nextRetryAtEpochMillis ?: 0) > clock()) {
                    return@synchronized CareResult.Success(it)
                }
            }
            val outcome = nextOutcome.also { nextOutcome = NextOutcome.DELIVER }
            val deliveryId = "sim-${UUID.randomUUID()}"
            val record = when (outcome) {
            NextOutcome.DELIVER -> DeliveryRecord(
                deliveryId = deliveryId,
                alertId = alert.eventId,
                recipientId = recipientId,
                attemptCount = 1,
                nextRetryAtEpochMillis = null,
                simulated = true,
                idempotencyKey = idempotencyKey,
                status = DeliveryStatus.SIMULATED_DELIVERED,
                receipt = "SIMULATED_RECEIPT:${alert.eventId}:${clock()}",
            )
            NextOutcome.RETRY -> DeliveryRecord(
                deliveryId = deliveryId,
                alertId = alert.eventId,
                recipientId = recipientId,
                attemptCount = 1,
                nextRetryAtEpochMillis = clock() + 60_000,
                simulated = true,
                idempotencyKey = idempotencyKey,
                status = DeliveryStatus.RETRY_PENDING,
                receipt = null,
            )
            NextOutcome.FAIL -> DeliveryRecord(
                deliveryId = deliveryId,
                alertId = alert.eventId,
                recipientId = recipientId,
                attemptCount = 1,
                nextRetryAtEpochMillis = null,
                simulated = true,
                idempotencyKey = idempotencyKey,
                status = DeliveryStatus.FAILED,
                receipt = "SIMULATED_FAILURE:${alert.eventId}",
            )
            }
            val stable = record.copy(deliveryId = previous?.deliveryId ?: record.deliveryId, attemptCount = (previous?.attemptCount ?: 0) + 1)
            records[idempotencyKey] = stable
            CareResult.Success(stable)
        }
    }

    override suspend fun queryReceipt(deliveryId: String): CareResult<DeliveryRecord> =
        synchronized(lock) {
            records.values.firstOrNull { it.deliveryId == deliveryId }
                ?.let { CareResult.Success(it) }
                ?: CareResult.Failure(CareFailure.NotFound("delivery", deliveryId))
        }

    override suspend fun submitDisposition(eventId: String, disposition: String): CareResult<Unit> =
        if (eventId.isBlank() || disposition.isBlank()) {
            CareResult.Failure(CareFailure.InvalidInput("Event and disposition are required"))
        } else {
            CareResult.Success(Unit)
        }
}
