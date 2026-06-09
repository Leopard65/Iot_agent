package com.example.iotgpt.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores one chat conversation and its generated summary metadata.
 */
@Entity(
    tableName = "conversations",
    indices = [
        Index(value = ["updatedAt"]),
        Index(value = ["modelId"])
    ]
)
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val summary: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val modelId: String,
    val messageCount: Int
)
