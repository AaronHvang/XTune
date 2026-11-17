package com.qqmusictv.data.qq

import com.qqmusictv.data.network.LyricLine
import com.qqmusictv.data.network.LyricWord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * QQ Music lyric source. The web API only returns LRC (line-level) timing, so we
 * synthesize word-level timing after parsing: CJK text splits per-character;
 * Latin/split text splits on whitespace; each word gets an equal slice of the
 * line's startMs..endMs window. This is good enough for the karaoke-style
 * per-character wipe animation used by the player.
 */
@Singleton
class QQLyricsSource @Inject constructor(private val api: QQMusicApi) {

    private val cache = HashMap<String, Pair<Long, List<LyricLine>>>()
    private val creditLine = Regex(
        """^(作词|作曲|编曲|制作人|监制|出品|录音|混音|母带|吉他|贝斯|和声|和音|统筹|企划|发行|OP|SP|ISRC)\s*[:：]""",
        RegexOption.IGNORE_CASE,
    )

    suspend fun getLyrics(
        songId: String,
        storefront: String,
        title: String = "",
        artist: String = "",
        durationSec: Long = 0,
    ): List<LyricLine> = withContext(Dispatchers.IO) {
        cached(songId)?.let { return@withContext it }
        val raw = api.lyric(songId) ?: return@withContext emptyList()
        val lines = parse(raw)
        if (lines.isNotEmpty()) store(songId, lines)
        lines
    }

    private fun cached(songId: String): List<LyricLine>? {
        val hit = cache[songId] ?: return null
        if (System.currentTimeMillis() - hit.first > CACHE_MS) { cache.remove(songId); return null }
        return hit.second
    }

    private fun store(songId: String, lines: List<LyricLine>) {
        cache[songId] = System.currentTimeMillis() to lines
    }

    private fun parse(raw: String): List<LyricLine> {
        val tag = Regex("""\[(\d{1,2}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
        val staged = ArrayList<Pair<Long, String>>()
        raw.lineSequence().forEach { line ->
            val matches = tag.findAll(line).toList()
            if (matches.isEmpty()) return@forEach
            val text = line.substring(matches.last().range.last + 1).trim()
            if (text.isEmpty()) return@forEach
            matches.forEach { m ->
                val min = m.groupValues[1].toLongOrNull() ?: return@forEach
                val sec = m.groupValues[2].toLongOrNull() ?: return@forEach
                val frac = m.groupValues[3]
                val fracMs = when {
                    frac.isEmpty() -> 0L
                    frac.length == 1 -> frac.toLong() * 100
                    frac.length == 2 -> frac.toLong() * 10
                    else -> frac.take(3).toLong()
                }
                val timeMs = min * 60_000 + sec * 1_000 + fracMs
                // Drop "[ti:]/[ar:]"-style metadata and opening production credits.
                if (text.startsWith("[") && text.endsWith("]")) return@forEach
                if (timeMs < 15_000 && creditLine.containsMatchIn(text)) return@forEach
                staged.add(timeMs to text)
            }
        }
        val sorted = staged.sortedBy { it.first }
        return sorted.mapIndexed { i, (time, text) ->
            val end = sorted.getOrNull(i + 1)?.first ?: (time + 5_000)
            val words = synthesizeWords(text, time, end)
            LyricLine(startMs = time, endMs = end, text = text, words = words)
        }
    }

    /**
     * Break a lyric line into LyricWord entries with even timing slices across
     * startMs..endMs. Pure LRC sources have no word timing, so we synthesize it
     * on the way in. This keeps the per-character wipe animation fed even when the
     * upstream API doesn't ship KSC data.
     *
     *  - Whitespace-containing text ("Hello world") splits on whitespace.
     *  - CJK / non-whitespace text splits per character.
     */
    private fun synthesizeWords(text: String, startMs: Long, endMs: Long): List<LyricWord> {
        if (text.isEmpty()) return emptyList()
        val tokens: List<String> = if (text.any { it.isWhitespace() }) {
            text.split(Regex("""\s+""")).filter { it.isNotEmpty() }
        } else {
            text.map { it.toString() }
        }
        if (tokens.isEmpty()) return emptyList()
        val totalDuration = (endMs - startMs).coerceAtLeast(1L)
        val sliceMs = totalDuration / tokens.size
        return tokens.mapIndexed { idx, tok ->
            val wStart = startMs + idx * sliceMs
            val wEnd = if (idx == tokens.size - 1) endMs else wStart + sliceMs
            LyricWord(startMs = wStart, endMs = wEnd, text = tok)
        }
    }

    private companion object {
        const val CACHE_MS = 30 * 60 * 1000L
    }
}
