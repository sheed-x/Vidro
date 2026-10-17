package app.vidro.ui.welcome

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.vidro.R
import app.vidro.data.StorageAccess

@Composable
fun WelcomeScreen(onAccessChanged: () -> Unit) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { onAccessChanged() }

    val requestFullAccess = {
        if (StorageAccess.usesAllFilesSettings) {
            StorageAccess.openAllFilesSettings(context)
        } else {
            permissionLauncher.launch(StorageAccess.videoOnlyPermissions())
        }
    }

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Spacer(Modifier.height(40.dp))
            AppLogo()
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.welcome_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 320.dp),
            )
            Spacer(Modifier.height(32.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.padding(20.dp),
                ) {
                    Feature(Icons.Rounded.Movie, R.string.welcome_formats_title, R.string.welcome_formats_body)
                    Feature(Icons.Rounded.CloudOff, R.string.welcome_offline_title, R.string.welcome_offline_body)
                    Feature(Icons.Rounded.Block, R.string.welcome_noads_title, R.string.welcome_noads_body)
                }
            }

            Spacer(Modifier.height(32.dp))

            Text(
                text = stringResource(R.string.welcome_access_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 360.dp),
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = requestFullAccess,
                modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp).height(56.dp),
            ) {
                Text(
                    stringResource(
                        if (StorageAccess.usesAllFilesSettings) R.string.welcome_allow_all else R.string.welcome_allow,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (StorageAccess.usesAllFilesSettings) {
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { permissionLauncher.launch(StorageAccess.videoOnlyPermissions()) }) {
                    Text(stringResource(R.string.welcome_allow_videos))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** The launcher icon, drawn in-app (adaptive icons can't be loaded as a Compose painter). */
@Composable
fun AppLogo(size: Int = 96) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * 0.3f).dp))
            // Matches the launcher icon: white tile with a hairline edge so it reads on white pages too.
            .background(Color.White)
            .border(1.dp, Color(0xFFDDE3EC), RoundedCornerShape((size * 0.3f).dp)),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.requiredSize((size * 1.5f).dp),
        )
    }
}

@Composable
private fun Feature(icon: ImageVector, title: Int, body: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Column(Modifier.padding(start = 16.dp)) {
            Text(
                stringResource(title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
