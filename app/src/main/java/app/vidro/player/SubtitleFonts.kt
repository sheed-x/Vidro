package app.vidro.player

import android.content.Context
import android.net.Uri
import app.vidro.R
import java.io.File

/**
 * libass (mpv's subtitle renderer) has no access to Android's font system, so we give it a folder
 * of fonts: the system's Latin and Arabic families, our UI typeface, and fonts the user imports.
 * Missing glyphs fall back across the fonts in this folder, so mixed Arabic/English lines render.
 */
object SubtitleFonts {
    const val DEFAULT_FAMILY = "Roboto"
    const val FLEX_FAMILY = "Google Sans Flex"
    const val NASKH_FAMILY = "Noto Naskh Arabic"

    private val systemCandidates = listOf(
        "Roboto-Regular.ttf",
        "Roboto-Bold.ttf",
        "NotoNaskhArabic-Regular.ttf",
        "NotoNaskhArabic-Bold.ttf",
        "NotoNaskhArabicUI-Regular.ttf",
        "NotoSansArabic-Regular.ttf",
        "NotoSansArabicUI-Regular.ttf",
        "NotoSansHebrew-Regular.ttf",
        "DroidSansFallback.ttf",
    )

    fun directory(context: Context): File = File(context.filesDir, "fonts").apply { mkdirs() }

    /** Copies missing fonts into [directory]; cheap after the first run. */
    fun prepare(context: Context): File {
        val dir = directory(context)
        for (name in systemCandidates) {
            val source = File("/system/fonts", name)
            val target = File(dir, name)
            if (source.canRead() && (!target.exists() || target.length() != source.length())) {
                runCatching { source.copyTo(target, overwrite = true) }
            }
        }
        val flex = File(dir, "GoogleSansFlex.ttf")
        if (!flex.exists()) {
            runCatching {
                context.resources.openRawResource(R.font.google_sans_flex).use { input ->
                    flex.outputStream().use { input.copyTo(it) }
                }
            }
        }
        return dir
    }

    /** Imports a user-picked .ttf/.otf; returns its family name, or null if it isn't a usable font. */
    fun import(context: Context, uri: Uri, displayName: String): String? = runCatching {
        val safe = displayName.replace(Regex("[^\\w.\\- ]"), "_")
        val target = File(directory(context), safe)
        context.contentResolver.openInputStream(uri)!!.use { input -> target.outputStream().use { input.copyTo(it) } }
        familyName(target) ?: run { target.delete(); null }
    }.getOrNull()

    /** Family names of fonts the user imported earlier. */
    fun userFonts(context: Context): List<String> {
        val builtIn = systemCandidates.toSet() + "GoogleSansFlex.ttf"
        return directory(context).listFiles().orEmpty()
            .filter { it.name !in builtIn && it.extension.lowercase() in setOf("ttf", "otf") }
            .mapNotNull { familyName(it) }
            .distinct()
            .sorted()
    }

    /**
     * Reads the family name (nameID 1) from a TrueType/OpenType 'name' table — the name libass
     * matches on, which is often different from the file name.
     */
    fun familyName(file: File): String? = runCatching {
        val bytes = file.readBytes()
        fun u16(at: Int) = ((bytes[at].toInt() and 0xFF) shl 8) or (bytes[at + 1].toInt() and 0xFF)
        fun u32(at: Int) = (u16(at).toLong() shl 16) or u16(at + 2).toLong()
        val tables = u16(4)
        var nameOffset = -1
        for (i in 0 until tables) {
            val rec = 12 + i * 16
            if (String(bytes, rec, 4, Charsets.US_ASCII) == "name") nameOffset = u32(rec + 8).toInt()
        }
        if (nameOffset < 0) return@runCatching null
        val count = u16(nameOffset + 2)
        val strings = nameOffset + u16(nameOffset + 4)
        var fallback: String? = null
        for (i in 0 until count) {
            val rec = nameOffset + 6 + i * 12
            val platform = u16(rec)
            val nameId = u16(rec + 6)
            if (nameId != 1) continue
            val length = u16(rec + 8)
            val start = strings + u16(rec + 10)
            when (platform) {
                3, 0 -> return@runCatching String(bytes, start, length, Charsets.UTF_16BE)
                1 -> fallback = String(bytes, start, length, Charsets.ISO_8859_1)
            }
        }
        fallback
    }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
}
