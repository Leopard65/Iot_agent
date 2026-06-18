package com.example.iotgpt.feature.stats.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the pure chart density-note logic used by the stats charts.
 */
class ChartDensityNoteTest {

    @Test
    fun normalData_returnsNoNote() {
        assertNull(chartDensityNote(seriesCount = 3, pointCount = 6, aggregated = false, maxModels = 5))
    }

    @Test
    fun fewPoints_returnsFewNote() {
        assertEquals(
            "数据较少，继续使用后趋势更清晰",
            chartDensityNote(seriesCount = 3, pointCount = 2, aggregated = false, maxModels = 5)
        )
    }

    @Test
    fun singleModel_returnsFewNote() {
        assertEquals(
            "数据较少，继续使用后趋势更清晰",
            chartDensityNote(seriesCount = 1, pointCount = 9, aggregated = false, maxModels = 5)
        )
    }

    @Test
    fun aggregated_returnsManyNote() {
        assertEquals(
            "仅显示调用最多的 5 个模型，其余归入“其他”",
            chartDensityNote(seriesCount = 6, pointCount = 9, aggregated = true, maxModels = 5)
        )
    }

    @Test
    fun aggregatedTakesPriorityOverFew() {
        assertEquals(
            "仅显示调用最多的 5 个模型，其余归入“其他”",
            chartDensityNote(seriesCount = 6, pointCount = 1, aggregated = true, maxModels = 5)
        )
    }
}
