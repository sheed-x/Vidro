package app.vidro.data

import java.io.File

data class Video(
    val id: Long,
    val path: String,
    val name: String,
    val size: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    /** Seconds since epoch. */
    val dateAdded: Long,
    /** Seconds since epoch. */
    val dateModified: Long,
) {
    val file: File get() = File(path)
    val folderPath: String get() = path.substringBeforeLast('/')
    val title: String get() = name.substringBeforeLast('.')
    val extension: String get() = name.substringAfterLast('.', "").uppercase()
}

data class VideoFolder(
    val path: String,
    val name: String,
    val videos: List<Video>,
) {
    val totalSize: Long = videos.sumOf { it.size }
    val lastModified: Long = videos.maxOfOrNull { it.dateModified } ?: 0
    /** Most recently modified video, used as the folder's cover in grid view. */
    val cover: Video? = videos.maxByOrNull { it.dateModified }

    /** Up to three latest videos for the collage cover in grid view. */
    val covers: List<Video> = videos.sortedByDescending { it.dateModified }.take(3)
}

data class LibraryState(
    val folders: List<VideoFolder> = emptyList(),
    val isScanning: Boolean = false,
    val hasScanned: Boolean = false,
) {
    val allVideos: List<Video> get() = folders.flatMap { it.videos }
}
