package app.vidro.ui.library

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.vidro.R
import app.vidro.ui.components.CounterIntro
import app.vidro.ui.components.SpinStat
import app.vidro.ui.components.splitSize
import app.vidro.ui.theme.LocalIsDark
import kotlinx.coroutines.delay

/** The "other side" of a counter, revealed by tapping it. */
data class StatBack(val value: String, val label: String)

/**
 * Home counters. Each spins (drag up/down) and flips when tapped to show a related figure.
 * With motion effects on, a soft glass sheen drifts across the card as the phone tilts.
 */
@Composable
fun StatsCard(
    videos: Int,
    folders: Int,
    plays: Int,
    storage: String,
    backs: List<StatBack>,
    haptics: Boolean,
    motion: Boolean,
) {
    // Spin up from zero once per launch; afterwards the wheels just roll to new values.
    val intro = remember { !CounterIntro.played }
    LaunchedEffect(Unit) { CounterIntro.played = true }
    val sizeParts = splitSize(storage)
    // Entrance: the card rises into place; once the digits settle, one band of light sweeps across.
    val appear = remember { Animatable(if (intro) 0f else 1f) }
    val sweep = remember { Animatable(if (intro) -0.6f else 2f) }
    LaunchedEffect(Unit) {
        if (!intro) return@LaunchedEffect
        appear.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        if (!intro) return@LaunchedEffect
        delay(1800)
        sweep.animateTo(1.6f, tween(1100, easing = FastOutSlowInEasing))
    }
    val tilt by rememberTilt(motion)
    val dark = LocalIsDark.current
    val sheen = if (dark) Color.White.copy(alpha = 0.07f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .graphicsLayer {
                alpha = appear.value
                translationY = (1f - appear.value) * 28.dp.toPx()
                // A couple of degrees at most: felt more than seen.
                rotationY = tilt.x * 2.5f
                rotationX = -tilt.y * 2.5f
                cameraDistance = 16 * density
            }
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .drawWithContent {
                drawContent()
                if (sweep.value in -0.5f..1.5f) {
                    val x = size.width * sweep.value
                    val band = size.width * 0.22f
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.Transparent, sheen.copy(alpha = sheen.alpha * 2.2f), Color.Transparent),
                            start = Offset(x - band, 0f),
                            end = Offset(x + band, size.height),
                        ),
                    )
                }
                if (motion) {
                    // A diagonal band of light that slides with the tilt.
                    val center = size.width * (0.5f + tilt.x * 0.45f)
                    val band = size.width * 0.35f
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.Transparent, sheen, Color.Transparent),
                            start = Offset(center - band, size.height * (0.5f + tilt.y * 0.5f) - band),
                            end = Offset(center + band, size.height * (0.5f + tilt.y * 0.5f) + band),
                        ),
                    )
                }
            }
            .padding(vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlipStat(backs.getOrNull(0), haptics) {
            SpinStat(videos.toDouble(), 0, "", stringResource(R.string.stat_videos), Modifier, intro, 0, haptics)
        }
        StatDivider()
        FlipStat(backs.getOrNull(1), haptics) {
            SpinStat(folders.toDouble(), 0, "", stringResource(R.string.stat_folders), Modifier, intro, 120, haptics)
        }
        StatDivider()
        FlipStat(backs.getOrNull(2), haptics) {
            SpinStat(plays.toDouble(), 0, "", stringResource(R.string.stat_plays), Modifier, intro, 240, haptics)
        }
        StatDivider()
        FlipStat(backs.getOrNull(3), haptics) {
            SpinStat(
                sizeParts.value, sizeParts.decimals, sizeParts.unit, stringResource(R.string.stat_storage), Modifier,
                intro, 360, haptics, decimalSeparator = sizeParts.separator,
            )
        }
    }
}

/** Tap to turn the counter over (3D flip); it turns back by itself after a few seconds. */
@Composable
private fun RowScope.FlipStat(back: StatBack?, haptics: Boolean, front: @Composable () -> Unit) {
    val hapticFeedback = LocalHapticFeedback.current
    var flipped by remember { mutableStateOf(false) }
    val angle by animateFloatAsState(if (flipped) 180f else 0f, tween(450), label = "flip")
    LaunchedEffect(flipped) {
        if (flipped) {
            delay(4000)
            flipped = false
        }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .weight(1f)
            .pointerInput(back) {
                detectTapGestures {
                    if (back == null) return@detectTapGestures
                    flipped = !flipped
                    if (haptics) hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                }
            }
            .graphicsLayer {
                rotationY = angle
                cameraDistance = 12 * density
            },
    ) {
        if (angle <= 90f || back == null) {
            front()
        } else {
            // Counter-rotate so the back reads normally rather than mirrored.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer { rotationY = 180f },
            ) {
                Box(Modifier.height(36.dp), contentAlignment = Alignment.Center) {
                    Text(
                        back.value,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
                Text(
                    back.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Hairline between counters, short enough to read as a separator rather than a box. */
@Composable
private fun StatDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(28.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
    )
}

/**
 * Device tilt relative to how the phone was held when Home appeared, each axis in -1..1 and
 * smoothed. Uses the gravity sensor only while [enabled] and on screen.
 */
@Composable
private fun rememberTilt(enabled: Boolean): State<Offset> {
    val context = LocalContext.current
    val tilt = remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(enabled) {
        if (!enabled) {
            tilt.value = Offset.Zero
            return@DisposableEffect onDispose { }
        }
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = manager.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var baseline: Offset? = null
        var smoothed = Offset.Zero
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val raw = Offset(-event.values[0], event.values[1])
                val base = baseline ?: raw.also { baseline = it }
                val target = Offset(
                    ((raw.x - base.x) / 4f).coerceIn(-1f, 1f),
                    ((raw.y - base.y) / 4f).coerceIn(-1f, 1f),
                )
                smoothed += (target - smoothed) * 0.15f
                tilt.value = smoothed
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { manager.unregisterListener(listener) }
    }
    return tilt
}
