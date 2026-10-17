package app.vidro.ui.settings

import android.content.Context
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VisibilityOff
import app.vidro.player.ResumeMode
import app.vidro.data.HomeShelf
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.ViewCarousel
import coil3.SingletonImageLoader
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import app.vidro.R
import app.vidro.data.StorageAccess
import app.vidro.ui.components.Group
import app.vidro.ui.components.GroupRow
import app.vidro.ui.library.LibraryViewModel
import app.vidro.ui.library.LocalBottomBarSpace
import app.vidro.ui.library.VidroTopBar
import app.vidro.ui.welcome.AppLogo

private data class LanguageOption(val tag: String, val label: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: LibraryViewModel, onOpenAbout: () -> Unit = {}) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val hasFullAccess by viewModel.hasFullAccess.collectAsState()
    val player by viewModel.playerSettings.collectAsState()
    var languageDialog by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    val minDurations = listOf(0, 10, 30, 60)
    val minDurationLabels = listOf(
        stringResource(R.string.off),
        stringResource(R.string.min_10s),
        stringResource(R.string.min_30s),
        stringResource(R.string.min_1m),
    )
    val resumeLabels = listOf(
        stringResource(R.string.resume_auto),
        stringResource(R.string.resume_ask),
        stringResource(R.string.resume_never),
    )
    val doubleTapOptions = listOf(5, 10, 15, 20, 30)
    val shelfLabels = listOf(
        stringResource(R.string.continue_watching),
        stringResource(R.string.recently_added),
        stringResource(R.string.off),
    )
    val secondsFormat = stringResource(R.string.player_seconds)

    val languages = listOf(
        LanguageOption("", stringResource(R.string.language_system)),
        LanguageOption("en", "English"),
        LanguageOption("ar", "العربية"),
    )
    val currentTag = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-')
    val currentLanguage = languages.firstOrNull { it.tag == currentTag } ?: languages.first()
    val version = remember { appVersion(context) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { VidroTopBar(title = stringResource(R.string.tab_settings), scrollBehavior = scrollBehavior) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = LocalBottomBarSpace.current + 24.dp),
        ) {
            item { AboutHeader(version) }

            item {
                Group(
                    title = stringResource(R.string.settings_general),
                    rows = listOf(
                        {
                            GroupRow(
                                title = stringResource(R.string.language),
                                summary = currentLanguage.label,
                                icon = Icons.Rounded.Language,
                                onClick = { languageDialog = true },
                                trailing = { Chevron() },
                            )
                        },
                        {
                            SwitchRow(
                                title = stringResource(R.string.motion_effects),
                                summary = stringResource(R.string.motion_effects_desc),
                                icon = Icons.Rounded.ScreenRotation,
                                checked = settings.motionEffects,
                                onChange = { viewModel.setMotionEffects(it) },
                            )
                        },
                        {
                            SwitchRow(
                                title = stringResource(R.string.haptics),
                                summary = stringResource(R.string.haptics_desc),
                                icon = Icons.Rounded.Vibration,
                                checked = settings.haptics,
                                onChange = { viewModel.setHaptics(it) },
                            )
                        },
                    ),
                )
            }

            item {
                Group(
                    title = stringResource(R.string.settings_library),
                    rows = listOfNotNull<@Composable () -> Unit>(
                        {
                            // An explicit on/off switch: hiding the shelf used to be buried in a choice list.
                            SwitchRow(
                                title = stringResource(R.string.home_shelf_show),
                                summary = stringResource(R.string.home_shelf_show_desc),
                                icon = Icons.Rounded.ViewCarousel,
                                checked = settings.homeShelf != HomeShelf.Off,
                                onChange = { on -> viewModel.setHomeShelf(if (on) HomeShelf.ContinueWatching else HomeShelf.Off) },
                            )
                        },
                        if (settings.homeShelf != HomeShelf.Off) {
                            {
                                GroupRow(
                                    title = stringResource(R.string.home_shelf),
                                    summary = shelfLabels[settings.homeShelf.ordinal],
                                    icon = Icons.Rounded.ViewCarousel,
                                    onClick = { dialog = SettingsDialog.HomeShelf },
                                    trailing = { Chevron() },
                                )
                            }
                        } else {
                            null
                        },
                        {
                            SwitchRow(
                                title = stringResource(R.string.show_thumbnails),
                                summary = stringResource(R.string.show_thumbnails_desc),
                                icon = Icons.Rounded.Image,
                                checked = settings.showThumbnails,
                                onChange = { viewModel.setShowThumbnails(it) },
                            )
                        },
                        {
                            SwitchRow(
                                title = stringResource(R.string.show_new),
                                summary = stringResource(R.string.show_new_desc),
                                icon = Icons.Rounded.NewReleases,
                                checked = settings.showNewBadge,
                                onChange = { viewModel.setShowNewBadge(it) },
                            )
                        },
                        {
                            SwitchRow(
                                title = stringResource(R.string.show_watch_time),
                                summary = stringResource(R.string.show_watch_time_desc),
                                icon = Icons.Rounded.History,
                                checked = settings.showWatchTime,
                                onChange = { viewModel.setShowWatchTime(it) },
                            )
                        },
                        {
                            GroupRow(
                                title = stringResource(R.string.min_duration),
                                summary = minDurationLabels[minDurations.indexOf(settings.minDurationSec).coerceAtLeast(0)],
                                icon = Icons.Rounded.Timelapse,
                                onClick = { dialog = SettingsDialog.MinDuration },
                                trailing = { Chevron() },
                            )
                        },
                        {
                            GroupRow(
                                title = stringResource(R.string.hidden_folders),
                                summary = if (settings.hiddenFolders.isEmpty()) stringResource(R.string.hidden_folders_none)
                                else stringResource(R.string.hidden_folders_count, settings.hiddenFolders.size),
                                icon = Icons.Rounded.VisibilityOff,
                                onClick = { dialog = SettingsDialog.HiddenFolders },
                                trailing = { Chevron() },
                            )
                        },
                        {
                            GroupRow(
                                title = stringResource(R.string.rescan),
                                summary = stringResource(R.string.rescan_desc),
                                icon = Icons.Rounded.Refresh,
                                onClick = {
                                    viewModel.refresh()
                                    Toast.makeText(context, R.string.rescan_done, Toast.LENGTH_SHORT).show()
                                },
                            )
                        },
                        {
                            GroupRow(
                                title = stringResource(R.string.file_access),
                                summary = stringResource(
                                    if (hasFullAccess) R.string.file_access_full else R.string.file_access_limited,
                                ),
                                icon = Icons.Rounded.FolderOpen,
                                onClick = if (StorageAccess.usesAllFilesSettings) {
                                    { StorageAccess.openAllFilesSettings(context) }
                                } else null,
                                trailing = if (StorageAccess.usesAllFilesSettings) {
                                    { Chevron() }
                                } else null,
                            )
                        },
                    ),
                )
            }

            item {
                Group(
                    title = stringResource(R.string.settings_player),
                    rows = buildList {
                        add {
                            GroupRow(
                                title = stringResource(R.string.resume_mode),
                                summary = resumeLabels[player.resumeMode.ordinal],
                                icon = Icons.Rounded.PlayCircle,
                                onClick = { dialog = SettingsDialog.Resume },
                                trailing = { Chevron() },
                            )
                        }
                        add {
                            GroupRow(
                                title = stringResource(R.string.doubletap_seconds),
                                summary = secondsFormat.format(player.doubleTapSeconds),
                                icon = Icons.Rounded.FastForward,
                                onClick = { dialog = SettingsDialog.DoubleTap },
                                trailing = { Chevron() },
                            )
                        }
                        add {
                            SwitchRow(
                                title = stringResource(R.string.auto_next),
                                summary = stringResource(R.string.auto_next_desc),
                                icon = Icons.Rounded.SkipNext,
                                checked = player.autoPlayNext,
                                onChange = { on -> viewModel.updatePlayerSettings { it.copy(autoPlayNext = on) } },
                            )
                        }
                        add {
                            SwitchRow(
                                title = stringResource(R.string.background_play),
                                summary = stringResource(R.string.background_play_desc),
                                icon = Icons.Rounded.Headphones,
                                checked = player.backgroundPlay,
                                onChange = { on ->
                                    if (on && Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    viewModel.updatePlayerSettings { it.copy(backgroundPlay = on) }
                                },
                            )
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            add {
                                SwitchRow(
                                    title = stringResource(R.string.auto_pip),
                                    summary = stringResource(R.string.auto_pip_desc),
                                    icon = Icons.Rounded.PictureInPictureAlt,
                                    checked = player.autoPip,
                                    onChange = { on -> viewModel.updatePlayerSettings { it.copy(autoPip = on) } },
                                )
                            }
                        }
                        add {
                            SwitchRow(
                                title = stringResource(R.string.volume_boost),
                                summary = stringResource(R.string.volume_boost_desc),
                                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                                checked = player.volumeBoost,
                                onChange = { on -> viewModel.updatePlayerSettings { it.copy(volumeBoost = on) } },
                            )
                        }
                    },
                )
            }

            item {
                Group(
                    title = stringResource(R.string.settings_privacy),
                    rows = listOf(
                        {
                            GroupRow(
                                title = stringResource(R.string.privacy_offline),
                                summary = stringResource(R.string.privacy_offline_desc),
                                icon = Icons.Rounded.CloudOff,
                                trailing = { Verified() },
                            )
                        },
                        {
                            GroupRow(
                                title = stringResource(R.string.privacy_tracking),
                                summary = stringResource(R.string.privacy_tracking_desc),
                                icon = Icons.Rounded.Block,
                                trailing = { Verified() },
                            )
                        },
                        {
                            GroupRow(
                                title = stringResource(R.string.clear_history),
                                summary = stringResource(R.string.clear_history_desc),
                                icon = Icons.Rounded.DeleteSweep,
                                onClick = { dialog = SettingsDialog.ClearHistory },
                            )
                        },
                        {
                            GroupRow(
                                title = stringResource(R.string.clear_thumbs),
                                summary = stringResource(R.string.clear_thumbs_desc),
                                icon = Icons.Rounded.CleaningServices,
                                onClick = {
                                    SingletonImageLoader.get(context).let { loader ->
                                        loader.memoryCache?.clear()
                                        loader.diskCache?.clear()
                                    }
                                    Toast.makeText(context, R.string.thumbs_cleared, Toast.LENGTH_SHORT).show()
                                },
                            )
                        },
                    ),
                )
            }

            item {
                Group(
                    title = stringResource(R.string.settings_about),
                    rows = listOf {
                        GroupRow(
                            title = stringResource(R.string.about_title),
                            summary = stringResource(R.string.about_version, version),
                            icon = Icons.Rounded.Info,
                            onClick = onOpenAbout,
                            trailing = { Chevron() },
                        )
                    },
                )
            }
        }
    }

    when (dialog) {
        SettingsDialog.MinDuration -> ChoiceDialog(
            title = stringResource(R.string.min_duration),
            options = minDurationLabels,
            selected = minDurations.indexOf(settings.minDurationSec),
            onDismiss = { dialog = null },
        ) { viewModel.setMinDuration(minDurations[it]) }

        SettingsDialog.HomeShelf -> ChoiceDialog(
            title = stringResource(R.string.home_shelf),
            // "Off" is the switch above; here you only pick what the shelf shows.
            options = shelfLabels.take(2),
            selected = settings.homeShelf.ordinal,
            onDismiss = { dialog = null },
        ) { viewModel.setHomeShelf(HomeShelf.entries[it]) }

        SettingsDialog.Resume -> ChoiceDialog(
            title = stringResource(R.string.resume_mode),
            options = resumeLabels,
            selected = player.resumeMode.ordinal,
            onDismiss = { dialog = null },
        ) { i -> viewModel.updatePlayerSettings { it.copy(resumeMode = ResumeMode.entries[i]) } }

        SettingsDialog.DoubleTap -> ChoiceDialog(
            title = stringResource(R.string.doubletap_seconds),
            options = doubleTapOptions.map { secondsFormat.format(it) },
            selected = doubleTapOptions.indexOf(player.doubleTapSeconds),
            onDismiss = { dialog = null },
        ) { i -> viewModel.updatePlayerSettings { it.copy(doubleTapSeconds = doubleTapOptions[i]) } }

        SettingsDialog.HiddenFolders -> HiddenFoldersDialog(
            folders = settings.hiddenFolders.sorted(),
            onShow = { viewModel.setFolderHidden(it, hidden = false) },
            onDismiss = { dialog = null },
        )

        SettingsDialog.ClearHistory -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.clear_history_question)) },
            text = { Text(stringResource(R.string.clear_history_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearHistory()
                    dialog = null
                    Toast.makeText(context, R.string.history_cleared, Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.clear)) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(android.R.string.cancel)) } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        )

        SettingsDialog.Licenses -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.licenses)) },
            text = {
                Text(
                    LICENSES,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(android.R.string.ok)) } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        )

        null -> Unit
    }

    if (languageDialog) {
        AlertDialog(
            onDismissRequest = { languageDialog = false },
            title = { Text(stringResource(R.string.language)) },
            text = {
                Column {
                    languages.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable(role = Role.RadioButton) {
                                    languageDialog = false
                                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(option.tag))
                                }
                                .padding(vertical = 4.dp),
                        ) {
                            RadioButton(selected = option == currentLanguage, onClick = null)
                            Text(
                                option.label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { languageDialog = false }) { Text(stringResource(android.R.string.cancel)) }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        )
    }
}

