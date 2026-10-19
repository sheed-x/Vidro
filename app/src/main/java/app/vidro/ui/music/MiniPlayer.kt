package app.vidro.ui.music

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.vidro.R
import app.vidro.player.PlayerController
import app.vidro.ui.components.glass
import dev.chrisbanes.haze.HazeState

val MiniPlayerHeight = 64.dp

/**
 * The current session in a glass pill above the tab bar: cover, title, play/pause and next, with a
 * hairline of progress. Tap opens it; swipe up opens it too.
 */
@Composable
fun MiniPlayer(controller: PlayerController, hazeState: HazeState, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val state by controller.engine.state.collectAsState()
    val index by controller.index.collectAsState()
    val queue by controller.queue.collectAsState()
    val artwork by controller.artwork.collectAsState()
    val item = queue.getOrNull(index) ?: return
    val art = remember(artwork) { artwork?.asImageBitmap() }
    val shape = RoundedCornerShape(24.dp)
    val progress = if (state.durationSec > 0) (state.positionSec / state.durationSec).toFloat().coerceIn(0f, 1f) else 0f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(MiniPlayerHeight)
            .shadow(16.dp, shape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.16f))
            .glass(hazeState, shape)
            .clickable(onClick = onOpen)
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, drag ->
                    if (drag < -12f) {
                        change.consume()
                        onOpen()
                    }
                }
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize().padding(start = 8.dp, end = 4.dp)) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceContainerHighest))),
            ) {
                if (art != null) {
                    Image(art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.Center).size(22.dp),
                    )
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(item.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    item.artist ?: stringResource(if (item.isAudio) R.string.unknown_artist else R.string.playing_video_as_audio),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { controller.engine.togglePause() }) {
                Icon(
                    if (state.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    contentDescription = stringResource(if (state.paused) R.string.player_play else R.string.player_pause),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(30.dp),
                )
            }
            IconButton(onClick = { controller.next() }, enabled = controller.hasNext) {
                Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.player_next), modifier = Modifier.size(28.dp))
            }
        }
        // Progress hairline along the bottom edge, inside the rounded corners.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 22.dp)
                .fillMaxWidth()
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
        ) {
            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
        }
    }
}
