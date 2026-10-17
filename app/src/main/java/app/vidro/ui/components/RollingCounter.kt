package app.vidro.ui.components

import android.view.SoundEffectConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.lerp
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.floor
import kotlin.math.pow

/** The opening "spin up from zero" plays once per app launch, not every time Home is shown. */
object CounterIntro {
    var played = false
}

/**
 * A number shown on rolling digit wheels, like the iOS timer picker. Each digit wheel animates to
 * its new digit in the direction the value moved; on first show the wheels spin up from zero.
 */
@Composable
fun RollingNumber(
    text: String,
    style: TextStyle,
    color: Color,
    intro: Boolean,
    introDelayMs: Int,
    direction: Int,
    expanded: Float = 0f,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        text.forEachIndexed { i, ch ->
            // Keyed from the right so the ones wheel stays the same wheel when the number grows.
            key(text.length - i) {
                if (ch.isDigit()) {
                    DigitWheel(
                        digit = ch.digitToInt(),
                        style = style,
                        color = color,
                        intro = intro,
                        // Higher places settle a little later, like a slot machine.
                        introDelayMs = introDelayMs + (text.length - i) * 70,
                        direction = direction,
                        expanded = expanded,
                    )
                } else {
                    Text(ch.toString(), style = style, color = color)
                }
            }
        }
    }
}

@Composable
private fun DigitWheel(
    digit: Int,
    style: TextStyle,
    color: Color,
    intro: Boolean,
    introDelayMs: Int,
    direction: Int,
    expanded: Float,
) {
    val position = remember { Animatable(if (intro) 0f else digit.toFloat()) }
    var first by remember { mutableStateOf(true) }

    LaunchedEffect(digit) {
        if (first) {
            first = false
            if (intro) {
                delay(introDelayMs.toLong())
                position.animateTo(digit + 20f, tween(1300, easing = FastOutSlowInEasing))
            }
            return@LaunchedEffect
        }
        val current = Math.round(position.value)
        val currentDigit = Math.floorMod(current, 10)
        val step = when {
            direction > 0 -> Math.floorMod(digit - currentDigit, 10)
            direction < 0 -> -Math.floorMod(currentDigit - digit, 10)
            else -> {
                val forward = Math.floorMod(digit - currentDigit, 10)
                if (forward <= 5) forward else forward - 10
            }
        }
        position.animateTo((current + step).toFloat(), spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow))
    }

    val p = position.value
    val base = floor(p).toInt()
    // At rest the wheel is a one-digit window. While dragging it opens up like the iOS picker:
    // the neighbouring digits appear faintly above and below the current one.
    Box(if (expanded > 0f) Modifier else Modifier.clipToBounds()) {
        // Invisible "8" sizes the wheel window to one digit.
        Text("8", style = style, color = Color.Transparent)
        for (k in (base - 1)..(base + 2)) {
            val distance = k - p
            val near = kotlin.math.abs(distance)
            val alpha = if (near < 1f) 1f - near * 0.7f else expanded * 0.3f * (1f - (near - 1f)).coerceIn(0f, 1f)
            if (alpha <= 0.01f) continue
            Text(
                Math.floorMod(k, 10).toString(),
                style = style,
                color = color,
                modifier = Modifier.graphicsLayer {
                    translationY = distance * size.height
                    this.alpha = alpha
                },
            )
        }
    }
}

/**
 * A home-screen statistic you can "spin": drag up or down to roll the number with a tick per step,
 * and it rolls back to the real value when released. [value] may have [decimals] (e.g. 1.2 GB).
 */
