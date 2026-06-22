package com.example.iotgpt.core.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.iotgpt.core.database.AppDatabase
import com.example.iotgpt.core.database.entity.ConversationEntity
import com.example.iotgpt.core.database.entity.MessageEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for [MessageDao] pagination queries and
 * [ModelUsageDao] TokenSummary SQL. These verify Room DAO behavior on a
 * real SQLite engine.
 */
@RunWith(AndroidJUnit4::class)
class PaginationDaoInstrumentedTest {

    private lateinit var database: AppDatabase
    private lateinit var messageDao: MessageDao
    private lateinit var conversationDao: ConversationDao
    private lateinit var modelUsageDao: ModelUsageDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        messageDao = database.messageDao()
        conversationDao = database.conversationDao()
        modelUsageDao = database.modelUsageDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ── Pagination tests ────────────────────────────────────────────────

    @Test
    fun observeLatestMessages_returnsLatestNInAscendingOrder() = runBlocking {
        val convId = insertConversation()
        // Insert 60 messages
        repeat(60) { i ->
            messageDao.upsertMessage(makeMessage("msg-$i", convId, i.toLong()))
        }

        val latest50 = messageDao.observeLatestMessages(convId, 50).first()
        assertEquals(50, latest50.size)
        // Should be the newest 50 (IDs 10..59) in ASC order
        assertEquals("msg-10", latest50.first().id)
        assertEquals("msg-59", latest50.last().id)
    }

    @Test
    fun observeLatestMessages_fewerThanLimit_returnsAll() = runBlocking {
        val convId = insertConversation()
        repeat(5) { i ->
            messageDao.upsertMessage(makeMessage("msg-$i", convId, i.toLong()))
        }

        val messages = messageDao.observeLatestMessages(convId, 50).first()
        assertEquals(5, messages.size)
        assertEquals("msg-0", messages.first().id)
        assertEquals("msg-4", messages.last().id)
    }

    @Test
    fun observeLatestMessages_afterLoadOlder_returns80() = runBlocking {
        val convId = insertConversation()
        repeat(100) { i ->
            messageDao.upsertMessage(makeMessage("msg-$i", convId, i.toLong()))
        }

        val latest80 = messageDao.observeLatestMessages(convId, 80).first()
        assertEquals(80, latest80.size)
        assertEquals("msg-20", latest80.first().id)
        assertEquals("msg-99", latest80.last().id)
    }

    @Test
    fun observeLatestMessages_streamingUpsert_emitsUpdatedContent() = runBlocking {
        val convId = insertConversation()
        messageDao.upsertMessage(makeMessage("msg-0", convId, 0L, content = "initial"))

        // Collect first emission
        val initial = messageDao.observeLatestMessages(convId, 50).first()
        assertEquals("initial", initial.first().content)

        // Simulate streaming upsert (same ID, new content)
        messageDao.upsertMessage(makeMessage("msg-0", convId, 0L, content = "streamed update"))

        val updated = messageDao.observeLatestMessages(convId, 50).first()
        assertEquals("streamed update", updated.first().content)
    }

    @Test
    fun observeMessageCountForConversation_reflectsInserts() = runBlocking {
        val convId = insertConversation()
        repeat(10) { i ->
            messageDao.upsertMessage(makeMessage("msg-$i", convId, i.toLong()))
        }

        val count = messageDao.observeMessageCountForConversation(convId).first()
        assertEquals(10, count)
    }

    // ── TokenSummary tests ──────────────────────────────────────────────

    @Test
    fun observeTokenSummary_emptyTable_allZeros() = runBlocking {
        val summary = modelUsageDao.observeTokenSummary().first()
        assertEquals(0, summary.totalPromptTokens)
        assertEquals(0, summary.totalCompletionTokens)
        assertEquals(0, summary.estimatedCount)
    }

    @Test
    fun observeTokenSummary_withData_correctTotals() = runBlocking {
        modelUsageDao.insertUsage(
            com.example.iotgpt.core.database.entity.ModelUsageEntity(
                id = "u1", modelId = "m1", conversationId = "c1",
                promptTokens = 100, completionTokens = 50, totalTokens = 150,
                isEstimated = true, createdAt = 1000L
            )
        )
        modelUsageDao.insertUsage(
            com.example.iotgpt.core.database.entity.ModelUsageEntity(
                id = "u2", modelId = "m1", conversationId = "c1",
                promptTokens = 200, completionTokens = 80, totalTokens = 280,
                isEstimated = false, createdAt = 2000L
            )
        )

        val summary = modelUsageDao.observeTokenSummary().first()
        assertEquals(300, summary.totalPromptTokens)
        assertEquals(130, summary.totalCompletionTokens)
        assertEquals(1, summary.estimatedCount) // only u1 is estimated
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private suspend fun insertConversation(): String {
        val id = "conv-${System.nanoTime()}"
        conversationDao.upsertConversation(
            ConversationEntity(
                id = id, title = "Test", summary = "Test",
                createdAt = 0L, updatedAt = 0L,
                modelId = "test", messageCount = 0
            )
        )
        return id
    }

    private fun makeMessage(
        id: String,
        conversationId: String,
        createdAt: Long,
        content: String = "content-$id"
    ) = MessageEntity(
        id = id,
        conversationId = conversationId,
        role = "user",
        content = content,
        attachmentJson = null,
        createdAt = createdAt,
        isStreaming = false,
        tokenCount = content.length / 2
    )
}
