package com.example.mdd_calender.feature.health

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthProvidersTest {
    @Test
    fun disconnectedProviderReturnsExplicitStateNotFakeEmptyData() = runBlocking {
        val provider = DisconnectedHealthProvider()

        val result = provider.fetch("task", "student", HealthMetric.HEART_RATE, 0)

        assertEquals(ProviderFetchResult.NotConnected, result)
        assertTrue(provider.capabilities().all { it.availability == HealthProviderAvailability.NOT_CONNECTED })
    }

    @Test
    fun demoProviderMarksEverySampleAsDemo() = runBlocking {
        val provider = DemoHealthProvider(DemoHealthScenario.BALANCED) { 2_000_000_000_000L }

        val result = provider.fetch("task", "student", HealthMetric.SLEEP, 0) as ProviderFetchResult.Samples

        assertTrue(result.values.isNotEmpty())
        assertTrue(result.values.all { it.domain == HealthDataDomain.DEMO })
    }
}
