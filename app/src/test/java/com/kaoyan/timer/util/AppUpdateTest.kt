package com.kaoyan.timer.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateTest {

    @Test
    fun twoTenIsNewerThanTwoNine() {
        assertTrue(AppUpdate.compareVersions("v2.10", "v2.9") > 0)
        assertTrue(AppUpdate.compareVersions("2.10", "2.9") > 0)
        assertTrue(AppUpdate.compareVersions("2.9", "2.10") < 0)
    }

    @Test
    fun equalVersionsIgnoreVPrefix() {
        assertEquals(0, AppUpdate.compareVersions("v2.5", "2.5"))
        assertEquals(0, AppUpdate.compareVersions("2.5.0", "2.5"))
    }

    @Test
    fun multiSegmentOrdering() {
        assertTrue(AppUpdate.compareVersions("1.2.3", "1.2.2") > 0)
        assertTrue(AppUpdate.compareVersions("3.0", "2.99") > 0)
        assertTrue(AppUpdate.compareVersions("1.0", "1.0.1") < 0)
    }

    @Test
    fun onlyTrustsGithubReleaseDownloadApk() {
        assertTrue(
            AppUpdate.isTrustedApkUrl(
                "https://github.com/38yuanzhao/kaoyan-timer/releases/download/v2.5/app-release.apk"
            )
        )
        assertFalse(
            AppUpdate.isTrustedApkUrl(
                "http://github.com/38yuanzhao/kaoyan-timer/releases/download/v2.5/app-release.apk"
            )
        )
        assertFalse(
            AppUpdate.isTrustedApkUrl(
                "https://evil.example/38yuanzhao/kaoyan-timer/releases/download/v2.5/app-release.apk"
            )
        )
        assertFalse(
            AppUpdate.isTrustedApkUrl(
                "https://github.com/38yuanzhao/kaoyan-timer/releases/download/v2.5/notes.txt"
            )
        )
    }
}
