package com.example.mdd_calender.feature.health

import java.util.Collections

class DisconnectedHealthProvider : HealthProviderClient {
    override fun capabilities(): List<HealthProviderCapability> = HealthMetric.entries.map {
        HealthProviderCapability(
            metric = it,
            availability = HealthProviderAvailability.NOT_CONNECTED,
            domain = HealthDataDomain.REAL,
            description = "尚未接入手机健康平台或穿戴设备",
        )
    }

    override suspend fun requestPermission(metric: HealthMetric): Boolean = false
    override suspend fun revokePermission(metric: HealthMetric) = Unit

    override suspend fun fetch(
        taskId: String,
        studentId: String,
        metric: HealthMetric,
        sinceEpochMillis: Long,
    ): ProviderFetchResult = ProviderFetchResult.NotConnected

    override fun cancel(taskId: String) = Unit
}

enum class DemoHealthScenario {
    BALANCED,
    ELEVATED_HEART_RATE,
    SHORT_SLEEP,
    LOW_QUALITY,
    NO_DATA,
    STALE_DATA,
}

/** Deterministic fictional data only. It never claims access to a device or system health store. */
class DemoHealthProvider(
    private val scenario: DemoHealthScenario,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) : HealthProviderClient {
    private val cancelledTaskIds = Collections.synchronizedSet(mutableSetOf<String>())

    override fun capabilities(): List<HealthProviderCapability> = HealthMetric.entries.map {
        HealthProviderCapability(
            metric = it,
            availability = HealthProviderAvailability.AVAILABLE,
            domain = HealthDataDomain.DEMO,
            description = "虚构演示数据，不连接真实设备",
        )
    }

    override suspend fun requestPermission(metric: HealthMetric): Boolean = true
    override suspend fun revokePermission(metric: HealthMetric) = Unit
    override fun cancel(taskId: String) { cancelledTaskIds += taskId }

    override suspend fun fetch(
        taskId: String,
        studentId: String,
        metric: HealthMetric,
        sinceEpochMillis: Long,
    ): ProviderFetchResult {
        if (taskId in cancelledTaskIds) return ProviderFetchResult.Cancelled
        if (scenario == DemoHealthScenario.NO_DATA) return ProviderFetchResult.Samples(emptyList())

        val now = nowEpochMillis()
        val baseTime = if (scenario == DemoHealthScenario.STALE_DATA) now - 10 * DAY else now
        val quality = if (scenario == DemoHealthScenario.LOW_QUALITY) 0.25 else 0.92
        val samples = when (metric) {
            HealthMetric.HEART_RATE -> List(6) { index ->
                HealthSample(
                    ownerId = studentId,
                    metric = metric,
                    observedAtEpochMillis = baseTime - index * HOUR,
                    value = if (scenario == DemoHealthScenario.ELEVATED_HEART_RATE) 106.0 + index else 72.0 + index,
                    unit = "bpm",
                    quality = quality,
                    domain = HealthDataDomain.DEMO,
                )
            }
            HealthMetric.SLEEP -> List(3) { index ->
                HealthSample(
                    ownerId = studentId,
                    metric = metric,
                    observedAtEpochMillis = baseTime - index * DAY,
                    value = if (scenario == DemoHealthScenario.SHORT_SLEEP) 5.2 + index * 0.1 else 7.4 + index * 0.1,
                    unit = "hours",
                    quality = quality,
                    domain = HealthDataDomain.DEMO,
                )
            }
        }.filter { it.observedAtEpochMillis >= sinceEpochMillis }
        return if (taskId in cancelledTaskIds) ProviderFetchResult.Cancelled else ProviderFetchResult.Samples(samples)
    }

    private companion object {
        const val HOUR = 60L * 60 * 1000
        const val DAY = 24 * HOUR
    }
}
