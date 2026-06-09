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
}

data class ModelUsageSummary(
    val modelId: String,
    val callCount: Int,
    val totalTokens: Long
)
