package app.vidro.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.vidro.R
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Timeline used by the video player and the music screen.
 *
 * The thumb belongs to the finger while dragging and never fights the playback position: grabbing
 * near the thumb doesn't make it jump, moving the finger up away from the bar slows the scrub down
 * (half, then quarter speed) for precise positioning, and a time bubble shows where you'll land.
 * Seconds are always left to right, like the video itself.
 */
@Composable
fun SeekBar(
    position: Double,
    duration: Double,
    onScrubStart: () -> Unit,
    onScrub: (Double) -> Unit,
    /** Where the finger let go, and whether it was fine scrubbing (so the landing should be exact). */
    onScrubEnd: (Double, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = Color.White,
    inactiveColor: Color = Color.White.copy(alpha = 0.28f),
    bubbleColor: Color = Color.Black.copy(alpha = 0.72f),
    bubbleTextColor: Color = Color.White,
    /** Small dots under the track, e.g. chapters. */
    markers: List<Double> = emptyList(),
    /** Accent dots, e.g. bookmarks. */
    highlights: List<Double> = emptyList(),
    highlightColor: Color = MaterialTheme.colorScheme.primary,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val currentPosition by rememberUpdatedState(position)
    val currentDuration by rememberUpdatedState(duration)
    val start by rememberUpdatedState(onScrubStart)
    val scrub by rememberUpdatedState(onScrub)
    val end by rememberUpdatedState(onScrubEnd)

    var dragValue by remember { mutableStateOf<Double?>(null) }
    var fineFactor by remember { mutableFloatStateOf(1f) }
    var bubbleWidth by remember { mutableIntStateOf(0) }
    var barWidth by remember { mutableIntStateOf(0) }
    val dragging = dragValue != null
    val total = duration.coerceAtLeast(0.0)
    val shown = (dragValue ?: position).coerceIn(0.0, total.coerceAtLeast(0.0))
    val fraction = if (total > 0) (shown / total).toFloat() else 0f

    val trackHeight by animateDpAsState(if (dragging) 8.dp else 4.dp, spring(stiffness = 900f), label = "track")
    val thumbRadius by animateDpAsState(if (dragging) 10.dp else 7.dp, spring(dampingRatio = 0.6f, stiffness = 700f), label = "thumb")
    val sidePadding = 12.dp

    val fineHalf = stringResource(R.string.scrub_half)
    val fineQuarter = stringResource(R.string.scrub_fine)

    // The timeline always runs left to right, also in right-to-left languages.
    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(36.dp)
                .onSizeChanged { barWidth = it.width }
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(shown.toFloat(), 0f..total.toFloat().coerceAtLeast(1f))
                    setProgress { value ->
                        end(value.toDouble().coerceIn(0.0, total), true)
                        true
                    }
                }
                .pointerInput(Unit) {
                    val pad = sidePadding.toPx()
                    val grabRadius = 28.dp.toPx()
                    val halfZone = 56.dp.toPx()
                    val quarterZone = 120.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val length = currentDuration
                        if (length <= 0) return@awaitEachGesture
                        val width = (size.width - 2 * pad).coerceAtLeast(1f)
                        val startValue = currentPosition.coerceIn(0.0, length)
                        val thumbX = pad + (startValue / length).toFloat() * width
                        // Grabbing the thumb keeps it where it is; touching elsewhere jumps there first.
                        var value = if (abs(down.position.x - thumbX) <= grabRadius) startValue
                        else (((down.position.x - pad) / width).coerceIn(0f, 1f) * length)
                        var lastX = down.position.x
                        down.consume()
                        start()
                        dragValue = value
                        fineFactor = 1f
                        scrub(value)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            val dx = change.position.x - lastX
                            lastX = change.position.x
                            val away = abs(change.position.y - size.height / 2f)
                            val factor = when {
                                away > quarterZone -> 0.25f
                                away > halfZone -> 0.5f
                                else -> 1f
                            }
                            if (factor != fineFactor) {
                                fineFactor = factor
                                if (LocalHapticsEnabled.value) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            }
                            if (dx != 0f) {
                                value = (value + dx / width * length * factor).coerceIn(0.0, length)
                                dragValue = value
                                scrub(value)
                            }
                            change.consume()
                        }
                        val fine = fineFactor < 1f
                        dragValue = null
                        fineFactor = 1f
                        end(value, fine)
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize().padding(horizontal = sidePadding)) {
                val h = trackHeight.toPx()
                val y = size.height / 2f
                val radius = CornerRadius(h / 2, h / 2)
                drawRoundRect(inactiveColor, Offset(0f, y - h / 2), Size(size.width, h), radius)
                val activeWidth = size.width * fraction
                if (activeWidth > 0f) drawRoundRect(activeColor, Offset(0f, y - h / 2), Size(activeWidth, h), radius)
                if (total > 0) {
                    markers.forEach { t ->
                        drawCircle(activeColor.copy(alpha = 0.7f), 2.dp.toPx(), Offset((t / total).toFloat() * size.width, y + h / 2 + 5.dp.toPx()))
                    }
                    highlights.forEach { t ->
                        drawCircle(highlightColor, 3.dp.toPx(), Offset((t / total).toFloat() * size.width, y + h / 2 + 5.dp.toPx()))
                    }
                }
                drawCircle(activeColor, thumbRadius.toPx(), Offset(activeWidth, y))
            }

            // Time bubble above the thumb while dragging.
            AnimatedVisibility(
                visible = dragging,
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut(targetScale = 0.8f),
                modifier = Modifier.align(Alignment.TopStart).offset {
                    val pad = with(density) { sidePadding.toPx() }
                    val x = pad + (barWidth - 2 * pad) * fraction - bubbleWidth / 2f
                    val maxX = (barWidth - bubbleWidth).coerceAtLeast(0).toFloat()
                    IntOffset(x.coerceIn(0f, maxX).roundToInt(), -with(density) { 44.dp.roundToPx() })
                },
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .onSizeChanged { bubbleWidth = it.width }
                        .clip(RoundedCornerShape(14.dp))
                        .background(bubbleColor)
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                ) {
                    Text(formatDuration((shown * 1000).toLong()).ifEmpty { "0:00" }, style = MaterialTheme.typography.labelLarge, color = bubbleTextColor)
                    if (fineFactor < 1f) {
                        Text(
                            if (fineFactor < 0.5f) fineQuarter else fineHalf,
                            style = MaterialTheme.typography.labelSmall,
                            color = bubbleTextColor.copy(alpha = 0.75f),
                        )
                    }
                }
            }
        }
    }
}
