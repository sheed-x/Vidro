package app.vidro.player

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.ViewDay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.vidro.R
import app.vidro.ui.components.formatDuration
import app.vidro.ui.components.formatSize
import java.util.Locale
import kotlin.math.roundToInt

/** Every panel the player can show; [parent] is where the back arrow leads. */
enum class Panel(val parent: Panel? = null) {
    More,
    Audio,
    Subtitles,
    SubtitleStyle(Subtitles),
    Effects(Audio),
    Bookmarks(More),
    Queue(More),
    Chapters(More),
    Info(More),
    Sleep(More),
    CustomAspect(More),
    Stereo(More),
    Settings(More),
}

/** Actions the panels need from the screen around them. */
class PanelActions(
    val open: (Panel) -> Unit,
    val close: () -> Unit,
    val back: () -> Unit,
    val enterPip: (() -> Unit)?,
    /** Keep only the sound and continue in the music screen (works with the screen locked). */
    val listen: () -> Unit,
    val kidsLock: () -> Unit,
    val toast: (String) -> Unit,
)

@Composable
fun PanelContent(panel: Panel, controller: PlayerController, actions: PanelActions, scope: ColumnScope) = with(scope) {
    val title = stringResource(
        when (panel) {
            Panel.More -> R.string.panel_more
            Panel.Audio -> R.string.panel_audio
            Panel.Subtitles -> R.string.panel_subtitles
            Panel.SubtitleStyle -> R.string.panel_sub_style
            Panel.Effects -> R.string.panel_effects
            Panel.Bookmarks -> R.string.panel_bookmarks
            Panel.Queue -> R.string.panel_queue
            Panel.Chapters -> R.string.panel_chapters
            Panel.Info -> R.string.panel_info
            Panel.Sleep -> R.string.panel_sleep
            Panel.CustomAspect -> R.string.panel_aspect
            Panel.Stereo -> R.string.panel_stereo
            Panel.Settings -> R.string.panel_settings
        },
    )
    PanelHeader(title, onBack = if (panel.parent != null) actions.back else null, onClose = actions.close)
    PanelBody {
        when (panel) {
            Panel.More -> MorePanel(controller, actions)
            Panel.Audio -> AudioPanel(controller, actions)
            Panel.Subtitles -> SubtitlesPanel(controller, actions)
            Panel.SubtitleStyle -> SubtitleStylePanel(controller, actions)
            Panel.Effects -> EffectsPanel(controller)
            Panel.Bookmarks -> BookmarksPanel(controller)
            Panel.Queue -> QueuePanel(controller)
            Panel.Chapters -> ChaptersPanel(controller, actions)
            Panel.Info -> InfoPanel(controller)
            Panel.Sleep -> SleepPanel(controller, actions)
            Panel.CustomAspect -> CustomAspectPanel(controller, actions)
            Panel.Stereo -> StereoPanel(controller)
            Panel.Settings -> SettingsPanel(controller)
        }
    }
}

