package app.vidro.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.vidro.R
import app.vidro.data.UpdateRepository
import app.vidro.data.UpdateState

/**
 * The update flow as one dialog: what's new → download progress → install. Shown whenever the
 * update repository has something for the user (the silent launch check only shows "Available").
 */
@Composable
fun UpdateDialog(updates: UpdateRepository) {
    val context = LocalContext.current
    val state by updates.state.collectAsState()
    // Coming back from the "allow installs" settings page: install straight away.
    LifecycleResumeEffect(state) {
        val ready = state as? UpdateState.ReadyToInstall
        if (ready != null && updates.canInstall()) updates.install(ready.file)
        onPauseOrDispose { }
    }

    when (val s = state) {
        is UpdateState.Available -> AlertDialog(
            onDismissRequest = updates::dismiss,
            title = { Text(stringResource(R.string.update_available, s.update.version)) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    if (s.update.title.isNotBlank()) {
                        Text(s.update.title, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        s.update.notes.ifBlank { stringResource(R.string.update_no_notes) },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (s.update.sizeBytes > 0) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.update_size, formatSize(context, s.update.sizeBytes)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { updates.download(s.update) }) { Text(stringResource(R.string.update_install)) } },
            dismissButton = { TextButton(onClick = updates::dismiss) { Text(stringResource(R.string.update_later)) } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        )

        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.update_downloading, s.update.version)) },
            text = {
                Column {
                    LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("${(s.progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = updates::dismiss) { Text(stringResource(android.R.string.cancel)) } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        )

        is UpdateState.ReadyToInstall -> AlertDialog(
            onDismissRequest = updates::dismiss,
            title = { Text(stringResource(R.string.update_ready, s.update.version)) },
            text = {
                Text(
                    stringResource(if (updates.canInstall()) R.string.update_ready_body else R.string.update_permission_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (updates.canInstall()) updates.install(s.file) else updates.openInstallPermission()
                }) {
                    Text(stringResource(if (updates.canInstall()) R.string.update_install_now else R.string.update_allow))
                }
            },
            dismissButton = { TextButton(onClick = updates::dismiss) { Text(stringResource(R.string.update_later)) } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        )

        is UpdateState.UpToDate -> AlertDialog(
            onDismissRequest = updates::dismiss,
            title = { Text(stringResource(R.string.update_up_to_date)) },
            text = { Text(stringResource(R.string.update_up_to_date_body, updates.currentVersion())) },
            confirmButton = { TextButton(onClick = updates::dismiss) { Text(stringResource(android.R.string.ok)) } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        )

        is UpdateState.Failed -> AlertDialog(
            onDismissRequest = updates::dismiss,
            title = { Text(stringResource(R.string.update_failed)) },
            text = { Text(stringResource(R.string.update_failed_body)) },
            confirmButton = { TextButton(onClick = updates::dismiss) { Text(stringResource(android.R.string.ok)) } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        )

        UpdateState.Checking, UpdateState.Idle -> Unit
    }
}
