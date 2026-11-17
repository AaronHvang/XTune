package com.qqmusictv.ui.navigation

import com.qqmusictv.i18n.tr
import com.qqmusictv.R

enum class TopNavTab(private val labelId: Int) {
    NowPlaying(R.string.msg_086),
    Browse(R.string.msg_088),
    Library(R.string.msg_089),
    Search(R.string.msg_090),
    Dev(R.string.msg_182);
    val label: String get() = tr(labelId)


}
