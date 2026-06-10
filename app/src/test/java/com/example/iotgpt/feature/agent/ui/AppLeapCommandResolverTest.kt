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

    @Test
    fun extractsDialPhoneNumber() {
        assertEquals("13800138000", AppLeapCommandResolver.extractPhoneNumber("拨打 138 0013 8000"))
        assertEquals("+8613800138000", AppLeapCommandResolver.extractPhoneNumber("call +86-13800138000"))
    }

    @Test
    fun extractsMapNavigationQuery() {
        assertEquals("北京邮电大学", AppLeapCommandResolver.extractMapQuery("导航到 北京邮电大学"))
        assertEquals("上海虹桥站", AppLeapCommandResolver.extractMapQuery("地图导航 上海虹桥站"))
    }

    @Test
    fun extractsMarketTargetAndDetectsPackageNames() {
        assertEquals("QQ", AppLeapCommandResolver.extractMarketTarget("应用市场搜索 QQ"))
        assertEquals("com.tencent.mobileqq", AppLeapCommandResolver.extractMarketTarget("安装 com.tencent.mobileqq"))
        assertTrue(AppLeapCommandResolver.isPackageName("com.tencent.mobileqq"))
    }
}
