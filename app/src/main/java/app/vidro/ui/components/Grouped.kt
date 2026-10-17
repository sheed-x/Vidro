package app.vidro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

private val Outer = 24.dp
private val Inner = 6.dp

/**
 * Android 16 style grouping: rows of one group are separate rounded tiles with a small gap,
 * large corners on the outside of the group and small ones between rows.
 */
fun groupShape(index: Int, count: Int) = RoundedCornerShape(
    topStart = if (index == 0) Outer else Inner,
    topEnd = if (index == 0) Outer else Inner,
    bottomStart = if (index == count - 1) Outer else Inner,
    bottomEnd = if (index == count - 1) Outer else Inner,
)

/** Lays out one row of a group (works inside LazyColumn items too). */
@Composable
fun Modifier.groupItem(index: Int, count: Int): Modifier = this
    .padding(horizontal = 16.dp)
    .padding(top = if (index == 0) 0.dp else 2.dp)
    .clip(groupShape(index, count))
    .background(MaterialTheme.colorScheme.surfaceContainerLow)

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 32.dp, end = 20.dp, top = 24.dp, bottom = 8.dp)
            .heightIn(min = 32.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

/** A whole group built from a list of rows, for non-lazy screens like Settings. */
@Composable
fun Group(title: String?, rows: List<@Composable () -> Unit>) {
    Column {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 24.dp, bottom = 10.dp),
            )
        }
        rows.forEachIndexed { index, row ->
            Box(Modifier.fillMaxWidth().groupItem(index, rows.size)) { row() }
        }
    }
}

@Composable
fun GroupRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    summary: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 68.dp)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        if (icon != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (summary != null) {
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing?.invoke()
    }
}
