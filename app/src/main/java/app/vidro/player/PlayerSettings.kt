package app.vidro.player

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale

enum class ResumeMode { Auto, Ask, Never }

@Serializable
data class SubtitleStyle(
    /** Font family name as libass sees it (see [SubtitleFonts]). */
    val font: String = SubtitleFonts.DEFAULT_FAMILY,
    /** 1 = mpv's default size. */
    val scale: Float = 1f,
    val textColor: Long = 0xFFFFFFFF,
    val borderColor: Long = 0xFF000000,
    val borderSize: Float = 3f,
    val shadow: Boolean = true,
    val background: Boolean = false,
    val backgroundColor: Long = 0x99000000,
    /** mpv sub-pos: 100 = bottom edge, lower values move up. */
    val position: Int = 100,
    val bold: Boolean = false,
    val italic: Boolean = false,
    /** Force our look on styled (ASS) subtitles instead of keeping their own. */
    val overrideAss: Boolean = false,
)

enum class Reverb { None, Room, Hall, Stage }

/** Equaliser + effects, rendered by FFmpeg filters inside mpv. */
@Serializable
data class AudioEffects(
    val enabled: Boolean = true,
    /** Gain in dB for each of [EQ_BANDS]. */
    val bands: List<Float> = List(EQ_BANDS.size) { 0f },
    val preset: String = "flat",
    val bass: Float = 0f,
    val treble: Float = 0f,
    /** 0..1 stereo widening. */
    val virtualizer: Float = 0f,
    val reverb: Reverb = Reverb.None,
    /** Evens out loud and quiet scenes — handy at night. */
    val normalize: Boolean = false,
) {
    val isNeutral: Boolean
        get() = bands.all { it == 0f } && bass == 0f && treble == 0f && virtualizer == 0f &&
            reverb == Reverb.None && !normalize

    fun toFilterChain(): String {
        if (!enabled || isNeutral) return ""
        val parts = mutableListOf<String>()
        if (virtualizer > 0f) {
            parts += "aformat=channel_layouts=stereo"
            parts += "stereowiden=delay=20:feedback=${f(0.1f + virtualizer * 0.5f)}:crossfeed=${f(virtualizer * 0.4f)}:drymix=0.8"
        }
        bands.forEachIndexed { i, gain ->
            if (gain != 0f) parts += "equalizer=f=${EQ_BANDS[i]}:t=o:w=1:g=${f(gain)}"
        }
        if (bass != 0f) parts += "bass=g=${f(bass)}"
        if (treble != 0f) parts += "treble=g=${f(treble)}"
        when (reverb) {
            Reverb.Room -> parts += "aecho=0.8:0.85:30|45:0.18|0.12"
            Reverb.Hall -> parts += "aecho=0.8:0.88:60|110:0.25|0.18"
            Reverb.Stage -> parts += "aecho=0.8:0.9:90|160|230:0.22|0.16|0.1"
            Reverb.None -> Unit
        }
        if (normalize) parts += "dynaudnorm=f=250:g=15"
        // Boosting bands can clip; a limiter keeps it clean.
        parts += "alimiter=limit=0.95"
        return "lavfi=[${parts.joinToString(",")}]"
    }

    companion object {
        val EQ_BANDS = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

        /** Ready-made curves (dB per band) plus the one-tap Cinematic / Clarity / Music effects. */
        val PRESETS: Map<String, AudioEffects> = mapOf(
            "flat" to AudioEffects(),
            "bass" to AudioEffects(preset = "bass", bands = listOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f)),
            "rock" to AudioEffects(preset = "rock", bands = listOf(4f, 3f, 2f, 0f, -1f, -1f, 1f, 2f, 3f, 4f)),
            "pop" to AudioEffects(preset = "pop", bands = listOf(-1f, 1f, 3f, 4f, 3f, 0f, -1f, -1f, 1f, 2f)),
            "jazz" to AudioEffects(preset = "jazz", bands = listOf(3f, 2f, 1f, 2f, -1f, -1f, 0f, 1f, 2f, 3f)),
            "classical" to AudioEffects(preset = "classical", bands = listOf(4f, 3f, 2f, 1f, 0f, 0f, 0f, 1f, 2f, 3f)),
            "vocal" to AudioEffects(preset = "vocal", bands = listOf(-2f, -2f, -1f, 1f, 3f, 4f, 4f, 2f, 0f, -1f)),
            "cinematic" to AudioEffects(
                preset = "cinematic",
                bands = listOf(4f, 3f, 1f, 0f, 0f, 0f, 1f, 2f, 2f, 1f),
                virtualizer = 0.5f,
                reverb = Reverb.Room,
            ),
            "clarity" to AudioEffects(
                preset = "clarity",
                bands = listOf(-3f, -2f, -1f, 0f, 2f, 3f, 4f, 3f, 1f, 0f),
                normalize = true,
            ),
            "music" to AudioEffects(preset = "music", bands = listOf(3f, 2f, 1f, 0f, 0f, 0f, 0f, 1f, 2f, 3f), virtualizer = 0.3f),
            "night" to AudioEffects(preset = "night", normalize = true),
        )

        private fun f(value: Float) = String.format(Locale.ROOT, "%.2f", value)
    }
}

/** Everything the user can tune in the player; persisted as JSON in its own DataStore. */
@Serializable
data class PlayerSettings(
    val doubleTapSeconds: Int = 10,
    val gestureSeek: Boolean = true,
    val gestureBrightness: Boolean = true,
    val gestureVolume: Boolean = true,
    val gestureZoom: Boolean = true,
    val gestureDoubleTap: Boolean = true,
    val gestureLongPress: Boolean = true,
    val longPressSpeed: Float = 2f,
    val resumeMode: ResumeMode = ResumeMode.Auto,
    val autoPlayNext: Boolean = true,
    val showClock: Boolean = true,
    val volumeBoost: Boolean = true,
    val backgroundPlay: Boolean = false,
    val autoPip: Boolean = true,
    val subtitleStyle: SubtitleStyle = SubtitleStyle(),
    /** mpv sub-codepage value: "auto", "utf8", "cp1256"… */
    val subCodepage: String = "auto",
    /** Preferred subtitle language (ISO codes for mpv's slang), empty = no preference. */
    val subLanguage: String = "",
    val effects: AudioEffects = AudioEffects(),
    val passthrough: Boolean = false,
)

private val Context.playerStore by preferencesDataStore(name = "player")

class PlayerSettingsRepository(context: Context) {
    private val store = context.playerStore
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val key = stringPreferencesKey("settings")

    val settings: Flow<PlayerSettings> = store.data.map { prefs ->
        prefs[key]?.let { runCatching { json.decodeFromString<PlayerSettings>(it) }.getOrNull() } ?: PlayerSettings()
    }

    suspend fun update(transform: (PlayerSettings) -> PlayerSettings) {
        store.edit { prefs ->
            val current = prefs[key]?.let { runCatching { json.decodeFromString<PlayerSettings>(it) }.getOrNull() }
                ?: PlayerSettings()
            prefs[key] = json.encodeToString(PlayerSettings.serializer(), transform(current))
        }
    }
}
