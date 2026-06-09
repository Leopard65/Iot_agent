package com.example.iotgpt.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores local agent task status for classroom-safe Claw mode demonstrations.
 */
@Entity(
    tableName = "agent_tasks",
    indices = [
        Index(value = ["type"]),
        Index(value = ["status"]),
        Index(value = ["createdAt"])
    ]
)
data class AgentTaskEntity(
    @PrimaryKey val id: String,
    val type: String,
    val status: String,
    val description: String,
    val createdAt: Long,
    val completedAt: Long?
)
