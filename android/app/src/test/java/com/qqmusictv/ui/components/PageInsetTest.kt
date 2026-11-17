package com.qqmusictv.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class PageInsetTest {
    /** tvOS 规范：左右安全区为视口宽度的 4.6875%（1920pt 画布下 90pt）。 */
    @Test fun gutterFollowsTvOsSafeAreaRatio() {
        for ((width, expected) in listOf(600f to 40f, 880f to 41.25f, 921.6f to 43.2f, 960f to 45f,
            1040f to 48.75f, 1536f to 72f, 1920f to 90f, 2560f to 90f)) {
            assertEquals(expected, settingsPageInset(width.dp).value, 0.01f)
        }
    }
}
