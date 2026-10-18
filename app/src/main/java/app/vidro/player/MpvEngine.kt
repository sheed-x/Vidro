package app.vidro.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Surface
import dev.jdtech.mpv.MPVLib
import dev.jdtech.mpv.MPVLib.MpvEvent
import dev.jdtech.mpv.MPVLib.MpvFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale
import kotlin.math.log2

enum class TrackType { Video, Audio, Subtitle }

data class Track(
    val id: Int,
    val type: TrackType,
    val title: String?,
    val lang: String?,
    val codec: String?,
    val channels: Int,
    val external: Boolean,
    /** Path of an external subtitle/audio file, when mpv knows it. */
    val externalFile: String?,
)

data class Chapter(val title: String?, val timeSec: Double)

data class PlayerState(
    val positionSec: Double = 0.0,
    val durationSec: Double = 0.0,
    val paused: Boolean = false,
    val eof: Boolean = false,
    val loaded: Boolean = false,
    val buffering: Boolean = false,
    /** Decoder actually in use, e.g. "mediacodec-copy"; empty or "no" means software. */
    val hwdecCurrent: String = "",
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val rotation: Int = 0,
    val tracks: List<Track> = emptyList(),
    val chapters: List<Chapter> = emptyList(),
    /** Selected track ids; null = off. */
    val audioId: Int? = null,
    val subtitleId: Int? = null,
    val secondarySubtitleId: Int? = null,
    val speed: Double = 1.0,
    /** mpv's own volume, 0..200 (above 100 is software boost). */
    val volume: Double = 100.0,
    val audioDelaySec: Double = 0.0,
    val subDelaySec: Double = 0.0,
    /** False until mpv reports the first real position of the current file. */
    val positionKnown: Boolean = false,
) {
    val isHardware: Boolean get() = hwdecCurrent.isNotEmpty() && hwdecCurrent != "no"

    /** Display aspect after the file's own rotation flag (0/90/180/270). */
    val isPortraitVideo: Boolean
        get() {
            if (videoWidth <= 0 || videoHeight <= 0) return false
            val rotated = rotation % 180 != 0
            val w = if (rotated) videoHeight else videoWidth
            val h = if (rotated) videoWidth else videoHeight
            return h > w
        }

    /** Width / height as displayed, for the picture-in-picture window. */
    val displayAspect: Float
        get() {
            if (videoWidth <= 0 || videoHeight <= 0) return 16f / 9f
            val rotated = rotation % 180 != 0
            return if (rotated) videoHeight.toFloat() / videoWidth else videoWidth.toFloat() / videoHeight
        }

    fun tracksOf(type: TrackType) = tracks.filter { it.type == type }
}

/** Decoder choice offered in the player. mpv falls back to software by itself if hardware can't handle a codec. */
enum class DecoderMode(val mpvValue: String) {
    Hardware("mediacodec,mediacodec-copy"),
    Software("no"),
}

/** How the picture fills the screen. [Custom] uses the ratio typed by the user. */
enum class AspectMode { Fit, Crop, Stretch, Ratio16x9, Ratio4x3, Original, Custom }

/** Side-by-side / top-bottom 3D files shown as a normal 2D picture (one eye). */
enum class StereoMode { Off, SideBySide, TopBottom }

/**
 * Thin owner of one libmpv instance. All mpv callbacks arrive on mpv's own thread and are
 * forwarded to [state] on the main thread.
 */
