package app.vidro.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.vidro.ui.theme.LocalIsDark
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

data class TabItem(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

/** The selected tab grows to show its label; the others are icon-only. */
private val TabWidth = 60.dp
private val SelectedTabWidth = 108.dp
private val LensStretch = 36.dp
private val BarHeight = 64.dp
private val BarPadding = 4.dp

/**
 * Frosted-glass surface shared by the floating bar and its companion buttons: blurred content behind
 * (Android 12+; a translucent tint on older versions) with a thin light edge, like iOS 26 / Pixel glass.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun Modifier.glass(hazeState: HazeState, shape: Shape = CircleShape): Modifier {
    val dark = LocalIsDark.current
    return this
        .clip(shape)
        .hazeEffect(
            state = hazeState,
            style = HazeMaterials.thin(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        )
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = if (dark) 0.16f else 0.9f),
                    (if (dark) Color.White else Color.Black).copy(alpha = 0.06f),
                ),
            ),
            shape = shape,
        )
}

/**
 * Floating pill tab bar. The selected tab shows its label; a soft lens sits under it and follows
 * the finger while swiping between pages ([position] is the fractional page). Only the selected tab
 * uses the accent color.
 */
@Composable
fun FloatingTabBar(
    tabs: List<TabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    position: Float = selectedIndex.toFloat(),
    /** Minimised while content scrolls down: only the current tab's icon remains. */
    compact: Boolean = false,
    onExpand: () -> Unit = {},
    /** Narrower tabs on small phones, so four tabs and the search button still fit. */
    tabWidth: Dp = TabWidth,
    selectedTabWidth: Dp = SelectedTabWidth,
) {
    val dark = LocalIsDark.current
    val haptics = LocalHapticFeedback.current
    // One light tick whenever the selected tab changes, whether by tap or by swipe.
    var lastSelected by remember { mutableIntStateOf(selectedIndex) }
    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0 && lastSelected >= 0 && selectedIndex != lastSelected && LocalHapticsEnabled.value) {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
        lastSelected = selectedIndex
    }
    Box(
        modifier = modifier
            // A soft, low shadow lifts the glass off the content without a hard edge.
            .shadow(16.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.16f))
            .height(BarHeight)
            .glass(hazeState)
            .padding(BarPadding),
    ) {
        if (selectedIndex >= 0) {
            // Liquid lens: mid-swipe it stretches like a drop of water between two tabs and
            // settles back to its size when the page lands (stretch = 0 at rest, 1 halfway).
            val clamped = position.coerceIn(0f, (tabs.size - 1).toFloat())
            val stretch = sin(PI * (clamped - floor(clamped))).toFloat()
            val lensWidth by animateDpAsState(
                if (compact) tabWidth else selectedTabWidth + LensStretch * stretch,
                spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
                label = "lens-width",
            )
            val lensX = if (compact) 0.dp else tabWidth * clamped - LensStretch / 2 * stretch
            Box(
                Modifier
                    // Tabs before the selected one are collapsed, so the lens sits at position × collapsed width.
                    .offset(x = lensX)
                    .width(lensWidth)
                    .fillMaxHeight()
                    .graphicsLayer { scaleY = 1f - 0.1f * stretch }
                    .clip(CircleShape)
                    .background((if (dark) Color.White else Color.Black).copy(alpha = if (dark) 0.10f else 0.06f))
                    .border(
                        1.dp,
                        (if (dark) Color.White else Color.Black).copy(alpha = if (dark) 0.06f else 0.04f),
                        CircleShape,
                    ),
            )
        }
        Row {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    tab = tab,
                    selected = index == selectedIndex,
                    compact = compact,
                    tabWidth = tabWidth,
                    selectedTabWidth = selectedTabWidth,
                    onClick = { if (compact) onExpand() else onSelect(index) },
                )
            }
        }
    }
}

@Composable
private fun Tab(tab: TabItem, selected: Boolean, compact: Boolean, tabWidth: Dp, selectedTabWidth: Dp, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val color by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "tab-color",
    )
    val width by animateDpAsState(
        when {
            compact && !selected -> 0.dp
            compact -> tabWidth
            selected -> selectedTabWidth
            else -> tabWidth
        },
        spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "tab-width",
    )
    // Gentle feedback: the tab dips slightly while pressed; the selected icon is a touch larger.
    val scale by animateFloatAsState(
        when {
            pressed -> 0.92f
            selected -> 1.06f
            else -> 1f
        },
        spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "tab-scale",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = tab.label },
    ) {
        Icon(
            if (selected) tab.selectedIcon else tab.icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp).graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        )
        AnimatedVisibility(
            visible = selected && !compact,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Text(
                tab.label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
            )
        }
    }
}

/** Whether UI ticks are on (Settings → Haptics and sounds); read by small widgets without plumbing. */
object LocalHapticsEnabled {
    var value: Boolean = true
}

/** Round glass button that sits beside the tab bar (search), matching its height. */
@Composable
fun FloatingCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .shadow(16.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.16f))
            .size(BarHeight)
            .glass(hazeState)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Icon(icon, contentDescription = contentDescription, tint = MaterialTheme.colorScheme.onSurface)
    }
}
