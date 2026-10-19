package app.vidro.player

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import android.view.SurfaceView
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.content.ContextCompat
import app.vidro.data.Bookmark
import app.vidro.data.PlaybackEntry
import app.vidro.data.PlaybackHistory
import app.vidro.VidroApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.random.Random

/** One entry of the play queue: a file path, or a content:// URI handed over by another app. */
data class PlayItem(
    val target: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    /** A song or other audio file; it always plays in the music screen. */
    val isAudio: Boolean = false,
    val durationMs: Long = 0,
) {
    val isContent: Boolean get() = target.startsWith("content://")
}

/** Rotation button cycle. [FollowVideo] picks landscape or portrait from the video's own shape. */
enum class RotationMode { FollowVideo, Landscape, Portrait }

enum class RepeatMode { Off, One, All }

/**
 * Everything between the screen and the engine: the play queue, resume points, repeat/shuffle,
 * sleep timer, bookmarks, effects, orientation, audio focus and the media session used by
 * headset buttons, the notification and picture-in-picture.
 *
 * Lives in [PlayerHolder] rather than in the activity, so background playback survives the
 * activity being closed or recreated.
 */
class PlayerController(
    private val app: Context,
    private val history: PlaybackHistory,
    private val settingsRepository: PlayerSettingsRepository,
    initialSettings: PlayerSettings,
    initialItems: List<PlayItem>,
    startIndex: Int,
    /** Start a video as sound only (the music screen instead of the video player). */
    startAudioOnly: Boolean = false,
) {
    private val fontsDir: File = SubtitleFonts.prepare(app)

    val engine = MpvEngine(
        context = app,
        fontsDir = fontsDir.path,
        defaultFont = initialSettings.subtitleStyle.font,
        subCodepage = initialSettings.subCodepage,
        subLanguages = slangFor(initialSettings.subLanguage),
    )
    private val scope = MainScope()

    private val _settings = MutableStateFlow(initialSettings)
    val settings: StateFlow<PlayerSettings> = _settings.asStateFlow()

    private val _queue = MutableStateFlow(initialItems)

    /** The play queue; "Play next" and "Add to queue" grow it while playing. */
    val queue: StateFlow<List<PlayItem>> = _queue.asStateFlow()
    val items: List<PlayItem> get() = _queue.value

    private val _index = MutableStateFlow(startIndex.coerceIn(0, initialItems.lastIndex))
    val index: StateFlow<Int> = _index.asStateFlow()
    val currentItem: PlayItem get() = items[_index.value]

    /** Position (seconds) playback resumed from, while the "Start over" offer is showing. */
    private val _resumedFrom = MutableStateFlow<Double?>(null)
    val resumedFrom: StateFlow<Double?> = _resumedFrom.asStateFlow()

    /** In "Ask" resume mode: the saved position waiting for the user's answer (playback is paused). */
    private val _resumePrompt = MutableStateFlow<Double?>(null)
    val resumePrompt: StateFlow<Double?> = _resumePrompt.asStateFlow()

    private val _error = MutableStateFlow(false)
    val error: StateFlow<Boolean> = _error.asStateFlow()

    private val _decoder = MutableStateFlow(DecoderMode.Hardware)
    val decoder: StateFlow<DecoderMode> = _decoder.asStateFlow()

    private val _aspect = MutableStateFlow(AspectMode.Fit)
    val aspect: StateFlow<AspectMode> = _aspect.asStateFlow()
    private val _customAspect = MutableStateFlow("21:9")
    val customAspect: StateFlow<String> = _customAspect.asStateFlow()

    private val _rotation = MutableStateFlow(RotationMode.FollowVideo)
    val rotation: StateFlow<RotationMode> = _rotation.asStateFlow()

    private val _flipH = MutableStateFlow(false)
    val flipH: StateFlow<Boolean> = _flipH.asStateFlow()
    private val _flipV = MutableStateFlow(false)
    val flipV: StateFlow<Boolean> = _flipV.asStateFlow()
    private val _stereo = MutableStateFlow(StereoMode.Off)
    val stereo: StateFlow<StereoMode> = _stereo.asStateFlow()

    private val _repeat = MutableStateFlow(RepeatMode.Off)
    val repeat: StateFlow<RepeatMode> = _repeat.asStateFlow()
    private val _shuffle = MutableStateFlow(false)
    val shuffle: StateFlow<Boolean> = _shuffle.asStateFlow()

    /** Wall-clock time (ms) the sleep timer pauses playback, or null. */
    private val _sleepAt = MutableStateFlow<Long?>(null)
    val sleepAt: StateFlow<Long?> = _sleepAt.asStateFlow()
    private val _sleepAtEnd = MutableStateFlow(false)
    val sleepAtEnd: StateFlow<Boolean> = _sleepAtEnd.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

    /** Listening only: no picture is decoded, playback carries on with the screen off or locked. */
    private val _audioOnly = MutableStateFlow(startAudioOnly || initialItems.getOrNull(startIndex)?.isAudio == true)
    val audioOnly: StateFlow<Boolean> = _audioOnly.asStateFlow()

    /** Whether the video player screen is showing this session (the mini player hides then). */
    private val _screenAttached = MutableStateFlow(false)
    val screenAttached: StateFlow<Boolean> = _screenAttached.asStateFlow()

    /** Cover for the music screen, notification and lock screen: album art or a frame of the video. */
    private val _artwork = MutableStateFlow<Bitmap?>(null)
    val artwork: StateFlow<Bitmap?> = _artwork.asStateFlow()
    private var artworkJob: Job? = null

    /** Called when playback should end and the player close (queue finished). */
    var onQueueFinished: () -> Unit = { PlayerHolder.clear(this) }

    val hasNext: Boolean get() = _shuffle.value && items.size > 1 || _index.value < items.lastIndex || _repeat.value == RepeatMode.All
    val hasPrevious: Boolean get() = _index.value > 0 || playedStack.isNotEmpty()

    private val playedStack = ArrayDeque<Int>()
    private var activity: Activity? = null
    private var sleepJob: Job? = null
    private var released = false

    val session = MediaSessionCompat(app, "Vidro").apply {
        setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() = engine.setPaused(false)
            override fun onPause() = engine.setPaused(true)
            override fun onSkipToNext() = next()
            override fun onSkipToPrevious() = previous()
            override fun onSeekTo(pos: Long) = engine.seekTo(pos / 1000.0)
            override fun onFastForward() = engine.seekBy(_settings.value.doubleTapSeconds.toDouble())
            override fun onRewind() = engine.seekBy(-_settings.value.doubleTapSeconds.toDouble())
            override fun onStop() {
                engine.setPaused(true)
                onQueueFinished()
            }
        })
        isActive = true
    }

    private val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            engine.setPaused(true)
        }
    }

    // Pause when headphones are unplugged or a Bluetooth headset disconnects, like every media app.
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) engine.setPaused(true)
        }
    }

    init {
        ContextCompat.registerReceiver(
            app, noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        engine.onEndReached = ::handleEnd
        engine.onLoadFailed = { _error.value = true }
        engine.onFileLoaded = ::onFileLoaded
        applyGlobalSettings(initialSettings)
        engine.setVideoEnabled(!_audioOnly.value)

        scope.launch {
            settingsRepository.settings.drop(1).collect { new ->
                val old = _settings.value
                _settings.value = new
                if (new.subtitleStyle != old.subtitleStyle) engine.applySubtitleStyle(new.subtitleStyle)
                if (new.effects != old.effects) engine.setAudioFilters(new.effects.toFilterChain())
                if (new.passthrough != old.passthrough) engine.setPassthrough(new.passthrough)
                if (new.subCodepage != old.subCodepage) engine.setSubCodepage(new.subCodepage)
                if (new.backgroundPlay != old.backgroundPlay) updateService()
            }
        }
        // Checkpoint the position regularly so a crash or kill loses at most a few seconds.
        scope.launch {
            while (isActive) {
                delay(CHECKPOINT_MS)
                if (!engine.state.value.paused) saveProgress()
            }
        }
        // Re-apply orientation once the video's real size (and rotation flag) is known.
        scope.launch {
            engine.state.map { Triple(it.videoWidth > 0, it.isPortraitVideo, it.rotation) }
                .distinctUntilChanged()
                .collect { applyOrientation() }
        }
        // The notification / lock screen controls appear as soon as a file is open, not only after
        // the first pause or resume.
        scope.launch {
            engine.state.map { it.loaded }.distinctUntilChanged().collect { updateService() }
        }
        scope.launch {
            engine.state.map { it.paused }.distinctUntilChanged().collect { paused ->
                if (paused) saveProgress() else requestAudioFocus()
                updateService()
            }
        }
        // Keep the media session (notification, lock screen, Bluetooth) in step with playback.
        scope.launch {
            engine.state.map { listOf(it.paused, it.loaded, (it.positionSec / 5).toInt(), it.speed) }
                .distinctUntilChanged()
                .collect { publishSessionState() }
        }
        load(_index.value)
    }

    // --- Activity link ---

    fun attachActivity(activity: Activity) {
        this.activity = activity
        _screenAttached.value = true
        applyOrientation()
    }

    fun detachActivity(activity: Activity) {
        if (this.activity === activity) {
            this.activity = null
            _screenAttached.value = false
            onQueueFinished = { PlayerHolder.clear(this) }
        }
    }

    /** Leaving the player screen: keep playing if background play is on or we're only listening. */
    fun onScreenHidden() {
        saveProgress()
        if (!_settings.value.backgroundPlay && !_audioOnly.value) engine.setPaused(true)
    }

    /** Switches between watching and listening; songs are always listen-only. */
    fun setAudioOnly(enabled: Boolean) {
        val value = enabled || currentItem.isAudio
        if (_audioOnly.value == value) return
        _audioOnly.value = value
        engine.setVideoEnabled(!value)
        updateService()
    }

    // --- Queue ---

    fun load(position: Int) {
        if (engine.state.value.loaded) saveProgress()
        _index.value = position
        _error.value = false
        _resumedFrom.value = null
        _resumePrompt.value = null
        _bookmarks.value = emptyList()
        val item = items[position]
        loadArtwork(item)
        scope.launch {
            val entry = withContext(Dispatchers.IO) { runCatching { history.load(item.target) }.getOrNull() }
            // Songs start from the top; long recordings (podcasts, audiobooks) remember their place.
            val saved = entry?.resumePointSec()?.takeIf { !item.isAudio || item.durationMs >= LONG_AUDIO_MS }
            val mode = if (item.isAudio && _settings.value.resumeMode == ResumeMode.Ask) ResumeMode.Auto else _settings.value.resumeMode
            val start = if (mode == ResumeMode.Never) null else saved
            val target = withContext(Dispatchers.IO) { resolve(item) }
            if (target == null) {
                _error.value = true
                return@launch
            }
            pendingDelays = entry
            engine.load(target, start, startPaused = mode == ResumeMode.Ask && saved != null)
            withContext(Dispatchers.IO) { runCatching { history.recordPlay(item.target) } }
            when {
                start == null -> Unit
                mode == ResumeMode.Ask -> _resumePrompt.value = start
                else -> _resumedFrom.value = start
            }
            _bookmarks.value = withContext(Dispatchers.IO) { runCatching { history.bookmarks(item.target) }.getOrDefault(emptyList()) }
            publishMetadata()
        }
    }

    private var pendingDelays: PlaybackEntry? = null

    private fun onFileLoaded() {
        engine.setAspect(_aspect.value, _customAspect.value)
        engine.setVideoTransform(_flipH.value, _flipV.value, _stereo.value)
        pendingDelays?.let {
            if (it.audioDelayMs != 0L) engine.setAudioDelay(it.audioDelayMs / 1000.0)
            if (it.subDelayMs != 0L) engine.setSubDelay(it.subDelayMs / 1000.0)
        }
        pendingDelays = null
        publishMetadata()
    }

    fun next() {
        val target = nextIndex() ?: return
        playedStack.addLast(_index.value)
        load(target)
    }

    /** Like most players: restart the current video if we're past the first seconds, else go back one. */
    fun previous() {
        if (engine.state.value.positionSec > RESTART_THRESHOLD_SEC) {
            engine.seekTo(0.0)
            return
        }
        val target = playedStack.removeLastOrNull() ?: (_index.value - 1).takeIf { it >= 0 }
        if (target != null) load(target) else engine.seekTo(0.0)
    }

    fun playAt(position: Int) {
        if (position == _index.value) return
        playedStack.addLast(_index.value)
        load(position)
    }

    private fun nextIndex(): Int? = when {
        _shuffle.value && items.size > 1 -> {
            var pick: Int
            do pick = Random.nextInt(items.size) while (pick == _index.value)
            pick
        }
        _index.value < items.lastIndex -> _index.value + 1
        _repeat.value == RepeatMode.All -> 0
        else -> null
    }

    /** Puts [added] right after the current item. */
    fun playNext(added: List<PlayItem>) = insert(_index.value + 1, added)

    /** Appends [added] to the end of the queue. */
    fun addToQueue(added: List<PlayItem>) = insert(items.size, added)

    private fun insert(at: Int, added: List<PlayItem>) {
        if (added.isEmpty()) return
        _queue.value = items.toMutableList().apply { addAll(at, added) }
        if (_index.value >= at) _index.value += added.size
        shiftHistory(at, added.size)
    }

    /** Takes an upcoming or earlier item out of the queue; the one playing stays. */
    fun removeAt(position: Int) {
        if (position == _index.value || position !in items.indices) return
        _queue.value = items.toMutableList().apply { removeAt(position) }
        if (position < _index.value) _index.value -= 1
        playedStack.removeAll { it == position }
        shiftHistory(position + 1, -1)
    }

    private fun shiftHistory(from: Int, by: Int) {
        for (i in playedStack.indices) if (playedStack[i] >= from) playedStack[i] += by
    }

    fun cycleRepeat() {
        _repeat.value = RepeatMode.entries[(_repeat.value.ordinal + 1) % RepeatMode.entries.size]
    }

    fun toggleShuffle() {
        _shuffle.value = !_shuffle.value
    }

    // --- Resume ---

    fun startOver() {
        engine.seekTo(0.0)
        _resumedFrom.value = null
        if (_resumePrompt.value != null) {
            _resumePrompt.value = null
            engine.setPaused(false)
        }
    }

    fun acceptResume() {
        _resumePrompt.value = null
        engine.setPaused(false)
    }

    fun dismissResume() {
        _resumedFrom.value = null
    }

    // --- Picture ---

    fun setDecoder(mode: DecoderMode) {
        _decoder.value = mode
        engine.setDecoder(mode)
    }

    fun setAspect(mode: AspectMode) {
        _aspect.value = mode
        engine.setAspect(mode, _customAspect.value)
    }

    fun setCustomAspect(ratio: String) {
        _customAspect.value = ratio
        setAspect(AspectMode.Custom)
    }

    fun cycleRotation() {
        _rotation.value = RotationMode.entries[(_rotation.value.ordinal + 1) % RotationMode.entries.size]
        applyOrientation()
    }

    fun toggleFlipH() {
        _flipH.value = !_flipH.value
        engine.setVideoTransform(_flipH.value, _flipV.value, _stereo.value)
    }

    fun toggleFlipV() {
        _flipV.value = !_flipV.value
        engine.setVideoTransform(_flipH.value, _flipV.value, _stereo.value)
    }

    fun setStereo(mode: StereoMode) {
        _stereo.value = mode
        engine.setVideoTransform(_flipH.value, _flipV.value, mode)
    }

    /**
     * Saves the frame on screen to Pictures/Vidro by copying the video surface (works on every
     * Android version we support, independent of mpv's image encoders). Letterbox bars are cropped.
     */
    fun takeScreenshot(onDone: (Boolean) -> Unit) {
        val view = PlayerSurface.view?.get()
        if (view == null || view.width == 0 || view.height == 0) {
            onDone(false)
            return
        }
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        PixelCopy.request(view, bitmap, { result ->
            if (result != PixelCopy.SUCCESS) {
                onDone(false)
                return@request
            }
            scope.launch {
                val ok = withContext(Dispatchers.IO) { publishImage(cropToVideo(bitmap), "Vidro_${System.currentTimeMillis()}.jpg") }
                onDone(ok)
            }
        }, Handler(Looper.getMainLooper()))
    }

    private fun cropToVideo(full: Bitmap): Bitmap {
        if (_aspect.value != AspectMode.Fit) return full
        val aspect = engine.state.value.displayAspect
        val viewAspect = full.width.toFloat() / full.height
        return if (viewAspect > aspect) {
            val w = (full.height * aspect).toInt().coerceIn(1, full.width)
            Bitmap.createBitmap(full, (full.width - w) / 2, 0, w, full.height)
        } else {
            val h = (full.width / aspect).toInt().coerceIn(1, full.height)
            Bitmap.createBitmap(full, 0, (full.height - h) / 2, full.width, h)
        }
    }

    private fun publishImage(bitmap: Bitmap, name: String): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Vidro")
            }
            val uri = app.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            app.contentResolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Vidro").apply { mkdirs() }
            val target = File(dir, name)
            target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            @Suppress("DEPRECATION")
            app.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(target)))
        }
        true
    }.getOrDefault(false)

    // --- Sleep timer ---

    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        _sleepAtEnd.value = false
        _sleepAt.value = minutes?.let { System.currentTimeMillis() + it * 60_000L }
        if (minutes == null) return
        sleepJob = scope.launch {
            while (isActive) {
                val at = _sleepAt.value ?: break
                if (System.currentTimeMillis() >= at) {
                    engine.setPaused(true)
                    _sleepAt.value = null
                    break
                }
                delay(1000)
            }
        }
    }

    fun setSleepAtEndOfVideo() {
        sleepJob?.cancel()
        _sleepAt.value = null
        _sleepAtEnd.value = true
    }

    // --- Bookmarks ---

    fun addBookmark() {
        val key = currentItem.target
        val position = (engine.state.value.positionSec * 1000).toLong()
        scope.launch {
            _bookmarks.value = withContext(Dispatchers.IO) {
                history.addBookmark(key, position)
                history.bookmarks(key)
            }
        }
    }

    fun deleteBookmark(bookmark: Bookmark) {
        scope.launch {
            _bookmarks.value = withContext(Dispatchers.IO) {
                history.deleteBookmark(bookmark.id)
                history.bookmarks(bookmark.key)
            }
        }
    }

    // --- Subtitles & audio files ---

    /** Copies a picked file into the cache (mpv can't read content:// itself) and loads it. */
    fun addExternalFile(uri: Uri, displayName: String, subtitle: Boolean) {
        scope.launch {
            val path = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(app.cacheDir, "external").apply { mkdirs() }
                    val target = File(dir, displayName.replace(Regex("[\\\\/:*?\"<>|]"), "_"))
                    app.contentResolver.openInputStream(uri)!!.use { input -> target.outputStream().use { input.copyTo(it) } }
                    target.path
                }.getOrNull()
            } ?: return@launch
            if (subtitle) engine.addSubtitle(path) else engine.addAudio(path)
        }
    }

    /** Writes the selected external subtitle as SRT with the current delay; returns the file. */
    fun saveSubtitleAsSrt(onDone: (File?) -> Unit) {
        val state = engine.state.value
        val track = state.tracks.firstOrNull { it.id == state.subtitleId && it.type == TrackType.Subtitle }
        val source = track?.externalFile
        if (!SubtitleConverter.canConvert(source)) {
            onDone(null)
            return
        }
        scope.launch {
            val file = withContext(Dispatchers.IO) {
                SubtitleConverter.saveAsSrt(
                    sourcePath = source!!,
                    delayMs = (state.subDelaySec * 1000).toLong(),
                    charset = SubtitleConverter.charsetFor(_settings.value.subCodepage),
                    fallbackDir = app.getExternalFilesDir(null) ?: app.filesDir,
                )
            }
            onDone(file)
        }
    }

    fun importFont(uri: Uri, displayName: String, onDone: (String?) -> Unit) {
        scope.launch {
            val family = withContext(Dispatchers.IO) { SubtitleFonts.import(app, uri, displayName) }
            onDone(family)
        }
    }

    fun userFonts(): List<String> = SubtitleFonts.userFonts(app)

    fun updateSettings(transform: (PlayerSettings) -> PlayerSettings) {
        // Apply locally right away so sliders feel instant; the repository round-trip follows.
        val new = transform(_settings.value)
        val old = _settings.value
        _settings.value = new
        if (new.subtitleStyle != old.subtitleStyle) engine.applySubtitleStyle(new.subtitleStyle)
        if (new.effects != old.effects) engine.setAudioFilters(new.effects.toFilterChain())
        if (new.passthrough != old.passthrough) engine.setPassthrough(new.passthrough)
        if (new.subCodepage != old.subCodepage) engine.setSubCodepage(new.subCodepage)
        if (new.backgroundPlay != old.backgroundPlay) updateService()
        scope.launch { settingsRepository.update(transform) }
    }

    // --- Persistence ---

    fun saveProgress(finished: Boolean = false) {
        val s = engine.state.value
        // Right after a file opens the position still reads 0 until mpv reports the real one;
        // saving then would wipe the resume point.
        if (!s.loaded || s.durationSec <= 0 || (!s.positionKnown && !finished)) return
        val done = finished || s.positionSec >= s.durationSec - END_MARGIN_SEC
        history.save(
            PlaybackEntry(
                key = currentItem.target,
                positionMs = if (done) 0 else (s.positionSec * 1000).toLong(),
                durationMs = (s.durationSec * 1000).toLong(),
                lastPlayed = System.currentTimeMillis(),
                finished = done,
                audioDelayMs = (s.audioDelaySec * 1000).toLong(),
                subDelayMs = (s.subDelaySec * 1000).toLong(),
            ),
        )
    }

    fun release() {
        if (released) return
        released = true
        saveProgress()
        abandonAudioFocus()
        runCatching { app.unregisterReceiver(noisyReceiver) }
        session.isActive = false
        session.release()
        PlaybackService.stop(app)
        scope.cancel()
        engine.release()
    }

    private fun handleEnd() {
        saveProgress(finished = true)
        if (_sleepAtEnd.value) {
            _sleepAtEnd.value = false
            engine.setPaused(true)
            return
        }
        if (_repeat.value == RepeatMode.One) {
            engine.seekTo(0.0)
            engine.setPaused(false)
            return
        }
        if (!_settings.value.autoPlayNext) return
        val target = nextIndex()
        if (target != null) {
            playedStack.addLast(_index.value)
            load(target)
        } else if (activity == null && _audioOnly.value) {
            // Listening from the music screen: stop at the top of the last track instead of vanishing.
            engine.setPaused(true)
            engine.seekTo(0.0)
        } else {
            onQueueFinished()
        }
    }

    private fun applyGlobalSettings(s: PlayerSettings) {
        engine.applySubtitleStyle(s.subtitleStyle)
        engine.setAudioFilters(s.effects.toFilterChain())
        engine.setPassthrough(s.passthrough)
    }

    private fun applyOrientation() {
        val activity = activity ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity.isInPictureInPictureMode) return
        val s = engine.state.value
        activity.requestedOrientation = when (_rotation.value) {
            RotationMode.Landscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            RotationMode.Portrait -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            RotationMode.FollowVideo -> when {
                s.videoWidth <= 0 -> activity.requestedOrientation
                s.isPortraitVideo -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        }
    }

    /** The foreground service only exists while playback may continue off-screen and something is loaded. */
    private fun updateService() {
        if (released) return
        val offScreen = _settings.value.backgroundPlay || _audioOnly.value
        if (offScreen && engine.state.value.loaded) PlaybackService.start(app) else PlaybackService.stop(app)
    }

    private fun publishSessionState() {
        val s = engine.state.value
        val actions = PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_SEEK_TO or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
            PlaybackStateCompat.ACTION_FAST_FORWARD or PlaybackStateCompat.ACTION_REWIND or PlaybackStateCompat.ACTION_STOP
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(
                    if (s.paused) PlaybackStateCompat.STATE_PAUSED else PlaybackStateCompat.STATE_PLAYING,
                    (s.positionSec * 1000).toLong(),
                    if (s.paused) 0f else s.speed.toFloat(),
                )
                .build(),
        )
        PlaybackService.refresh()
    }

    private fun publishMetadata() {
        val item = currentItem
        val art = _artwork.value
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, item.title)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, item.title)
                .apply {
                    item.artist?.let {
                        putString(MediaMetadataCompat.METADATA_KEY_ARTIST, it)
                        putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, it)
                    }
                    item.album?.let { putString(MediaMetadataCompat.METADATA_KEY_ALBUM, it) }
                    // The lock screen and the notification show this as the background / cover.
                    if (art != null) {
                        putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art)
                        putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, art)
                    }
                }
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, (engine.state.value.durationSec * 1000).toLong())
                .build(),
        )
        PlaybackService.refresh()
    }

    private fun loadArtwork(item: PlayItem) {
        artworkJob?.cancel()
        _artwork.value = null
        artworkJob = scope.launch {
            _artwork.value = withContext(Dispatchers.IO) { Artwork.load(app, item) }
            publishMetadata()
        }
    }

    /** mpv can't open content:// URIs itself; hand it a file descriptor it will close when done. */
    private fun resolve(item: PlayItem): String? {
        if (!item.isContent) return item.target
        return runCatching {
            app.contentResolver.openFileDescriptor(Uri.parse(item.target), "r")?.detachFd()
        }.getOrNull()?.let { "fdclose://$it" }
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .build(),
                )
                .setOnAudioFocusChangeListener(focusListener)
                .build()
                .also { focusRequest = it }
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusListener)
        }
    }

    private fun PlaybackEntry.resumePointSec(): Double? {
        if (finished || positionMs < MIN_RESUME_MS) return null
        if (durationMs > 0 && positionMs > durationMs - END_MARGIN_SEC * 1000) return null
        return positionMs / 1000.0
    }

    companion object {
        private const val CHECKPOINT_MS = 10_000L
        private const val MIN_RESUME_MS = 5_000L
        private const val END_MARGIN_SEC = 5.0
        private const val RESTART_THRESHOLD_SEC = 5.0
        private const val LONG_AUDIO_MS = 10 * 60_000L

        /** mpv slang list for a preferred subtitle language code. */
        fun slangFor(code: String): String = when (code) {
            "ar" -> "ar,ara,arabic"
            "en" -> "en,eng,english"
            "fr" -> "fr,fre,fra,french"
            "es" -> "es,spa,spanish"
            "tr" -> "tr,tur,turkish"
            else -> ""
        }
    }
}

