package app.vidro.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A newer release published on GitHub. */
data class AppUpdate(
    val version: String,
    val title: String,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val update: AppUpdate) : UpdateState
    data class Downloading(val update: AppUpdate, val progress: Float) : UpdateState
    data class ReadyToInstall(val update: AppUpdate, val file: File) : UpdateState
    data class Failed(val reason: String) : UpdateState
}

/**
 * The app's only network use: asking GitHub for the latest release of [REPO], and downloading
 * its APK when the user agrees. Nothing else ever leaves the phone.
 */
class UpdateRepository(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var checkedThisLaunch = false
    private var job: Job? = null

    /** Silent check at start-up, once per launch; only an available update surfaces in the UI. */
    fun checkOnLaunch() {
        if (checkedThisLaunch) return
        checkedThisLaunch = true
        check(userInitiated = false)
    }

    fun check(userInitiated: Boolean = true) {
        if (job?.isActive == true) return
        job = scope.launch {
            if (userInitiated) _state.value = UpdateState.Checking
            val result = runCatching { fetchLatest() }
            _state.value = when {
                result.isFailure -> if (userInitiated) UpdateState.Failed(result.exceptionOrNull()?.message.orEmpty()) else UpdateState.Idle
                result.getOrNull()?.let { isNewer(it.version, currentVersion()) } == true -> UpdateState.Available(result.getOrNull()!!)
                else -> if (userInitiated) UpdateState.UpToDate else UpdateState.Idle
            }
        }
    }

    fun dismiss() {
        job?.cancel()
        _state.value = UpdateState.Idle
    }

    fun download(update: AppUpdate) {
        job?.cancel()
        job = scope.launch {
            _state.value = UpdateState.Downloading(update, 0f)
            val file = runCatching {
                withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                    dir.listFiles()?.forEach { it.delete() }
                    val target = File(dir, "Vidro-${update.version}.apk")
                    val connection = open(update.apkUrl)
                    val total = connection.contentLengthLong.takeIf { it > 0 } ?: update.sizeBytes
                    connection.inputStream.use { input ->
                        target.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var done = 0L
                            var lastReported = 0f
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                done += read
                                val progress = if (total > 0) done.toFloat() / total else 0f
                                if (progress - lastReported >= 0.01f) {
                                    lastReported = progress
                                    _state.value = UpdateState.Downloading(update, progress)
                                }
                            }
                        }
                    }
                    target
                }
            }
            _state.value = file.fold(
                onSuccess = { UpdateState.ReadyToInstall(update, it) },
                onFailure = { UpdateState.Failed(it.message.orEmpty()) },
            )
        }
    }

    /** Android 8+ asks the user once to allow installs from Vidro. */
    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Hands the APK to the system installer, which shows its own confirmation. */
    fun install(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            _state.value = UpdateState.Failed("installer")
        }
    }

    fun currentVersion(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty().substringBefore('-')

    private suspend fun fetchLatest(): AppUpdate? = withContext(Dispatchers.IO) {
        val connection = open("https://api.github.com/repos/$REPO/releases/latest")
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        if (connection.responseCode == 404) return@withContext null
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val json = Json.parseToJsonElement(body).jsonObject
        val asset = json["assets"]?.jsonArray
            ?.map { it.jsonObject }
            ?.firstOrNull { it.string("name")?.endsWith(".apk") == true }
            ?: return@withContext null
        AppUpdate(
            version = json.string("tag_name").orEmpty().removePrefix("v"),
            title = json.string("name").orEmpty(),
            notes = cleanNotes(json.string("body").orEmpty()),
            apkUrl = asset.string("browser_download_url") ?: return@withContext null,
            sizeBytes = asset["size"]?.jsonPrimitive?.longOrNull ?: 0L,
        )
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Vidro-Android")
        }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    companion object {
        const val REPO = "sheed-x/Vidro"
        const val REPO_URL = "https://github.com/sheed-x/Vidro"
        const val PROFILE_URL = "https://github.com/sheed-x"

        /** "0.10.0" > "0.9.2": compares the numeric parts one by one. */
        fun isNewer(remote: String, current: String): Boolean {
            val r = remote.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
            val c = current.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(r.size, c.size)) {
                val a = r.getOrElse(i) { 0 }
                val b = c.getOrElse(i) { 0 }
                if (a != b) return a > b
            }
            return false
        }

        /** GitHub release notes are Markdown; show them as clean plain text. */
        private fun cleanNotes(markdown: String): String = markdown.lines()
            .joinToString("\n") { line ->
                line.trim()
                    .removePrefix("### ").removePrefix("## ").removePrefix("# ")
                    .replace("**", "")
                    .let { if (it.startsWith("- ") || it.startsWith("* ")) "• " + it.drop(2) else it }
            }
            .trim()
    }
}
