package app.vidro.player

import java.io.File
import java.nio.charset.Charset
import java.util.Locale

/**
 * Saves a text subtitle as SRT with the current sync offset baked in, so a fixed timing
 * can be kept for next time (or shared). Reads SRT, WebVTT and SSA/ASS.
 */
object SubtitleConverter {

    private data class Cue(val startMs: Long, val endMs: Long, val text: String)

    private val supported = setOf("srt", "vtt", "ass", "ssa")

    fun canConvert(path: String?): Boolean = path != null && path.substringAfterLast('.').lowercase() in supported

    /**
     * Writes "<name>.vidro.srt" next to the source (or into [fallbackDir] if that folder isn't
     * writable). Returns the written file, or null if nothing could be parsed.
     */
    fun saveAsSrt(sourcePath: String, delayMs: Long, charset: Charset, fallbackDir: File): File? {
        val source = File(sourcePath)
        val text = runCatching { source.readText(charset) }.getOrNull() ?: return null
        val cues = when (source.extension.lowercase()) {
            "ass", "ssa" -> parseAss(text)
            else -> parseSrtOrVtt(text)
        }.map { it.copy(startMs = (it.startMs + delayMs).coerceAtLeast(0), endMs = (it.endMs + delayMs).coerceAtLeast(0)) }
            .filter { it.endMs > it.startMs }
        if (cues.isEmpty()) return null

        val out = buildString {
            cues.forEachIndexed { i, cue ->
                append(i + 1).append('\n')
                append(time(cue.startMs)).append(" --> ").append(time(cue.endMs)).append('\n')
                append(cue.text).append("\n\n")
            }
        }
        val name = source.nameWithoutExtension + ".vidro.srt"
        val primary = File(source.parentFile, name)
        return runCatching { primary.writeText(out, Charsets.UTF_8); primary }.getOrNull()
            ?: runCatching { File(fallbackDir, name).apply { writeText(out, Charsets.UTF_8) } }.getOrNull()
    }

    private val srtTime = Regex("""(?:(\d+):)?(\d{1,2}):(\d{2})[,.](\d{1,3})""")

    private fun parseSrtOrVtt(text: String): List<Cue> {
        val cues = mutableListOf<Cue>()
        val blocks = text.replace("\r\n", "\n").replace('\r', '\n').split(Regex("\n\\s*\n"))
        for (block in blocks) {
            val lines = block.lines().filter { it.isNotBlank() }
            val timingIndex = lines.indexOfFirst { "-->" in it }
            if (timingIndex < 0) continue
            val (from, to) = lines[timingIndex].split("-->").map { it.trim() }
            val start = parseTime(from) ?: continue
            val end = parseTime(to.substringBefore(' ')) ?: continue
            val body = lines.drop(timingIndex + 1).joinToString("\n")
            if (body.isNotBlank()) cues += Cue(start, end, body)
        }
        return cues
    }

    private fun parseTime(value: String): Long? {
        val m = srtTime.find(value) ?: return null
        val h = m.groupValues[1].toLongOrNull() ?: 0
        val min = m.groupValues[2].toLong()
        val s = m.groupValues[3].toLong()
        val ms = m.groupValues[4].padEnd(3, '0').toLong()
        return ((h * 60 + min) * 60 + s) * 1000 + ms
    }

    private fun parseAss(text: String): List<Cue> {
        var format: List<String> = listOf("layer", "start", "end", "style", "name", "marginl", "marginr", "marginv", "effect", "text")
        val cues = mutableListOf<Cue>()
        for (raw in text.lines()) {
            val line = raw.trim()
            when {
                line.startsWith("Format:", ignoreCase = true) && cues.isEmpty() ->
                    format = line.substringAfter(':').split(',').map { it.trim().lowercase() }
                line.startsWith("Dialogue:", ignoreCase = true) -> {
                    val fields = line.substringAfter(':').split(',', limit = format.size)
                    if (fields.size < format.size) continue
                    val start = assTime(fields[format.indexOf("start")].trim()) ?: continue
                    val end = assTime(fields[format.indexOf("end")].trim()) ?: continue
                    val body = fields[format.indexOf("text")]
                        .replace(Regex("""\{[^}]*\}"""), "")
                        .replace("\\N", "\n").replace("\\n", "\n").replace("\\h", " ")
                        .trim()
                    if (body.isNotEmpty()) cues += Cue(start, end, body)
                }
            }
        }
        return cues.sortedBy { it.startMs }
    }

    private fun assTime(value: String): Long? {
        val parts = value.split(':')
        if (parts.size != 3) return null
        val h = parts[0].toLongOrNull() ?: return null
        val m = parts[1].toLongOrNull() ?: return null
        val sec = parts[2].toDoubleOrNull() ?: return null
        return ((h * 60 + m) * 60 * 1000) + (sec * 1000).toLong()
    }

    private fun time(ms: Long): String = String.format(
        Locale.ROOT, "%02d:%02d:%02d,%03d", ms / 3_600_000, ms / 60_000 % 60, ms / 1000 % 60, ms % 1000,
    )

    /** Java charset for an mpv sub-codepage value. */
    fun charsetFor(codepage: String): Charset = when (codepage.lowercase()) {
        "auto", "utf8", "utf-8", "" -> Charsets.UTF_8
        else -> runCatching { Charset.forName(codepage.removePrefix("cp").let { if (it.all(Char::isDigit)) "windows-$it" else codepage }) }
            .getOrDefault(Charsets.UTF_8)
    }
}
