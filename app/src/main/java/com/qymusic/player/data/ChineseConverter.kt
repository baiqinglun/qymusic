package com.qymusic.player.data

import android.content.Context
import java.util.concurrent.atomic.AtomicReference

/**
 * 简体 ⇄ 繁体一键转换：基于 OpenCC 单字字典（TSCharacters / STCharacters，
 * Apache-2.0）。多音字/异体字按字典首个候选转换，转换后可继续手动微调。
 */
object ChineseConverter {

    enum class Direction { S2T, T2S }

    private class Tables(
        val s2t: Map<Char, String>,
        val t2s: Map<Char, String>,
    )

    private val tables = AtomicReference<Tables?>(null)

    fun convert(context: Context, text: String, direction: Direction): String {
        if (text.isBlank()) return text
        val table = when (direction) {
            Direction.S2T -> ensureTables(context).s2t
            Direction.T2S -> ensureTables(context).t2s
        }
        return buildString(text.length) {
            for (ch in text) {
                append(table[ch] ?: ch.toString())
            }
        }
    }

    private fun ensureTables(context: Context): Tables {
        tables.get()?.let { return it }
        synchronized(this) {
            tables.get()?.let { return it }
            val appContext = context.applicationContext
            // OpenCC 命名：TSCharacters = 繁→简（键为繁体），STCharacters = 简→繁（键为简体）
            val s2t = loadTable(appContext, "opencc/st_characters.txt")
            val t2s = loadTable(appContext, "opencc/ts_characters.txt")
            val value = Tables(s2t, t2s)
            tables.set(value)
            return value
        }
    }

    /** 行格式：单字 \t 候选1 候选2 …（取首个候选）。 */
    private fun loadTable(context: Context, assetPath: String): Map<Char, String> =
        runCatching {
            context.assets.open(assetPath).use { input ->
                input.bufferedReader(Charsets.UTF_8).readLines()
            }
        }.getOrDefault(emptyList())
            .asSequence()
            .filter { line -> !line.startsWith("#") }
            .mapNotNull { line ->
                val columns = line.split("\t")
                if (columns.size < 2) return@mapNotNull null
                val from = columns[0].trim()
                val to = columns[1].trim().split(' ', '　').firstOrNull()?.trim()
                if (from.length == 1 && !to.isNullOrBlank()) from[0] to to else null
            }
            .toMap()
}
