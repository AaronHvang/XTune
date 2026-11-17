package com.qqmusictv.data.updates

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.qqmusictv.BuildConfig
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 28], manifest = Config.NONE)
class ProductionUpdateSourceTest {
    @Test fun productionSourceAcceptsItsOwnDownloadAndRejectsPreviewHost() {
        assertEquals("https://update.invalid", BuildConfig.UPDATE_BASE_URL)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val updater = AppUpdates(context, BuildConfig.UPDATE_BASE_URL + "/update.json")
        val manifest = JSONObject().put("channel", "user").put("packageName", context.packageName)
            .put("versionName", "3.0-V56").put("versionCode", 56).put("sizeBytes", 7000000)
            .put("minSdk", 23).put("sha256", "a".repeat(64)).put("notes", "")
            .put("apkUrl", "https://update.invalid/downloads/QQMusicTV-V56.apk")
        assertEquals(56, updater.parse(manifest.toString()).code)
        manifest.put("apkUrl", "https://update-test.update.invalid/downloads/QQMusicTV-V56.apk")
        try { updater.parse(manifest.toString()); fail("preview host must be rejected") }
        catch (_: IllegalArgumentException) { }
    }
}
