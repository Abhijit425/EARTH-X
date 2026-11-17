@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.earthx.explorer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.earthx.explorer.ui.theme.EX

private val glassBrush = Brush.verticalGradient(listOf(Color(0xE60E1628), Color(0xE6080D19)))

/** Frosted dark panel with a faint cyan hairline. Used sparingly for primary surfaces. */
@Composable
fun GlassPanel(modifier: Modifier = Modifier, corner: Dp = 20.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(glassBrush)
            .border(BorderStroke(1.dp, Brush.verticalGradient(listOf(EX.Stroke, EX.StrokeSoft))), RoundedCornerShape(corner)),
        content = content,
    )
}

@Composable
fun GlassBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .background(EX.Glass)
            .border(1.dp, EX.StrokeSoft, RoundedCornerShape(28.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun PanelHeader(title: String, subtitle: String? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = EX.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = EX.TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = EX.Accent.copy(alpha = 0.85f),
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp))
}

@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = EX.Text) {
    Row(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = EX.TextDim, modifier = Modifier.width(132.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor, modifier = Modifier.weight(1f))
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, accent: Color = EX.Accent, icon: ImageVector? = null) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x14FFFFFF))
            .border(1.dp, EX.StrokeSoft, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, tint = accent, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(6.dp)) }
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = EX.TextDim, maxLines = 1)
        }
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = EX.Text, fontWeight = FontWeight.SemiBold, maxLines = 2)
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String? = null, checked: Boolean, icon: ImageVector? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = if (checked) EX.Accent else EX.TextDim, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(14.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = EX.Text)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = EX.TextDim)
        }
        Switch(checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = EX.Accent.copy(alpha = 0.55f), checkedThumbColor = EX.Accent))
    }
}

@Composable
fun SourceNote(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = EX.TextFaint, modifier = modifier.padding(horizontal = 20.dp, vertical = 6.dp))
}

@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier.clip(CircleShape).background(color.copy(alpha = 0.16f)).border(1.dp, color.copy(alpha = 0.45f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = color, modifier = Modifier.size(13.dp)); Spacer(Modifier.width(5.dp)) }
        else { Box(Modifier.size(7.dp).clip(CircleShape).background(color)); Spacer(Modifier.width(6.dp)) }
        Text(text, style = MaterialTheme.typography.labelMedium, color = EX.Text, maxLines = 1)
    }
}

@Composable
fun LoadingRow(text: String) {
    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = EX.Accent)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = EX.TextDim)
    }
}

@Composable
fun ErrorRow(text: String, onRetry: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = EX.Danger)
        if (onRetry != null) Text("Retry", style = MaterialTheme.typography.labelLarge, color = EX.Accent,
            modifier = Modifier.padding(top = 6.dp).clickable { onRetry() })
    }
}

@Composable
fun ListItemRow(
    title: String,
    subtitle: String? = null,
    leading: String? = null,
    leadingIcon: ImageVector? = null,
    trailing: String? = null,
    trailingColor: Color = EX.TextDim,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (leading != null) Text(leading, style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(30.dp))
        if (leadingIcon != null) Icon(leadingIcon, null, tint = EX.Accent, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = EX.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = EX.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelLarge, color = trailingColor)
    }
}

@Composable
fun RoundIconButton(icon: ImageVector, description: String, active: Boolean = false, size: Dp = 44.dp, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (active) EX.Accent.copy(alpha = 0.22f) else Color.Transparent,
        border = if (active) BorderStroke(1.dp, EX.Accent.copy(alpha = 0.6f)) else null,
        modifier = Modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = if (active) EX.Accent else EX.Text, modifier = Modifier.size(22.dp))
        }
    }
}
