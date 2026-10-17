package app.vidro.data

import android.content.Context
import android.database.ContentObserver
import android.os.Environment
import android.provider.MediaStore
import app.vidro.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/** Single source of truth for the video library; rescans when MediaStore reports changes. */
class LibraryRepository(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    private var scanJob: Job? = null
    private var debounceJob: Job? = null
    private var observing = false

    // Probe results survive rescans; keyed by path + modification time so edited files are re-read.
    private val probed = ConcurrentHashMap<String, Video>()

    private val observer = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean) {
            debounceJob?.cancel()
            debounceJob = scope.launch {
                delay(1500)
                refresh()
            }
        }
    }

    /** Call whenever access may have changed (app start, returning from settings). */
    fun start() {
        if (!StorageAccess.hasAnyAccess(context)) return
        if (!observing) {
            context.contentResolver.registerContentObserver(
                MediaStore.Files.getContentUri("external"), true, observer,
            )
            observing = true
        }
        refresh()
    }

    fun refresh() {
        if (!StorageAccess.hasAnyAccess(context)) return
        scanJob?.cancel()
        scanJob = scope.launch {
            _state.update { it.copy(isScanning = true) }
            val scanned = runCatching {
                MediaScanner.scan(context, StorageAccess.hasFullAccess(context))
            }.getOrDefault(emptyList())

            val byPath = LinkedHashMap<String, Video>(scanned.size)
            for (video in scanned) byPath[video.path] = probed[key(video)] ?: video
            _state.value = LibraryState(group(byPath.values), isScanning = false, hasScanned = true)

            // Second pass: read length/resolution from files MediaStore couldn't describe, publishing in batches.
            val missing = byPath.values.filter { (it.durationMs <= 0 || it.width <= 0) && !probed.containsKey(key(it)) }
            missing.chunked(PROBE_BATCH).forEach { batch ->
                ensureActive()
                for (video in batch) {
                    val result = MediaScanner.probe(video) ?: video
                    probed[key(video)] = result
                    byPath[video.path] = result
                }
                _state.update { it.copy(folders = group(byPath.values)) }
            }
        }
    }

    private fun group(videos: Collection<Video>): List<VideoFolder> {
        val internalRoot = Environment.getExternalStorageDirectory().absolutePath
        return videos.groupBy { it.folderPath }.map { (path, list) ->
            val name = if (path == internalRoot) {
                context.getString(R.string.internal_storage)
            } else {
                path.substringAfterLast('/')
            }
            VideoFolder(path = path, name = name, videos = list)
        }
    }

    private fun key(video: Video) = "${video.path}|${video.dateModified}"

    private companion object {
        const val PROBE_BATCH = 8
    }
}