@Composable
private fun AboutHeader(version: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(20.dp),
    ) {
        AppLogo(size = 64)
        Column(Modifier.padding(start = 16.dp)) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.welcome_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                version,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    summary: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    GroupRow(
        title = title,
        summary = summary,
        icon = icon,
        onClick = { onChange(!checked) },
        trailing = { Switch(checked = checked, onCheckedChange = onChange) },
    )
}

@Composable
private fun Chevron() {
    Icon(
        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Verified() {
    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
}

// Left-to-right isolate so "0.1.0-debug" isn't reordered inside Arabic text.
private fun appVersion(context: Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull().orEmpty().let { "⁦$it⁩" }

private enum class SettingsDialog { HomeShelf, MinDuration, Resume, DoubleTap, HiddenFolders, ClearHistory, Licenses }

@Composable
private fun ChoiceDialog(title: String, options: List<String>, selected: Int, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { i, label ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable(role = Role.RadioButton) {
                                onSelect(i)
                                onDismiss()
                            }
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(selected = i == selected, onClick = null)
                        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    )
}

@Composable
private fun HiddenFoldersDialog(folders: List<String>, onShow: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.hidden_folders)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (folders.isEmpty()) {
                    Text(stringResource(R.string.hidden_folders_empty), style = MaterialTheme.typography.bodyMedium)
                }
                folders.forEach { path ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(path.substringAfterLast('/'), style = MaterialTheme.typography.bodyLarge)
                            Text(path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { onShow(path) }) { Text(stringResource(R.string.unhide)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    )
}

internal const val LICENSES = """mpv — LGPL 2.1+
FFmpeg — LGPL 2.1+
libass, FriBiDi, HarfBuzz, FreeType — ISC / LGPL / MIT / FreeType License
libmpv-android (dev.jdtech.mpv) — MIT
Jetpack Compose, AndroidX, Material Components — Apache 2.0
Kotlin, kotlinx.coroutines, kotlinx.serialization — Apache 2.0
Coil — Apache 2.0
Haze — Apache 2.0
Google Sans Flex — SIL Open Font License 1.1
Noto fonts (from the system) — SIL Open Font License 1.1"""
