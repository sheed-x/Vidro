package app.vidro.ui.components

import android.content.Context
import android.text.format.Formatter
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** "1:02:03" / "4:05". Western digits on purpose: timecodes read the same in every language. */
fun formatDuration(ms: Long): String {
    if (ms <= 0) return ""
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.ROOT, "%d:%02d", m, s)
}

// Android puts a (narrow) no-break space before the unit; our font lacks it, and the fallback glyph
// shifted the baseline in large text. A plain space renders consistently.
fun formatSize(context: Context, bytes: Long): String =
    Formatter.formatShortFileSize(context, bytes).replace(' ', ' ').replace(' ', ' ')

fun formatDate(epochSeconds: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochSeconds * 1000))

/** Common name for a frame size, by its shorter side so portrait videos label correctly. */
fun resolutionLabel(width: Int, height: Int): String? {
    val side = minOf(width, height)
    return when {
        side <= 0 -> null
        side >= 2160 -> "4K"
        side >= 1440 -> "1440p"
        side >= 1080 -> "1080p"
        side >= 720 -> "720p"
        side >= 480 -> "480p"
        else -> "${side}p"
    }
}
