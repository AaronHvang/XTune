package com.qqmusictv.data.model

/**
 * Catalog artwork URLs are templates with {w}/{h}/{f} placeholders; QQ Music CDN URLs
 * (y.gtimg.cn/.../T002R500x500M000<mid>.jpg) bake the size into the path. Rewrite both
 * to the requested pixel size so list tiles don't decode full-size artwork.
 */
private val QQ_SIZE = Regex("T00([12])R\\d+x\\d+M000")
private val QQ_STEPS = intArrayOf(150, 300, 500, 800)

internal fun String.resizeArtwork(size: Int): String {
    var out = replace("{w}", "$size").replace("{h}", "$size").replace("{f}", "jpg")
    if ("y.gtimg.cn" in out) {
        val snapped = QQ_STEPS.minByOrNull { kotlin.math.abs(it - size) } ?: size
        out = out.replace(QQ_SIZE, "T00$1R${snapped}x${snapped}M000")
    }
    return out
}
