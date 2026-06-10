package com.example.iotgpt.feature.agent.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLeapCommandResolverTest {
    @Test
    fun extractsCompactChineseAppLaunchCommand() {
        assertEquals("qq", AppLeapCommandResolver.extractAppLaunchTarget("打开qq"))
        assertEquals("QQ", AppLeapCommandResolver.extractAppLaunchTarget("打开 QQ"))
    }

    @Test
    fun resolvesQqAliasToKnownPackages() {
        val candidates = AppLeapCommandResolver.packageCandidatesFor("qq")

        assertEquals("com.tencent.mobileqq", candidates.first())
        assertTrue(candidates.contains("com.tencent.tim"))
    }

    @Test
    fun keepsRawPackageNameAsFallbackCandidate() {
        assertEquals(
            "com.example.missing.app",
            AppLeapCommandResolver.extractAppLaunchTarget("打开 com.example.missing.app")
        )
        assertEquals(
            listOf("com.example.target"),
            AppLeapCommandResolver.packageCandidatesFor("com.example.target")
        )
    }

    @Test
    fun trimsBrowserSearchCommandToSearchQuery() {
        assertEquals("ESP32 MQTT", AppLeapCommandResolver.toSearchQuery("打开浏览器搜索 ESP32 MQTT"))
    }
}