// --- More ---

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MorePanel(controller: PlayerController, actions: PanelActions) {
    val state by controller.engine.state.collectAsState()
    val repeat by controller.repeat.collectAsState()
    val shuffle by controller.shuffle.collectAsState()
    val flipH by controller.flipH.collectAsState()
    val flipV by controller.flipV.collectAsState()
    val stereo by controller.stereo.collectAsState()
    val sleepAt by controller.sleepAt.collectAsState()
    val sleepAtEnd by controller.sleepAtEnd.collectAsState()
    val saved = stringResource(R.string.screenshot_saved)
    val failed = stringResource(R.string.screenshot_failed)

    val tiles = buildList<@Composable (Modifier) -> Unit> {
        add { m -> ToolTile(Icons.Rounded.Headphones, stringResource(R.string.play_as_audio), m) { actions.listen() } }
        add { m -> ToolTile(Icons.Rounded.Bedtime, stringResource(R.string.panel_sleep), m, active = sleepAt != null || sleepAtEnd) { actions.open(Panel.Sleep) } }
        add { m ->
            ToolTile(Icons.Rounded.PhotoCamera, stringResource(R.string.screenshot), m) {
                controller.takeScreenshot { ok -> actions.toast(if (ok) saved else failed) }
            }
        }
        add { m -> ToolTile(Icons.Rounded.Bookmark, stringResource(R.string.panel_bookmarks), m) { actions.open(Panel.Bookmarks) } }
        if (state.chapters.isNotEmpty()) {
            add { m -> ToolTile(Icons.Rounded.List, stringResource(R.string.panel_chapters), m) { actions.open(Panel.Chapters) } }
        }
        add { m -> ToolTile(Icons.AutoMirrored.Rounded.QueueMusic, stringResource(R.string.panel_queue), m) { actions.open(Panel.Queue) } }
        add { m ->
            ToolTile(
                if (repeat == RepeatMode.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                stringResource(
                    when (repeat) {
                        RepeatMode.Off -> R.string.repeat_off
                        RepeatMode.One -> R.string.repeat_one
                        RepeatMode.All -> R.string.repeat_all
                    },
                ),
                m, active = repeat != RepeatMode.Off,
            ) { controller.cycleRepeat() }
        }
        add { m -> ToolTile(Icons.Rounded.Shuffle, stringResource(R.string.shuffle), m, active = shuffle) { controller.toggleShuffle() } }
        add { m -> ToolTile(Icons.Rounded.Flip, stringResource(R.string.flip_h), m, active = flipH) { controller.toggleFlipH() } }
        add { m -> ToolTile(Icons.Rounded.ViewDay, stringResource(R.string.flip_v), m, active = flipV) { controller.toggleFlipV() } }
        add { m -> ToolTile(Icons.Rounded.AspectRatio, stringResource(R.string.panel_aspect), m) { actions.open(Panel.CustomAspect) } }
        add { m -> ToolTile(Icons.Rounded.ViewInAr, stringResource(R.string.panel_stereo), m, active = stereo != StereoMode.Off) { actions.open(Panel.Stereo) } }
        add { m -> ToolTile(Icons.Rounded.ChildCare, stringResource(R.string.kids_lock), m) { actions.kidsLock() } }
        add { m -> ToolTile(Icons.Rounded.Equalizer, stringResource(R.string.panel_effects), m) { actions.open(Panel.Effects) } }
        add { m -> ToolTile(Icons.Rounded.Info, stringResource(R.string.panel_info), m) { actions.open(Panel.Info) } }
        actions.enterPip?.let { pip -> add { m -> ToolTile(Icons.Rounded.PictureInPictureAlt, stringResource(R.string.pip), m) { pip() } } }
        add { m -> ToolTile(Icons.Rounded.Settings, stringResource(R.string.panel_settings), m) { actions.open(Panel.Settings) } }
    }
    FlowRow(
        maxItemsInEachRow = 3,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        tiles.forEach { tile -> tile(Modifier.weight(1f)) }
        // Keep the last row's tiles the same width as the others.
        repeat((3 - tiles.size % 3) % 3) { Spacer(Modifier.weight(1f)) }
    }
}

// --- Audio ---

@Composable
private fun AudioPanel(controller: PlayerController, actions: PanelActions) {
    val context = LocalContext.current
    val state by controller.engine.state.collectAsState()
    val settings by controller.settings.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) controller.addExternalFile(uri, context.displayName(uri) ?: "audio", subtitle = false)
    }

    SectionLabel(stringResource(R.string.audio_tracks))
    val tracks = state.tracksOf(TrackType.Audio)
    if (tracks.isEmpty()) Text(stringResource(R.string.no_tracks), color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(8.dp))
    tracks.forEachIndexed { i, track ->
        RadioRow(trackTitle(track, i), selected = state.audioId == track.id, sub = trackDetails(track)) { controller.engine.selectAudio(track.id) }
    }
    ActionRow(Icons.Rounded.LibraryMusic, stringResource(R.string.load_audio)) { picker.launch(arrayOf("audio/*", "video/*")) }

    SectionLabel(stringResource(R.string.speed))
    val speeds = listOf(0.5, 0.75, 1.0, 1.25, 1.5, 2.0)
    ChipGroup(speeds.map { speedLabel(it) }, selected = { abs(state.speed - speeds[it]) < 0.01 }) { controller.engine.setSpeed(speeds[it]) }
    SliderRow(stringResource(R.string.speed), speedLabel(state.speed), state.speed.toFloat(), 0.25f..4f, steps = 74) {
        controller.engine.setSpeed((it * 20).roundToInt() / 20.0)
    }

    SectionLabel(stringResource(R.string.volume))
    val maxVolume = if (settings.volumeBoost) 200f else 100f
    SliderRow(stringResource(R.string.volume), "${state.volume.roundToInt()}%", state.volume.toFloat(), 0f..maxVolume) {
        controller.engine.setVolume(it.toDouble())
    }

    StepperRow(
        stringResource(R.string.audio_delay), delayLabel(state.audioDelaySec),
        onMinus = { controller.engine.setAudioDelay(state.audioDelaySec - 0.05) },
        onPlus = { controller.engine.setAudioDelay(state.audioDelaySec + 0.05) },
        onReset = { controller.engine.setAudioDelay(0.0) },
    )

    SectionLabel(stringResource(R.string.panel_effects))
    ActionRow(
        Icons.Rounded.Equalizer,
        stringResource(R.string.panel_effects),
        sub = if (!settings.effects.enabled || settings.effects.isNeutral) stringResource(R.string.effects_off) else presetName(settings.effects.preset),
        chevron = true,
    ) { actions.open(Panel.Effects) }
    SwitchRow(stringResource(R.string.passthrough), settings.passthrough, sub = stringResource(R.string.passthrough_desc)) { on ->
        controller.updateSettings { it.copy(passthrough = on) }
    }
}