class MpvEngine(context: Context, fontsDir: String, defaultFont: String, subCodepage: String, subLanguages: String) :
    MPVLib.EventObserver {

    private val mpv: MPVLib = checkNotNull(MPVLib.create(context.applicationContext)) { "libmpv failed to initialise" }
    private val main = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    /** File opened and ready (seek targets and tracks are now valid). */
    var onFileLoaded: (() -> Unit)? = null

    /** mpv gave up on a file before it ever loaded. */
    var onLoadFailed: (() -> Unit)? = null

    /** Playback reached the end of the file (keep-open holds the last frame). */
    var onEndReached: (() -> Unit)? = null

    private var loadingFile = false
    private var fileStarted = false
    private var released = false

    init {
        with(mpv) {
            setOptionString("config", "no")
            // Cheap scalers and no extra GPU passes: the quality-oriented defaults dropped frames
            // when downscaling 4K, especially in the small picture-in-picture window.
            setOptionString("profile", "fast")
            // No surface yet: a file loaded before the SurfaceView exists would fail to open a GPU
            // output and mpv would drop the video track for good. attachSurface switches to gpu.
            setOptionString("vo", "null")
            setOptionString("gpu-context", "android")
            setOptionString("opengl-es", "yes")
            setOptionString("hwdec", DecoderMode.Hardware.mpvValue)
            setOptionString("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")
            setOptionString("ao", "audiotrack,opensles")
            setOptionString("volume-max", "200")
            setOptionString("keep-open", "yes")
            setOptionString("save-position-on-quit", "no")
            setOptionString("input-default-bindings", "no")
            setOptionString("osd-level", "0")
            setOptionString("force-window", "no")
            setOptionString("idle", "yes")
            // Offline player: never reach for the network, even for embedded links.
            setOptionString("ytdl", "no")
            setOptionString("load-scripts", "no")
            setOptionString("demuxer-max-bytes", "64MiB")
            setOptionString("demuxer-max-back-bytes", "64MiB")
            // Skip non-reference frames while catching up after a seek, so jumps land faster.
            setOptionString("hr-seek-framedrop", "yes")
            // Same-name subtitle and audio files next to the video load automatically.
            setOptionString("sub-auto", "fuzzy")
            setOptionString("audio-file-auto", "fuzzy")
            setOptionString("sub-file-paths", "Subs:subs:Subtitles:subtitles:Sub:sub")
            setOptionString("sub-codepage", subCodepage)
            if (subLanguages.isNotEmpty()) setOptionString("slang", subLanguages)
            // libass renders with the fonts we prepared (system Latin + Arabic, plus user fonts).
            setOptionString("sub-fonts-dir", fontsDir)
            setOptionString("osd-fonts-dir", fontsDir)
            setOptionString("sub-font", defaultFont)
            setOptionString("sub-scale-with-window", "yes")
            setOptionString("sub-use-margins", "no")
            init()
            addObserver(this@MpvEngine)
            observeProperty("time-pos", MpvFormat.MPV_FORMAT_DOUBLE)
            observeProperty("duration", MpvFormat.MPV_FORMAT_DOUBLE)
            observeProperty("pause", MpvFormat.MPV_FORMAT_FLAG)
            observeProperty("eof-reached", MpvFormat.MPV_FORMAT_FLAG)
            observeProperty("paused-for-cache", MpvFormat.MPV_FORMAT_FLAG)
            observeProperty("hwdec-current", MpvFormat.MPV_FORMAT_STRING)
            observeProperty("video-params/w", MpvFormat.MPV_FORMAT_INT64)
            observeProperty("video-params/h", MpvFormat.MPV_FORMAT_INT64)
            observeProperty("video-params/rotate", MpvFormat.MPV_FORMAT_INT64)
            observeProperty("track-list/count", MpvFormat.MPV_FORMAT_INT64)
            observeProperty("chapter-list/count", MpvFormat.MPV_FORMAT_INT64)
            observeProperty("aid", MpvFormat.MPV_FORMAT_STRING)
            observeProperty("sid", MpvFormat.MPV_FORMAT_STRING)
            observeProperty("secondary-sid", MpvFormat.MPV_FORMAT_STRING)
            observeProperty("speed", MpvFormat.MPV_FORMAT_DOUBLE)
            observeProperty("volume", MpvFormat.MPV_FORMAT_DOUBLE)
            observeProperty("audio-delay", MpvFormat.MPV_FORMAT_DOUBLE)
            observeProperty("sub-delay", MpvFormat.MPV_FORMAT_DOUBLE)
        }
    }

    // --- Surface ---

    fun attachSurface(surface: Surface) {
        if (released) return
        mpv.attachSurface(surface)
        mpv.setOptionString("force-window", "yes")
        mpv.setPropertyString("vo", "gpu")
        // A video output that failed earlier leaves the picture deselected; bring it back.
        if (property("vid") == "no") mpv.setPropertyString("vid", "auto")
    }

    fun resizeSurface(width: Int, height: Int) {
        if (released) return
        mpv.setPropertyString("android-surface-size", "${width}x$height")
    }

    fun detachSurface() {
        if (released) return
        mpv.setPropertyString("vo", "null")
        mpv.setOptionString("force-window", "no")
        mpv.detachSurface()
    }

    // --- Playback ---

    /** Opens [target] (a path or an fd:// URL), starting at [startSec] when given. */
    fun load(target: String, startSec: Double?, startPaused: Boolean = false) {
        if (released) return
        _state.update { PlayerState(speed = it.speed, volume = it.volume) }
        mpv.setPropertyString("start", startSec?.let(::fmt) ?: "none")
        loadingFile = true
        mpv.command(arrayOf("loadfile", target, "replace"))
        mpv.setPropertyBoolean("pause", startPaused)
    }

    fun setPaused(paused: Boolean) {
        if (!released) mpv.setPropertyBoolean("pause", paused)
    }

    fun togglePause() = setPaused(!_state.value.paused)

    /** [precise] = exact frame (on release); otherwise the nearest keyframe for fast scrubbing. */
    fun seekTo(seconds: Double, precise: Boolean = true) {
        if (released) return
        pendingScrub = null
        val mode = if (precise) "absolute+exact" else "absolute+keyframes"
        mpv.command(arrayOf("seek", fmt(seconds.coerceAtLeast(0.0)), mode))
    }

    /**
     * Quick ±N second jumps land on the nearest keyframe: exact seeking has to decode every frame
     * from the previous keyframe and made double-tap jumps stutter.
     */
    fun seekBy(seconds: Double) {
        if (!released) mpv.command(arrayOf("seek", fmt(seconds), "relative+keyframes"))
    }

    private var seekInFlight = false
    private var seekSentAt = 0L
    private var pendingScrub: Double? = null

    /**
     * Seeking while the finger drags the timeline. Only one seek is in flight at a time; the latest
     * position waits until mpv reports the previous one done. Sending every drag event queued up
     * dozens of seeks and made scrubbing jerky.
     */
    fun scrubTo(seconds: Double) {
        if (released) return
        val now = System.currentTimeMillis()
        if (seekInFlight && now - seekSentAt < 400) {
            pendingScrub = seconds
            return
        }
        pendingScrub = null
        seekInFlight = true
        seekSentAt = now
        mpv.command(arrayOf("seek", fmt(seconds.coerceAtLeast(0.0)), "absolute+keyframes"))
    }

    fun setSpeed(speed: Double) {
        if (released) return
        val value = speed.coerceIn(0.25, 4.0)
        // Faster than real time (e.g. hold-for-2x on 4K60 = 120 decoded fps) the decoder can't keep
        // up; letting it skip late frames keeps motion smooth instead of stuttering.
        mpv.setPropertyString("framedrop", if (value > 1.25) "decoder+vo" else "vo")
        mpv.setPropertyDouble("speed", value)
    }

    fun setVolume(volume: Double) {
        if (!released) mpv.setPropertyDouble("volume", volume.coerceIn(0.0, 200.0))
    }

    fun setDecoder(mode: DecoderMode) {
        // Changing hwdec live makes mpv reinitialise only the decoder; playback continues in place.
        if (!released) mpv.setPropertyString("hwdec", mode.mpvValue)
    }

    // --- Picture ---

    fun setAspect(mode: AspectMode, custom: String) {
        if (released) return
        mpv.setPropertyString("video-unscaled", if (mode == AspectMode.Original) "yes" else "no")
        mpv.setPropertyString("keepaspect", if (mode == AspectMode.Stretch) "no" else "yes")
        mpv.setPropertyDouble("panscan", if (mode == AspectMode.Crop) 1.0 else 0.0)
        mpv.setPropertyString(
            "video-aspect-override",
            when (mode) {
                AspectMode.Ratio16x9 -> "16:9"
                AspectMode.Ratio4x3 -> "4:3"
                AspectMode.Custom -> custom
                else -> "no"
            },
        )
    }

    /** [scale] 1 = no zoom; pan values are fractions of the picture size. */
    fun setZoom(scale: Float, panX: Float, panY: Float) {
        if (released) return
        mpv.setPropertyDouble("video-zoom", log2(scale.toDouble()))
        mpv.setPropertyDouble("video-pan-x", panX.toDouble())
        mpv.setPropertyDouble("video-pan-y", panY.toDouble())
    }

    /** Mirror and 3D handling, applied as one video filter chain. */
    fun setVideoTransform(flipH: Boolean, flipV: Boolean, stereo: StereoMode) {
        if (released) return
        // FFmpeg filters must go through mpv's lavfi wrapper; mpv has no native hflip/vflip.
        val filters = buildList {
            when (stereo) {
                StereoMode.SideBySide -> add("stereo3d=sbsl:ml")
                StereoMode.TopBottom -> add("stereo3d=abl:ml")
                StereoMode.Off -> Unit
            }
            if (flipH) add("hflip")
            if (flipV) add("vflip")
        }
        mpv.setPropertyString("vf", if (filters.isEmpty()) "" else "lavfi=[${filters.joinToString(",")}]")
    }

    // --- Tracks ---

    fun selectAudio(id: Int?) = setTrack("aid", id)
    fun selectSubtitle(id: Int?) = setTrack("sid", id)
    fun selectSecondarySubtitle(id: Int?) = setTrack("secondary-sid", id)

    fun addSubtitle(path: String) {
        if (!released) mpv.command(arrayOf("sub-add", path, "select"))
    }

    fun addAudio(path: String) {
        if (!released) mpv.command(arrayOf("audio-add", path, "select"))
    }

    fun setAudioDelay(seconds: Double) {
        if (!released) mpv.setPropertyDouble("audio-delay", seconds)
    }

    fun setSubDelay(seconds: Double) {
        if (!released) mpv.setPropertyDouble("sub-delay", seconds)
    }

    /** Jumps the video to the next (1) or previous (-1) subtitle line. */
    fun seekSubtitleLine(direction: Int) {
        if (!released) mpv.command(arrayOf("sub-seek", direction.toString()))
    }

    /** Re-reads text subtitles with a different character set (fixes garbled Arabic, etc.). */
    fun setSubCodepage(codepage: String) {
        if (released) return
        mpv.setPropertyString("sub-codepage", codepage)
        mpv.command(arrayOf("sub-reload"))
    }

    fun applySubtitleStyle(style: SubtitleStyle) {
        if (released) return
        with(mpv) {
            setPropertyString("sub-font", style.font)
            setPropertyDouble("sub-scale", style.scale.toDouble())
            setPropertyString("sub-color", argb(style.textColor))
            setPropertyString("sub-border-color", argb(style.borderColor))
            setPropertyDouble("sub-border-size", style.borderSize.toDouble())
            setPropertyDouble("sub-shadow-offset", if (style.shadow) 1.5 else 0.0)
            setPropertyString("sub-shadow-color", "#99000000")
            setPropertyString("sub-back-color", argb(if (style.background) style.backgroundColor else 0x00000000))
            setPropertyString("sub-border-style", if (style.background) "background-box" else "outline-and-shadow")
            setPropertyInt("sub-pos", style.position)
            setPropertyBoolean("sub-bold", style.bold)
            setPropertyBoolean("sub-italic", style.italic)
            setPropertyString("sub-ass-override", if (style.overrideAss) "force" else "yes")
        }
    }

    // --- Audio ---

    /** Equaliser and effects as one lavfi chain; empty = no processing. */
    fun setAudioFilters(chain: String) {
        if (!released) mpv.setPropertyString("af", chain)
    }

    /** Sends AC3/DTS bitstreams untouched to an external receiver (HDMI / optical). */
    fun setPassthrough(enabled: Boolean) {
        if (!released) mpv.setPropertyString("audio-spdif", if (enabled) "ac3,eac3,dts,dts-hd,truehd" else "")
    }

    /** Raw property read for the info panel. */
    fun property(name: String): String? = if (released) null else runCatching { mpv.getPropertyString(name) }.getOrNull()

    fun release() {
        if (released) return
        released = true
        mpv.removeObserver(this)
        mpv.destroy()
    }

    private fun setTrack(property: String, id: Int?) {
        if (!released) mpv.setPropertyString(property, id?.toString() ?: "no")
    }

    private fun readTracks(): List<Track> {
        val count = mpv.getPropertyInt("track-list/count") ?: return emptyList()
        return (0 until count).mapNotNull { i ->
            val p = "track-list/$i"
            val type = when (mpv.getPropertyString("$p/type")) {
                "video" -> TrackType.Video
                "audio" -> TrackType.Audio
                "sub" -> TrackType.Subtitle
                else -> return@mapNotNull null
            }
            Track(
                id = mpv.getPropertyInt("$p/id") ?: return@mapNotNull null,
                type = type,
                title = mpv.getPropertyString("$p/title"),
                lang = mpv.getPropertyString("$p/lang"),
                codec = mpv.getPropertyString("$p/codec"),
                channels = mpv.getPropertyInt("$p/demux-channel-count") ?: 0,
                external = mpv.getPropertyBoolean("$p/external") == true,
                externalFile = mpv.getPropertyString("$p/external-filename"),
            )
        }
    }

    private fun readChapters(): List<Chapter> {
        val count = mpv.getPropertyInt("chapter-list/count") ?: return emptyList()
        return (0 until count).map { i ->
            Chapter(
                title = mpv.getPropertyString("chapter-list/$i/title"),
                timeSec = mpv.getPropertyDouble("chapter-list/$i/time") ?: 0.0,
            )
        }
    }

    // --- MPVLib.EventObserver (mpv thread) ---

    override fun eventProperty(property: String) {}

    override fun eventProperty(property: String, value: Long) {
        when (property) {
            "track-list/count" -> {
                val tracks = readTracks()
                post { copy(tracks = tracks) }
            }
            "chapter-list/count" -> {
                val chapters = readChapters()
                post { copy(chapters = chapters) }
            }
            else -> post {
                when (property) {
                    "video-params/w" -> copy(videoWidth = value.toInt())
                    "video-params/h" -> copy(videoHeight = value.toInt())
                    "video-params/rotate" -> copy(rotation = value.toInt())
                    else -> this
                }
            }
        }
    }

    override fun eventProperty(property: String, value: Double) {
        post {
            when (property) {
                "time-pos" -> copy(positionSec = value, positionKnown = true)
                "duration" -> copy(durationSec = value)
                "speed" -> copy(speed = value)
                "volume" -> copy(volume = value)
                "audio-delay" -> copy(audioDelaySec = value)
                "sub-delay" -> copy(subDelaySec = value)
                else -> this
            }
        }
    }

    override fun eventProperty(property: String, value: Boolean) {
        if (property == "eof-reached" && value) main.post { if (!released) onEndReached?.invoke() }
        post {
            when (property) {
                "pause" -> copy(paused = value)
                "eof-reached" -> copy(eof = value)
                "paused-for-cache" -> copy(buffering = value)
                else -> this
            }
        }
    }

    override fun eventProperty(property: String, value: String) {
        post {
            when (property) {
                "hwdec-current" -> copy(hwdecCurrent = value)
                "aid" -> copy(audioId = value.toIntOrNull())
                "sid" -> copy(subtitleId = value.toIntOrNull())
                "secondary-sid" -> copy(secondarySubtitleId = value.toIntOrNull())
                else -> this
            }
        }
    }

    override fun event(eventId: Int) {
        // Replacing a file emits END_FILE for the old one before START_FILE for the new one, so a
        // failure only counts once the requested file has actually started.
        when (eventId) {
            MpvEvent.MPV_EVENT_PLAYBACK_RESTART -> main.post {
                seekInFlight = false
                pendingScrub?.let { scrubTo(it) }
            }
            MpvEvent.MPV_EVENT_START_FILE -> main.post {
                if (loadingFile) fileStarted = true
            }
            MpvEvent.MPV_EVENT_FILE_LOADED -> main.post {
                if (released) return@post
                loadingFile = false
                fileStarted = false
                _state.update { it.copy(loaded = true, tracks = readTracks(), chapters = readChapters()) }
                onFileLoaded?.invoke()
            }
            // The decoder is (re)chosen here, including silent fallbacks from hardware to software,
            // which the property observer doesn't always report; read the truth directly.
            MpvEvent.MPV_EVENT_VIDEO_RECONFIG -> main.post {
                if (released) return@post
                val current = mpv.getPropertyString("hwdec-current").orEmpty()
                _state.update { it.copy(hwdecCurrent = current) }
            }
            MpvEvent.MPV_EVENT_END_FILE -> main.post {
                if (released || !fileStarted) return@post
                loadingFile = false
                fileStarted = false
                onLoadFailed?.invoke()
            }
        }
    }

    private inline fun post(crossinline change: PlayerState.() -> PlayerState) {
        main.post { if (!released) _state.update { it.change() } }
    }

    private fun fmt(value: Double) = String.format(Locale.ROOT, "%.3f", value)

    private fun argb(color: Long) = String.format(Locale.ROOT, "#%08X", color and 0xFFFFFFFFL)
}
