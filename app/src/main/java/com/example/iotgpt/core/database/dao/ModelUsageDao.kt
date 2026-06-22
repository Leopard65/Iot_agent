package com.example.iotgpt.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.iotgpt.core.database.entity.ModelUsageEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access for model usage records and aggregated usage statistics.
 */
@Dao
interface ModelUsageDao {
    @Query("SELECT * FROM model_usages ORDER BY createdAt DESC")
    fun observeUsageRecords(): Flow<List<ModelUsageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsage(usage: ModelUsageEntity)

    @Query("DELETE FROM model_usages")
    suspend fun clearUsage()

    @Query(
        """
        SELECT modelId,
               COUNT(*) AS callCount,
               COALESCE(SUM(totalTokens), 0) AS totalTokens
        FROM model_usages
        GROUP BY modelId
        ORDER BY callCount DESC
        """
    )
    fun observeUsageByModel(): Flow<List<ModelUsageSummary>>

    @Query("SELECT MAX(createdAt) FROM model_usages")
    fun observeLastRequestAt(): Flow<Long?>

    /**
     * Aggregates usage records by hour and model, returning at most [maxHours]
     * most recent hour buckets. Far cheaper than loading every raw record.
     */
    @Query(
        """
        SELECT (createdAt - (createdAt % 3600000)) AS hourStart,
               modelId,
               COUNT(*) AS callCount,
               COALESCE(SUM(totalTokens), 0) AS totalTokens,
               COALESCE(SUM(promptTokens), 0) AS promptTokens,
               COALESCE(SUM(completionTokens), 0) AS completionTokens
        FROM model_usages
        WHERE createdAt >= (
            SELECT MAX(createdAt) - :maxHoursMillis FROM model_usages
        )
        GROUP BY hourStart, modelId
        ORDER BY hourStart ASC, totalTokens DESC
        """
    )
    fun observeHourlyUsageByModel(
        maxHoursMillis: Long = 12L * 60L * 60L * 1000L
    ): Flow<List<HourlyModelUsage>>

    /** Returns global token totals and estimated-record count in a single query. */
    @Query(
        """
        SELECT COALESCE(SUM(promptTokens), 0) AS totalPromptTokens,
               COALESCE(SUM(completionTokens), 0) AS totalCompletionTokens,
               COALESCE(SUM(CASE WHEN isEstimated = 1 THEN 1 ELSE 0 END), 0) AS estimatedCount
        FROM model_usages
        """
    )
    fun observeTokenSummary(): Flow<TokenSummary>
}

data class ModelUsageSummary(
    val modelId: String,
    val callCount: Int,
    val totalTokens: Long
)

/**
 * One row of the hourly model-usage aggregation produced by
 * [ModelUsageDao.observeHourlyUsageByModel].
 */
data class HourlyModelUsage(
    val hourStart: Long,
    val modelId: String,
    val callCount: Int,
    val totalTokens: Long,
    val promptTokens: Int,
    val completionTokens: Int
)

/**
 * Global token totals and estimated-record count from [ModelUsageDao.observeTokenSummary].
 */
data class TokenSummary(
    val totalPromptTokens: Int,
    val totalCompletionTokens: Int,
    val estimatedCount: Int
)