@Composable
private fun EffectsPanel(controller: PlayerController) {
    val settings by controller.settings.collectAsState()
    val fx = settings.effects
    fun update(change: (AudioEffects) -> AudioEffects) = controller.updateSettings { it.copy(effects = change(it.effects)) }

    SwitchRow(stringResource(R.string.panel_effects), fx.enabled) { on -> update { it.copy(enabled = on) } }
    SectionLabel(stringResource(R.string.presets))
    val presets = AudioEffects.PRESETS.keys.toList()
    ChipGroup(presets.map { presetName(it) }, selected = { presets[it] == fx.preset }) { i ->
        update { AudioEffects.PRESETS.getValue(presets[i]).copy(enabled = true) }
    }

    SectionLabel(stringResource(R.string.equalizer))
    AudioEffects.EQ_BANDS.forEachIndexed { i, hz ->
        val gain = fx.bands.getOrElse(i) { 0f }
        SliderRow(bandLabel(hz), dbLabel(gain), gain, -12f..12f, steps = 23) { value ->
            update { e -> e.copy(bands = e.bands.toMutableList().also { it[i] = value.roundToInt().toFloat() }, preset = "custom") }
        }
    }

    SectionLabel(stringResource(R.string.panel_effects))
    SliderRow(stringResource(R.string.bass_boost), dbLabel(fx.bass), fx.bass, 0f..12f, steps = 11) { v -> update { it.copy(bass = v.roundToInt().toFloat(), preset = "custom") } }
    SliderRow(stringResource(R.string.treble), dbLabel(fx.treble), fx.treble, 0f..12f, steps = 11) { v -> update { it.copy(treble = v.roundToInt().toFloat(), preset = "custom") } }
    SliderRow(stringResource(R.string.virtualizer), "${(fx.virtualizer * 100).roundToInt()}%", fx.virtualizer, 0f..1f, steps = 9) { v ->
        update { it.copy(virtualizer = (v * 10).roundToInt() / 10f, preset = "custom") }
    }
    SectionLabel(stringResource(R.string.reverb))
    val reverbs = Reverb.entries
    ChipGroup(
        reverbs.map {
            stringResource(
                when (it) {
                    Reverb.None -> R.string.reverb_none
                    Reverb.Room -> R.string.reverb_room
                    Reverb.Hall -> R.string.reverb_hall
                    Reverb.Stage -> R.string.reverb_stage
                },
            )
        },
        selected = { reverbs[it] == fx.reverb },
    ) { i -> update { it.copy(reverb = reverbs[i], preset = "custom") } }
    SwitchRow(stringResource(R.string.normalize), fx.normalize, sub = stringResource(R.string.normalize_desc)) { on ->
        update { it.copy(normalize = on, preset = "custom") }
    }
    PanelTextButton(stringResource(R.string.reset)) { update { AudioEffects() } }
}

// --- Subtitles ---

private val codepages = listOf(
    "auto", "utf8", "cp1256", "iso-8859-6", "cp1252", "cp1251", "cp1254", "cp1255", "cp1253", "gbk", "big5", "shift_jis", "euc-kr",
)

