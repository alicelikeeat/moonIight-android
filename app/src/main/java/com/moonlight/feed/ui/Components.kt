package com.moonlight.feed.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moonlight.feed.ui.design.MoonChip
import com.moonlight.feed.ui.design.glassStatic
import com.moonlight.feed.ui.design.moonPress
import com.moonlight.feed.ui.theme.LocalMoonColors

/** Glass card (static frosted — used inside scrolling lists). */
@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassStatic(RoundedCornerShape(22.dp))
            .padding(horizontal = 16.dp),
        content = content,
    )
}

/** iOS inset-grouped glass section. */
@Composable
fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalMoonColors.current
    Column(modifier = modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = c.textDim,
            modifier = Modifier.padding(start = 36.dp, bottom = 8.dp, top = 16.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .glassStatic(RoundedCornerShape(20.dp)),
            content = content,
        )
        footer?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = c.textDim, modifier = Modifier.padding(start = 36.dp, end = 36.dp, top = 6.dp))
        }
    }
}

/** Tinted rounded-square icon tile (iOS settings style). */
@Composable
fun IconTile(icon: ImageVector, tint: Color = LocalMoonColors.current.accent) {
    Box(
        Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(tint.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp)) }
}

@Composable
fun SettingsRow(
    title: String,
    subtitle: String? = null,
    trailing: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    iconTint: Color? = null,
    destructive: Boolean = false,
    chevron: Boolean = onClick != null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = LocalMoonColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null || onLongClick != null)
                    Modifier.moonPress(onClick ?: {}, onLongClick, pressedScale = 0.98f, highlight = c.text.copy(alpha = 0.06f))
                else Modifier
            )
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) IconTile(icon, iconTint ?: if (destructive) c.danger else c.accent)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (destructive) c.danger else c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.textDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.bodyMedium, color = c.textDim, maxLines = 1)
        }
        trailingContent?.invoke(this)
        if (chevron && trailingContent == null) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = c.textDim.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun SettingsDivider() {
    val c = LocalMoonColors.current
    Box(Modifier.fillMaxWidth().padding(start = 56.dp).height(0.6.dp).background(c.hairline))
}

@Composable
fun SoftChip(text: String, selected: Boolean, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    MoonChip(text, selected, onClick, onLongClick = onLongClick)
}
