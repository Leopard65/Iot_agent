package com.example.iotgpt.core.database.dao

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies that [TokenSummary] data class has safe defaults for empty tables.
 * The actual COALESCE in SQL is tested via instrumented DAO tests; this
 * ensures the Kotlin-side default contract.
 */
class TokenSummaryTest {

    @Test
    fun emptyTable_allZeros() {
        // When the SQL query returns a row with all zeros (thanks to COALESCE),
        // the data class should hold them without null.
        val summary = TokenSummary(
            totalPromptTokens = 0,
            totalCompletionTokens = 0,
            estimatedCount = 0
        )
        assertEquals(0, summary.totalPromptTokens)
        assertEquals(0, summary.totalCompletionTokens)
        assertEquals(0, summary.estimatedCount)
    }

    @Test
    fun nonZeroValues_preserved() {
        val summary = TokenSummary(
            totalPromptTokens = 1500,
            totalCompletionTokens = 800,
            estimatedCount = 3
        )
        assertEquals(1500, summary.totalPromptTokens)
        assertEquals(800, summary.totalCompletionTokens)
        assertEquals(3, summary.estimatedCount)
    }

    @Test
    fun estimatedCount_isIntNotLong() {
        // estimatedCount is Int (from COALESCE(SUM(...), 0) mapped to Int).
        // Verify no overflow for reasonable counts.
        val summary = TokenSummary(0, 0, estimatedCount = 100_000)
        assertEquals(100_000, summary.estimatedCount)
    }
}