@Composable
private fun SubtitlesPanel(controller: PlayerController, actions: PanelActions) {
    val context = LocalContext.current
    val state by controller.engine.state.collectAsState()
    val settings by controller.settings.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) controller.addExternalFile(uri, context.displayName(uri) ?: "subtitle.srt", subtitle = true)
    }
    val savedFormat = stringResource(R.string.saved_to)
    val saveFailed = stringResource(R.string.save_failed)
    val subs = state.tracksOf(TrackType.Subtitle)

    SectionLabel(stringResource(R.string.subtitle_track))
    RadioRow(stringResource(R.string.off), selected = state.subtitleId == null) { controller.engine.selectSubtitle(null) }
    subs.forEachIndexed { i, track ->
        RadioRow(trackTitle(track, i), selected = state.subtitleId == track.id, sub = trackDetails(track)) { controller.engine.selectSubtitle(track.id) }
    }
    ActionRow(Icons.Rounded.Subtitles, stringResource(R.string.load_subtitle)) { picker.launch(arrayOf("*/*")) }

    if (subs.size > 1) {
        SectionLabel(stringResource(R.string.subtitle_secondary))
        RadioRow(stringResource(R.string.off), selected = state.secondarySubtitleId == null) { controller.engine.selectSecondarySubtitle(null) }
        subs.filter { it.id != state.subtitleId }.forEachIndexed { i, track ->
            RadioRow(trackTitle(track, i), selected = state.secondarySubtitleId == track.id, sub = trackDetails(track)) {
                controller.engine.selectSecondarySubtitle(track.id)
            }
        }
    }

    SectionLabel(stringResource(R.string.sync))
    StepperRow(
        stringResource(R.string.subtitle_delay), delayLabel(state.subDelaySec),
        onMinus = { controller.engine.setSubDelay(state.subDelaySec - 0.1) },
        onPlus = { controller.engine.setSubDelay(state.subDelaySec + 0.1) },
        onReset = { controller.engine.setSubDelay(0.0) },
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionChip(Icons.Rounded.SkipPrevious, stringResource(R.string.previous_line), Modifier.weight(1f)) { controller.engine.seekSubtitleLine(-1) }
        ActionChip(Icons.Rounded.SkipNext, stringResource(R.string.next_line), Modifier.weight(1f)) { controller.engine.seekSubtitleLine(1) }
    }

    SectionLabel(stringResource(R.string.encoding))
    ChipGroup(codepages.map { codepageName(it) }, selected = { codepages[it] == settings.subCodepage }) { i ->
        controller.updateSettings { it.copy(subCodepage = codepages[i]) }
    }

    SectionLabel(stringResource(R.string.panel_sub_style))
    ActionRow(Icons.Rounded.TextFields, stringResource(R.string.panel_sub_style), chevron = true) { actions.open(Panel.SubtitleStyle) }
    ActionRow(Icons.Rounded.Save, stringResource(R.string.save_srt), sub = stringResource(R.string.save_srt_desc)) {
        controller.saveSubtitleAsSrt { file -> actions.toast(if (file != null) savedFormat.format(file.name) else saveFailed) }
    }
}

@Composable
private fun SubtitleStylePanel(controller: PlayerController, actions: PanelActions) {
    val context = LocalContext.current
    val settings by controller.settings.collectAsState()
    val style = settings.subtitleStyle
    var userFonts by remember { mutableStateOf(controller.userFonts()) }
    val added = stringResource(R.string.font_added)
    val invalid = stringResource(R.string.font_invalid)
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            controller.importFont(uri, context.displayName(uri) ?: "font.ttf") { family ->
                if (family != null) {
                    userFonts = controller.userFonts()
                    controller.updateSettings { it.copy(subtitleStyle = it.subtitleStyle.copy(font = family)) }
                    actions.toast(added)
                } else {
                    actions.toast(invalid)
                }
            }
        }
    }
    fun update(change: (SubtitleStyle) -> SubtitleStyle) = controller.updateSettings { it.copy(subtitleStyle = change(it.subtitleStyle)) }

    SectionLabel(stringResource(R.string.font))
    val fonts = listOf(SubtitleFonts.DEFAULT_FAMILY, SubtitleFonts.FLEX_FAMILY, SubtitleFonts.NASKH_FAMILY) + userFonts
    val fontLabels = listOf(stringResource(R.string.font_default), "Google Sans", "Naskh") + userFonts
    ChipGroup(fontLabels, selected = { fonts[it] == style.font }) { i -> update { it.copy(font = fonts[i]) } }
    ActionRow(Icons.Rounded.FontDownload, stringResource(R.string.add_font)) { fontPicker.launch(arrayOf("font/*", "application/x-font-ttf", "application/octet-stream", "*/*")) }

    SliderRow(stringResource(R.string.size), "${(style.scale * 100).roundToInt()}%", style.scale, 0.5f..2.5f, steps = 39) { v ->
        update { it.copy(scale = (v * 20).roundToInt() / 20f) }
    }
    SectionLabel(stringResource(R.string.text_color))
    ColorGroup(TextColors, style.textColor) { c -> update { it.copy(textColor = c) } }

    SectionLabel(stringResource(R.string.outline))
    SliderRow(stringResource(R.string.outline), String.format(Locale.ROOT, "%.1f", style.borderSize), style.borderSize, 0f..6f, steps = 11) { v ->
        update { it.copy(borderSize = (v * 2).roundToInt() / 2f) }
    }
    ColorGroup(OutlineColors, style.borderColor) { c -> update { it.copy(borderColor = c) } }
    SwitchRow(stringResource(R.string.shadow), style.shadow) { on -> update { it.copy(shadow = on) } }

    SwitchRow(stringResource(R.string.background), style.background) { on -> update { it.copy(background = on) } }
    if (style.background) ColorGroup(BackgroundColors, style.backgroundColor) { c -> update { it.copy(backgroundColor = c) } }

    SliderRow(stringResource(R.string.position), "${100 - style.position}", (100 - style.position).toFloat(), 0f..40f, steps = 39) { v ->
        update { it.copy(position = 100 - v.roundToInt()) }
    }
    SwitchRow(stringResource(R.string.bold), style.bold) { on -> update { it.copy(bold = on) } }
    SwitchRow(stringResource(R.string.italic), style.italic) { on -> update { it.copy(italic = on) } }
    SwitchRow(stringResource(R.string.override_ass), style.overrideAss, sub = stringResource(R.string.override_ass_desc)) { on ->
        update { it.copy(overrideAss = on) }
    }
    PanelTextButton(stringResource(R.string.reset)) { update { SubtitleStyle() } }
}

