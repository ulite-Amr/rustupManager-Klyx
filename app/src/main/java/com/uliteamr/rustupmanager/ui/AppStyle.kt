package com.uliteamr.rustupmanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.uliteamr.rustupmanager.icons.ArrowBack
import com.uliteamr.rustupmanager.icons.Check
import com.uliteamr.rustupmanager.icons.Close

@Composable
fun ScreenHeader(title: String, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .padding(start = 12.dp, top = 12.dp)
                .size(40.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
        ) {
            Icon(ArrowBack, contentDescription = "Back")
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 6.dp),
    )
}

@Composable
fun SettingsCard(
    icon: ImageVector? = null,
    title: String? = null,
    description: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (title != null || icon != null || trailing != null) {
                Row(verticalAlignment = Alignment.Top) {
                    if (icon != null) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(top = 2.dp, end = 12.dp)
                                .size(22.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        if (title != null) {
                            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                        if (description != null) {
                            Text(
                                description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                    if (trailing != null) {
                        Spacer(Modifier.width(12.dp))
                        trailing()
                    }
                }
            }
            if (content != null) {
                if (title != null || icon != null || trailing != null) {
                    Spacer(Modifier.height(8.dp))
                }
                content()
            }
        }
    }
}

@Composable
fun PillButton(text: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(50),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text,
            color = contentColor,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun AppSwitch(checked: Boolean, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        enabled = enabled,
        onCheckedChange = onCheckedChange,
        thumbContent = {
            Icon(
                imageVector = if (checked) Check else Close,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
        },
        colors = SwitchDefaults.colors(),
    )
}

@Composable
fun LogPanel(lines: List<String>, modifier: Modifier = Modifier, minHeight: Dp = 80.dp) {
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.scrollToItem(lines.size - 1)
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
    ) {
        if (lines.isEmpty()) {
            Box(modifier = Modifier.heightIn(min = minHeight).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                Text(
                    "No recent activity",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            SelectionContainer {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.heightIn(min = minHeight, max = 260.dp),
                    contentPadding = PaddingValues(12.dp),
                ) {
                    items(lines) { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = if (isErrorLine(line)) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Rough classification of rust-analyzer log lines for the "Errors" filter and coloring. */
internal fun isErrorLine(line: String): Boolean =
    line.contains("ERROR", ignoreCase = true) ||
        line.contains("PANIC", ignoreCase = true) ||
        line.contains("FATAL", ignoreCase = true) ||
        line.startsWith("!!!")

@Composable
fun InlineSpinner() {
    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
}

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                innerTextField()
            }
        },
    )
}
