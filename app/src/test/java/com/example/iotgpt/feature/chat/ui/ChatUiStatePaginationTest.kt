package com.example.iotgpt.feature.chat.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ChatUiState] pagination-related computed properties.
 */
class ChatUiStatePaginationTest {

    @Test
    fun hasOlderMessages_totalGreaterThanLimit_returnsTrue() {
        val state = ChatUiState(
            totalMessageCount = 100,
            visibleMessageLimit = 50
        )
        assertTrue(state.hasOlderMessages)
    }

    @Test
    fun hasOlderMessages_totalEqualsLimit_returnsFalse() {
        val state = ChatUiState(
            totalMessageCount = 50,
            visibleMessageLimit = 50
        )
        assertFalse(state.hasOlderMessages)
    }

    @Test
    fun hasOlderMessages_totalLessThanLimit_returnsFalse() {
        val state = ChatUiState(
            totalMessageCount = 10,
            visibleMessageLimit = 50
        )
        assertFalse(state.hasOlderMessages)
    }

    @Test
    fun hasOlderMessages_emptyConversation_returnsFalse() {
        val state = ChatUiState(
            totalMessageCount = 0,
            visibleMessageLimit = 50
        )
        assertFalse(state.hasOlderMessages)
    }

    @Test
    fun hasOlderMessages_afterLoadOlder_returnsFalseWhenAllLoaded() {
        // After loading all messages, total == visible
        val state = ChatUiState(
            totalMessageCount = 80,
            visibleMessageLimit = 80
        )
        assertFalse(state.hasOlderMessages)
    }

    @Test
    fun defaultVisibleMessageLimit_isFifty() {
        val state = ChatUiState()
        assertEquals(50, state.visibleMessageLimit)
    }

    @Test
    fun defaultTotalMessageCount_isZero() {
        val state = ChatUiState()
        assertEquals(0, state.totalMessageCount)
    }
}
