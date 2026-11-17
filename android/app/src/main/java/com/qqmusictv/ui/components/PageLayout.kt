package com.qqmusictv.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * tvOS 安全区规范：1920×1080 画布下左右各 90pt，即视口宽度的 4.6875%。
 * 按比例换算而非固定 dp：1080p（960dp）→ 45dp，4K（1920dp）→ 90dp 封顶。
 * 下限 40dp 保住窄屏布局，上限 90dp 避免超宽屏内容过度收缩。
 */
internal fun settingsPageInset(viewportWidth: Dp): Dp =
    (viewportWidth * 0.046875f).coerceIn(40.dp, 90.dp)

private val LocalPageHorizontalInset = staticCompositionLocalOf<Dp?> { null }

/** Read at the full page, never from the width of a nested card/column. */
val pageHorizontalInset: Dp
    @Composable get() = LocalPageHorizontalInset.current
        ?: settingsPageInset(LocalConfiguration.current.screenWidthDp.dp)

@Composable
fun PageLayout(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalPageHorizontalInset provides settingsPageInset(maxWidth)) {
            content()
        }
    }
}