@Composable
fun SpinStat(
    value: Double,
    decimals: Int,
    suffix: String,
    label: String,
    modifier: Modifier,
    intro: Boolean,
    introDelayMs: Int,
    haptics: Boolean,
    decimalSeparator: Char = '.',
) {
    val hapticFeedback = LocalHapticFeedback.current
    val view = LocalView.current
    // A short step and a little acceleration: slow drags move one digit at a time, flicks move many.
    val stepPx = with(LocalDensity.current) { 6.dp.toPx() }
    val accelPx = with(LocalDensity.current) { 14.dp.toPx() }
    var lastTick by remember { mutableStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    val expanded by animateFloatAsState(if (dragging) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium), label = "wheel-open")
    // A brief accent glow whenever the real value changes (e.g. a play was counted).
    val pulse = remember { Animatable(0f) }
    var lastValue by remember { mutableStateOf(value) }
    LaunchedEffect(value) {
        if (value != lastValue) {
            pulse.snapTo(1f)
            pulse.animateTo(0f, tween(900))
        }
        lastValue = value
    }
    val accent = MaterialTheme.colorScheme.primary
    val numberColor = lerp(MaterialTheme.colorScheme.onSurface, accent, maxOf(expanded, pulse.value))
    var steps by remember { mutableIntStateOf(0) }
    var direction by remember { mutableIntStateOf(0) }
    val unit = 10.0.pow(-decimals)
    val shown = (value + steps * unit).coerceAtLeast(0.0)
    val text = String.format(Locale.ROOT, "%.${decimals}f", shown).replace('.', decimalSeparator)

    fun tick() {
        if (!haptics) return
        // At flick speed many digits pass per frame; cap the clicks so it stays a crisp ratchet.
        val now = System.currentTimeMillis()
        if (now - lastTick < 28) return
        lastTick = now
        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        view.playSoundEffect(SoundEffectConstants.CLICK)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.pointerInput(value, decimals) {
            var accumulated = 0f
            detectVerticalDragGestures(
                onDragStart = {
                    accumulated = 0f
                    dragging = true
                },
                onDragEnd = {
                    dragging = false
                    direction = if (steps > 0) -1 else 1
                    steps = 0
                },
                onDragCancel = {
                    dragging = false
                    direction = if (steps > 0) -1 else 1
                    steps = 0
                },
                onVerticalDrag = { change, dy ->
                    change.consume()
                    val boost = 1f + (kotlin.math.abs(dy) / accelPx).coerceAtMost(3f)
                    accumulated += dy * boost
                    // Finger up = bigger number, like spinning a wheel upwards.
                    while (accumulated <= -stepPx) {
                        accumulated += stepPx
                        direction = 1
                        steps++
                        tick()
                    }
                    while (accumulated >= stepPx) {
                        accumulated -= stepPx
                        if (value + (steps - 1) * unit < 0) break
                        direction = -1
                        steps--
                        tick()
                    }
                },
            )
        },
    ) {
        // Fixed-height slot: Arabic unit glyphs come from a fallback font with taller metrics.
        Box(
            Modifier
                .height(36.dp)
                .graphicsLayer {
                    // The wheel you're holding lifts slightly; a value change gives it a small pop.
                    val scale = 1f + 0.08f * expanded + 0.06f * pulse.value
                    scaleX = scale
                    scaleY = scale
                },
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RollingNumber(
                    text = text,
                    // Tabular figures: every digit the same width, so the number doesn't shuffle while rolling.
                    style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                    color = numberColor,
                    intro = intro,
                    introDelayMs = introDelayMs,
                    direction = direction,
                    expanded = expanded,
                )
                if (suffix.isNotEmpty()) {
                    Text(
                        " $suffix",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        // The label steps aside while the open wheel shows its neighbouring digits.
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer { alpha = 1f - expanded },
        )
    }
}

/** Splits "237 MB" / "1.2 GB" / "1٫2 غ.ب" into its number, decimal count, separator and unit. */
data class SizeParts(val value: Double, val decimals: Int, val separator: Char, val unit: String)

fun splitSize(formatted: String): SizeParts {
    val match = Regex("""^([\d]+)(?:([.,٫])(\d+))?\s*(.*)$""").find(formatted.trim())
        ?: return SizeParts(0.0, 0, '.', formatted)
    val whole = match.groupValues[1]
    val fraction = match.groupValues[3]
    val separator = match.groupValues[2].firstOrNull() ?: '.'
    val value = "$whole.${fraction.ifEmpty { "0" }}".toDoubleOrNull() ?: 0.0
    return SizeParts(value, fraction.length, separator, match.groupValues[4])
}
