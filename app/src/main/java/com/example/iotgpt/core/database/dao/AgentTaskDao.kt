package com.example.iotgpt.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.iotgpt.core.database.entity.AgentTaskEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access for local agent task history and task state counters.
 */
@Dao
interface AgentTaskDao {
    @Query("SELECT * FROM agent_tasks ORDER BY createdAt DESC")
    fun observeTasks(): Flow<List<AgentTaskEntity>>

    @Query("SELECT * FROM agent_tasks WHERE type = :type ORDER BY createdAt DESC LIMIT 1")
    fun observeLatestTaskByType(type: String): Flow<AgentTaskEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: AgentTaskEntity)

    @Query(
        """
        UPDATE agent_tasks
        SET status = :status,
            description = :description,
            completedAt = :completedAt
        WHERE id = :id
        """
    )
    suspend fun updateTaskStatus(
        id: String,
        status: String,
        description: String,
        completedAt: Long?
    )

    @Query("SELECT COUNT(*) FROM agent_tasks WHERE status = :status")
    fun observeTaskCountByStatus(status: String): Flow<Int>
}
