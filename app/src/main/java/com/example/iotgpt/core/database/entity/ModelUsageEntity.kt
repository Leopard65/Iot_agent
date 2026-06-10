package com.example.iotgpt.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores one model usage record for statistics and dashboard reporting.
 */
@Entity(
    tableName = "model_usages",
    indices = [
        Index(value = ["modelId"]),
        Index(value = ["conversationId"]),
        Index(value = ["createdAt"])
    ]
)
data class ModelUsageEntity(
    @PrimaryKey val id: String,
    val modelId: String,
    val conversationId: String?,
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val isEstimated: Boolean,
    val createdAt: Long
)
