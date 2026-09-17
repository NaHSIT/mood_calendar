package com.example.mdd_calender.domain.model

enum class ActorRole { STUDENT, TEACHER, SYSTEM }
enum class DataDomain { DEMO, PRODUCTION }

data class ActorContext(
    val actorId: String,
    val role: ActorRole,
    val dataDomain: DataDomain,
)

data class StudentRef(val studentId: String, val studentCode: String)

sealed interface CareFailure {
    data object Unauthenticated : CareFailure
    data class Forbidden(val reason: String) : CareFailure
    data class NotFound(val objectType: String, val objectId: String) : CareFailure
    data class InvalidInput(val reason: String) : CareFailure
    data class Conflict(val reason: String) : CareFailure
    data class NotConfigured(val capability: String) : CareFailure
    data class TemporarilyUnavailable(val reason: String) : CareFailure
    data class CryptographyFailure(val reason: String) : CareFailure
}

sealed interface CareResult<out T> {
    data class Success<T>(val value: T) : CareResult<T>
    data class Failure(val error: CareFailure) : CareResult<Nothing>
}

fun interface UtcClock { fun nowEpochMillis(): Long }

object SystemUtcClock : UtcClock {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()
}
