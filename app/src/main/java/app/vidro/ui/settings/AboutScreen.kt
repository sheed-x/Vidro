package app.vidro.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon as M3Icon
import app.vidro.R
import app.vidro.data.UpdateRepository
import app.vidro.data.UpdateState
import app.vidro.ui.components.Group
import app.vidro.ui.components.GroupRow
import app.vidro.ui.library.LibraryViewModel
import app.vidro.ui.library.LocalBottomBarSpace
import app.vidro.ui.library.VidroTopBar
import app.vidro.ui.welcome.AppLogo

/** Who made Vidro, what it can do, where its code lives, and the update controls. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(viewModel: LibraryViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val updateState by viewModel.updates.state.collectAsState()
    var licenses by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val version = remember { viewModel.updates.currentVersion() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            VidroTopBar(
                title = stringResource(R.string.about_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        M3Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = LocalBottomBarSpace.current + 24.dp),
        ) {
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(vertical = 28.dp, horizontal = 24.dp),
                ) {
                    AppLogo(size = 88)
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        stringResource(R.string.about_version, version),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.about_tagline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            item {
                Group(
                    title = stringResource(R.string.about_developer),
                    rows = listOf(
                        {
                            GroupRow(
                                title = "sheed-x",
                                summary = stringResource(R.string.about_developer_desc),
                                icon = Icons.Rounded.Person,
                                onClick = { context.openUrl(UpdateRepository.PROFILE_URL) },
                                trailing = { OpenIcon() },
                            )
                        },
                        {
                            GroupRow(
                                title = stringResource(R.string.about_source),
                                summary = "github.com/sheed-x/Vidro",
                                icon = Icons.Rounded.Code,
                                onClick = { context.openUrl(UpdateRepository.REPO_URL) },
                                trailing = { OpenIcon() },
                            )
                        },
                        {
                            GroupRow(
                                title = stringResource(R.string.about_report),
                                summary = stringResource(R.string.about_report_desc),
                                icon = Icons.Rounded.BugReport,
                                onClick = { context.openUrl(UpdateRepository.REPO_URL + "/issues") },
                                trailing = { OpenIcon() },
                            )
                        },
                    ),
                )
            }

            item {
                Group(
                    title = stringResource(R.string.about_updates),
                    rows = listOf(
                        {
                            GroupRow(
                                title = stringResource(R.string.check_updates_auto),
                                summary = stringResource(R.string.check_updates_auto_desc),
                                icon = Icons.Rounded.Update,
                                onClick = { viewModel.setCheckUpdates(!settings.checkUpdates) },
                                trailing = { Switch(checked = settings.checkUpdates, onCheckedChange = { viewModel.setCheckUpdates(it) }) },
                            )
                        },
                        {
                            GroupRow(
                                title = stringResource(R.string.check_updates_now),
                                summary = stringResource(R.string.about_version, version),
                                icon = Icons.Rounded.SystemUpdate,
                                onClick = { viewModel.updates.check() },
                                trailing = {
                                    if (updateState is UpdateState.Checking) {
                                        CircularProgressIndicator(Modifier.padding(4.dp).height(22.dp), strokeWidth = 2.dp)
                                    } else {
                                        Chevron()
                                    }
                                },
                            )
                        },
                    ),
                )
            }

            item {
                Group(
                    title = stringResource(R.string.about_supports),
                    rows = listOf(
                        { Feature(Icons.Rounded.Movie, R.string.sup_formats, R.string.sup_formats_desc) },
                        { Feature(Icons.Rounded.Memory, R.string.sup_decoding, R.string.sup_decoding_desc) },
                        { Feature(Icons.Rounded.Subtitles, R.string.sup_subtitles, R.string.sup_subtitles_desc) },
                        { Feature(Icons.Rounded.Equalizer, R.string.sup_audio, R.string.sup_audio_desc) },
                        { Feature(Icons.Rounded.Swipe, R.string.sup_gestures, R.string.sup_gestures_desc) },
                        { Feature(Icons.Rounded.PictureInPictureAlt, R.string.sup_pip, R.string.sup_pip_desc) },
                        { Feature(Icons.Rounded.Language, R.string.sup_languages, R.string.sup_languages_desc) },
                    ),
                )
            }

            item {
                Group(
                    title = null,
                    rows = listOf {
                        GroupRow(
                            title = stringResource(R.string.licenses),
                            summary = stringResource(R.string.licenses_desc),
                            icon = Icons.Rounded.Gavel,
                            onClick = { licenses = true },
                            trailing = { Chevron() },
                        )
                    },
                )
            }

            item {
                Text(
                    stringResource(R.string.about_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            }
        }
    }

    if (licenses) {
        AlertDialog(
            onDismissRequest = { licenses = false },
            title = { Text(stringResource(R.string.licenses)) },
            text = {
                Text(LICENSES, style = MaterialTheme.typography.bodySmall, modifier = Modifier.verticalScroll(rememberScrollState()))
            },
            confirmButton = { TextButton(onClick = { licenses = false }) { Text(stringResource(android.R.string.ok)) } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        )
    }
}

@Composable
private fun Feature(icon: androidx.compose.ui.graphics.vector.ImageVector, title: Int, summary: Int) {
    GroupRow(title = stringResource(title), summary = stringResource(summary), icon = icon)
}

@Composable
private fun Chevron() {
    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun OpenIcon() {
    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Links open in the browser; the app itself never loads web pages. */
private fun Context.openUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
    }
}