/** The on-screen video surface, for screenshots. Weak so a closed screen can be collected. */
object PlayerSurface {
    var view: java.lang.ref.WeakReference<SurfaceView>? = null
}

/** Process-wide owner of the current player, so playback can outlive the activity. */
object PlayerHolder {
    private val _current = MutableStateFlow<PlayerController?>(null)

    /** The running session, for the mini player and the music screen. */
    val current: StateFlow<PlayerController?> = _current.asStateFlow()

    val controller: PlayerController? get() = _current.value

    /** Starts a new session (ending the current one) with the saved player settings. */
    fun start(app: VidroApp, items: List<PlayItem>, index: Int, audioOnly: Boolean = false): PlayerController {
        // A few milliseconds: the engine needs subtitle fonts/encoding before it starts.
        val settings = runBlocking { app.playerSettings.settings.first() }
        return PlayerController(app, app.history, app.playerSettings, settings, items, index, audioOnly).also(::replace)
    }

    /** Starting a new video ends the previous session and closes its screen (e.g. an open PiP window). */
    fun replace(new: PlayerController?) {
        _current.value?.let { old ->
            // Cleared first so the old session's own "finished" hook can't end the new one.
            _current.value = null
            old.onQueueFinished()
            old.release()
        }
        _current.value = new
    }

    fun clear(old: PlayerController) {
        if (_current.value === old) {
            _current.value = null
            old.release()
        }
    }
}