private val TextColors = listOf(0xFFFFFFFF, 0xFFFFEB3B, 0xFFFFC107, 0xFF80DEEA, 0xFFA5D6A7, 0xFFF48FB1, 0xFFB0BEC5, 0xFF000000)
private val OutlineColors = listOf(0xFF000000, 0xFF263238, 0xFF424242, 0xFFFFFFFF, 0xFF0D47A1)
private val BackgroundColors = listOf(0x99000000, 0xCC000000, 0x66000000, 0x99263238, 0x99FFFFFF)

// --- Bookmarks / queue / chapters ---

@Composable
private fun BookmarksPanel(controller: PlayerController) {
    val state by controller.engine.state.collectAsState()
    val bookmarks by controller.bookmarks.collectAsState()
    ActionRow(Icons.Rounded.BookmarkAdd, stringResource(R.string.add_bookmark, formatTime(state.positionSec))) { controller.addBookmark() }
    if (bookmarks.isEmpty()) {
        Text(stringResource(R.string.no_bookmarks), color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(12.dp))
    }
    bookmarks.forEach { bookmark ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { controller.engine.seekTo(bookmark.positionMs / 1000.0) }
                .padding(start = 8.dp),
        ) {
            Icon(Icons.Rounded.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                formatTime(bookmark.positionMs / 1000.0),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            )
            IconButton(onClick = { controller.deleteBookmark(bookmark) }) {
                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.delete), tint = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun QueuePanel(controller: PlayerController) {
    val index by controller.index.collectAsState()
    val repeat by controller.repeat.collectAsState()
    val shuffle by controller.shuffle.collectAsState()
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionChip(
            if (repeat == RepeatMode.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            stringResource(
                when (repeat) {
                    RepeatMode.Off -> R.string.repeat_off
                    RepeatMode.One -> R.string.repeat_one
                    RepeatMode.All -> R.string.repeat_all
                },
            ),
            Modifier.weight(1f), active = repeat != RepeatMode.Off,
        ) { controller.cycleRepeat() }
        ActionChip(Icons.Rounded.Shuffle, stringResource(R.string.shuffle), Modifier.weight(1f), active = shuffle) { controller.toggleShuffle() }
    }
    controller.items.forEachIndexed { i, item ->
        val current = i == index
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(if (current) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
                .clickable { controller.playAt(i) }
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            Text(
                "${i + 1}",
                style = MaterialTheme.typography.labelLarge,
                color = if (current) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f),
                modifier = Modifier.width(32.dp),
            )
            Text(
                item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (current) MaterialTheme.colorScheme.primary else Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ChaptersPanel(controller: PlayerController, actions: PanelActions) {
    val state by controller.engine.state.collectAsState()
    val untitled = stringResource(R.string.chapter_number)
    state.chapters.forEachIndexed { i, chapter ->
        val next = state.chapters.getOrNull(i + 1)?.timeSec ?: Double.MAX_VALUE
        val current = state.positionSec >= chapter.timeSec && state.positionSec < next
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(if (current) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
                .clickable { controller.engine.seekTo(chapter.timeSec); actions.close() }
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            Text(
                chapter.title?.takeIf { it.isNotBlank() } ?: untitled.format(i + 1),
                style = MaterialTheme.typography.bodyLarge,
                color = if (current) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier.weight(1f),
            )
            Text(formatTime(chapter.timeSec), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.7f))
        }
    }
}

// --- Info ---

@Composable
private fun InfoPanel(controller: PlayerController) {
    val context = LocalContext.current
    val state by controller.engine.state.collectAsState()
    val e = controller.engine
    val unknown = stringResource(R.string.unknown)
    fun prop(name: String) = e.property(name)?.takeIf { it.isNotBlank() && it != "no" }
    fun kbps(name: String) = prop(name)?.toDoubleOrNull()?.let { "${(it / 1000).roundToInt()} kb/s" }

    SectionLabel(stringResource(R.string.info_file))
    InfoLine(stringResource(R.string.info_name), controller.currentItem.title)
    InfoLine(stringResource(R.string.info_path), prop("path") ?: controller.currentItem.target)
    InfoLine(stringResource(R.string.info_container), prop("file-format") ?: unknown)
    prop("file-size")?.toLongOrNull()?.let { InfoLine(stringResource(R.string.info_size), formatSize(context, it)) }
    InfoLine(stringResource(R.string.info_duration), formatTime(state.durationSec))

    SectionLabel(stringResource(R.string.info_video))
    InfoLine(stringResource(R.string.info_codec), prop("video-format")?.uppercase() ?: prop("video-codec") ?: unknown)
    InfoLine(stringResource(R.string.info_resolution), if (state.videoWidth > 0) "${state.videoWidth} × ${state.videoHeight}" else unknown)
    prop("container-fps")?.toDoubleOrNull()?.let { InfoLine(stringResource(R.string.info_fps), String.format(Locale.ROOT, "%.3f", it).trimEnd('0').trimEnd('.')) }
    kbps("video-bitrate")?.let { InfoLine(stringResource(R.string.info_bitrate), it) }
    prop("video-params/pixelformat")?.let { InfoLine(stringResource(R.string.info_pixel), it) }
    InfoLine(stringResource(R.string.info_decoder), if (state.isHardware) "HW (${state.hwdecCurrent})" else "SW")

    SectionLabel(stringResource(R.string.info_audio))
    InfoLine(stringResource(R.string.info_codec), prop("audio-codec-name")?.uppercase() ?: unknown)
    prop("audio-params/samplerate")?.let { InfoLine(stringResource(R.string.info_sample), "$it Hz") }
    prop("audio-params/channel-count")?.toIntOrNull()?.let { InfoLine(stringResource(R.string.info_channels), channelLabel(it)) }
    kbps("audio-bitrate")?.let { InfoLine(stringResource(R.string.info_bitrate), it) }

    SectionLabel(stringResource(R.string.info_tracks))
    state.tracks.forEach { track ->
        val type = when (track.type) {
            TrackType.Video -> stringResource(R.string.info_video)
            TrackType.Audio -> stringResource(R.string.info_audio)
            TrackType.Subtitle -> stringResource(R.string.panel_subtitles)
        }
        InfoLine("$type ${track.id}", listOfNotNull(track.lang?.let(::languageName), track.title, track.codec?.uppercase()).joinToString(" · ").ifEmpty { "—" })
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.6f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Color.White)
    }
}

// --- Sleep / aspect / stereo ---

@Composable
private fun SleepPanel(controller: PlayerController, actions: PanelActions) {
    val sleepAt by controller.sleepAt.collectAsState()
    val atEnd by controller.sleepAtEnd.collectAsState()
    val minutesFormat = stringResource(R.string.sleep_minutes)
    if (sleepAt != null) {
        val left = ((sleepAt!! - System.currentTimeMillis()) / 1000.0).coerceAtLeast(0.0)
        Text(
            stringResource(R.string.sleep_remaining, formatTime(left)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(8.dp),
        )
    }
    RadioRow(stringResource(R.string.off), selected = sleepAt == null && !atEnd) { controller.setSleepTimer(null); actions.close() }
    listOf(15, 30, 45, 60, 90, 120).forEach { minutes ->
        RadioRow(minutesFormat.format(minutes), selected = false) { controller.setSleepTimer(minutes); actions.close() }
    }
    RadioRow(stringResource(R.string.sleep_end), selected = atEnd) { controller.setSleepAtEndOfVideo(); actions.close() }
}

@Composable
private fun CustomAspectPanel(controller: PlayerController, actions: PanelActions) {
    val current by controller.customAspect.collectAsState()
    val aspect by controller.aspect.collectAsState()
    val presets = listOf("21:9", "2.39:1", "2.35:1", "1.85:1", "18:9", "3:2", "5:4", "1:1")
    ChipGroup(presets, selected = { aspect == AspectMode.Custom && presets[it] == current }) { i -> controller.setCustomAspect(presets[i]) }
    var width by remember { mutableStateOf(current.substringBefore(':')) }
    var height by remember { mutableStateOf(current.substringAfter(':', "1")) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
        NumberField(width, stringResource(R.string.width), Modifier.weight(1f)) { width = it }
        Text(":", color = Color.White, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 8.dp))
        NumberField(height, stringResource(R.string.height), Modifier.weight(1f)) { height = it }
    }
    PanelTextButton(stringResource(R.string.apply)) {
        val w = width.toDoubleOrNull()
        val h = height.toDoubleOrNull()
        if (w != null && h != null && w > 0 && h > 0) {
            controller.setCustomAspect("$width:$height")
            actions.close()
        }
    }
}

@Composable
private fun NumberField(value: String, label: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { new -> onChange(new.filter { it.isDigit() || it == '.' }.take(6)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@Composable
private fun StereoPanel(controller: PlayerController) {
    val stereo by controller.stereo.collectAsState()
    RadioRow(stringResource(R.string.stereo_off), selected = stereo == StereoMode.Off) { controller.setStereo(StereoMode.Off) }
    RadioRow(stringResource(R.string.stereo_sbs), selected = stereo == StereoMode.SideBySide) { controller.setStereo(StereoMode.SideBySide) }
    RadioRow(stringResource(R.string.stereo_tb), selected = stereo == StereoMode.TopBottom) { controller.setStereo(StereoMode.TopBottom) }
}

// --- Player settings (the "Tuner") ---

@Composable
private fun SettingsPanel(controller: PlayerController) {
    val settings by controller.settings.collectAsState()
    fun update(change: (PlayerSettings) -> PlayerSettings) = controller.updateSettings(change)
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    SectionLabel(stringResource(R.string.gestures))
    SwitchRow(stringResource(R.string.g_seek), settings.gestureSeek) { on -> update { it.copy(gestureSeek = on) } }
    SwitchRow(stringResource(R.string.g_brightness), settings.gestureBrightness) { on -> update { it.copy(gestureBrightness = on) } }
    SwitchRow(stringResource(R.string.g_volume), settings.gestureVolume) { on -> update { it.copy(gestureVolume = on) } }
    SwitchRow(stringResource(R.string.g_zoom), settings.gestureZoom) { on -> update { it.copy(gestureZoom = on) } }
    SwitchRow(stringResource(R.string.g_doubletap), settings.gestureDoubleTap) { on -> update { it.copy(gestureDoubleTap = on) } }
    SwitchRow(stringResource(R.string.g_longpress), settings.gestureLongPress) { on -> update { it.copy(gestureLongPress = on) } }

    SectionLabel(stringResource(R.string.doubletap_seconds))
    val seconds = listOf(5, 10, 15, 20, 30)
    val secondsFormat = stringResource(R.string.player_seconds)
    ChipGroup(seconds.map { secondsFormat.format(it) }, selected = { seconds[it] == settings.doubleTapSeconds }) { i ->
        update { it.copy(doubleTapSeconds = seconds[i]) }
    }
    SectionLabel(stringResource(R.string.longpress_speed))
    val holdSpeeds = listOf(1.5f, 2f, 3f)
    ChipGroup(holdSpeeds.map { speedLabel(it.toDouble()) }, selected = { holdSpeeds[it] == settings.longPressSpeed }) { i ->
        update { it.copy(longPressSpeed = holdSpeeds[i]) }
    }

    SectionLabel(stringResource(R.string.playback))
    val resumeModes = ResumeMode.entries
    Text(stringResource(R.string.resume_mode), color = Color.White, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp, top = 4.dp))
    ChipGroup(
        resumeModes.map {
            stringResource(
                when (it) {
                    ResumeMode.Auto -> R.string.resume_auto
                    ResumeMode.Ask -> R.string.resume_ask
                    ResumeMode.Never -> R.string.resume_never
                },
            )
        },
        selected = { resumeModes[it] == settings.resumeMode },
    ) { i -> update { it.copy(resumeMode = resumeModes[i]) } }
    SwitchRow(stringResource(R.string.auto_next), settings.autoPlayNext, sub = stringResource(R.string.auto_next_desc)) { on -> update { it.copy(autoPlayNext = on) } }
    SwitchRow(stringResource(R.string.volume_boost), settings.volumeBoost, sub = stringResource(R.string.volume_boost_desc)) { on ->
        update { it.copy(volumeBoost = on) }
        if (!on && controller.engine.state.value.volume > 100) controller.engine.setVolume(100.0)
    }
    SwitchRow(stringResource(R.string.background_play), settings.backgroundPlay, sub = stringResource(R.string.background_play_desc)) { on ->
        if (on && Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        update { it.copy(backgroundPlay = on) }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        SwitchRow(stringResource(R.string.auto_pip), settings.autoPip, sub = stringResource(R.string.auto_pip_desc)) { on -> update { it.copy(autoPip = on) } }
    }

    SectionLabel(stringResource(R.string.display))
    SwitchRow(stringResource(R.string.show_clock), settings.showClock) { on -> update { it.copy(showClock = on) } }

    SectionLabel(stringResource(R.string.sub_language))
    val languages = listOf("", "ar", "en", "fr", "es", "tr")
    val noneLabel = stringResource(R.string.lang_none)
    ChipGroup(languages.map { if (it.isEmpty()) noneLabel else languageName(it) }, selected = { languages[it] == settings.subLanguage }) { i ->
        update { it.copy(subLanguage = languages[i]) }
    }
    Spacer(Modifier.height(8.dp))
}

// --- Small helpers ---

@Composable
private fun ActionChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, active: Boolean = false, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (active) MaterialTheme.colorScheme.primary else Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun trackTitle(track: Track, index: Int): String {
    // mpv guesses a "language" from bracketed parts of file names (e.g. "[aqz-…]"); only show real ones.
    val lang = track.lang?.let(::languageName)?.takeIf { !it.equals(track.lang, ignoreCase = true) }
    val title = if (track.external && track.externalFile != null) track.externalFile.substringAfterLast('/')
    else track.title?.takeIf { it.isNotBlank() }
    return listOfNotNull(title, lang).distinct().joinToString(" · ")
        .ifEmpty { stringResource(R.string.track_default, index + 1) }
}

@Composable
private fun trackDetails(track: Track): String? {
    val parts = mutableListOf<String>()
    track.codec?.let { parts += it.uppercase() }
    if (track.type == TrackType.Audio && track.channels > 0) parts += channelLabel(track.channels)
    if (track.external) parts += stringResource(R.string.external)
    return parts.joinToString(" · ").ifEmpty { null }
}

@Composable
private fun channelLabel(count: Int): String = when (count) {
    1 -> stringResource(R.string.channels_mono)
    2 -> stringResource(R.string.channels_stereo)
    6 -> "5.1"
    8 -> "7.1"
    else -> count.toString()
}

@Composable
fun presetName(key: String): String = stringResource(
    when (key) {
        "flat" -> R.string.preset_flat
        "bass" -> R.string.preset_bass
        "rock" -> R.string.preset_rock
        "pop" -> R.string.preset_pop
        "jazz" -> R.string.preset_jazz
        "classical" -> R.string.preset_classical
        "vocal" -> R.string.preset_vocal
        "cinematic" -> R.string.preset_cinematic
        "clarity" -> R.string.preset_clarity
        "music" -> R.string.preset_music
        "night" -> R.string.preset_night
        else -> R.string.preset_custom
    },
)

@Composable
private fun codepageName(cp: String): String = when (cp) {
    "auto" -> stringResource(R.string.enc_auto)
    "utf8" -> "UTF-8"
    "cp1256" -> stringResource(R.string.enc_arabic) + " (Windows-1256)"
    "iso-8859-6" -> stringResource(R.string.enc_arabic) + " (ISO-8859-6)"
    "cp1252" -> stringResource(R.string.enc_western)
    "cp1251" -> stringResource(R.string.enc_cyrillic)
    "cp1254" -> stringResource(R.string.enc_turkish)
    "cp1255" -> stringResource(R.string.enc_hebrew)
    "cp1253" -> stringResource(R.string.enc_greek)
    "gbk" -> stringResource(R.string.enc_chinese_s)
    "big5" -> stringResource(R.string.enc_chinese_t)
    "shift_jis" -> stringResource(R.string.enc_japanese)
    "euc-kr" -> stringResource(R.string.enc_korean)
    else -> cp
}

private fun abs(value: Double) = kotlin.math.abs(value)

fun speedLabel(speed: Double): String = String.format(Locale.ROOT, "%.2f", speed).trimEnd('0').trimEnd('.') + "×"

private fun delayLabel(seconds: Double): String = String.format(Locale.ROOT, "%+.2f s", seconds)

private fun dbLabel(db: Float): String = String.format(Locale.ROOT, "%+.0f dB", db)

private fun bandLabel(hz: Int): String = if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz"

fun formatTime(seconds: Double): String = formatDuration((seconds * 1000).toLong()).ifEmpty { "0:00" }

/** "ara" / "ar" → "العربية" / "Arabic", in the app's language. */
fun languageName(code: String): String {
    val locale = Locale.forLanguageTag(code).takeIf { it.language.isNotEmpty() } ?: Locale(code)
    val name = locale.getDisplayLanguage(Locale.getDefault())
    return if (name.isBlank() || name.equals(code, ignoreCase = true)) code else name.replaceFirstChar { it.titlecase(Locale.getDefault()) }
}

fun Context.displayName(uri: Uri): String? = runCatching {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull()

fun Context.showToast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
