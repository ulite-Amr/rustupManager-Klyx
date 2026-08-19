package com.uliteamr.rustupmanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.uliteamr.rustupmanager.icons.ArrowBack
import kotlin.math.roundToInt
import com.uliteamr.rustupmanager.icons.Check
import com.uliteamr.rustupmanager.icons.Close

private val CardShape = RoundedCornerShape(28.dp)
private val FieldShape = RoundedCornerShape(16.dp)

@Composable
fun ScreenHeader(
    title: String,
    onBack: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 12.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            ) {
                Icon(ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.weight(1f))
            trailing?.invoke()
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
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
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp),
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
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
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
                    Spacer(Modifier.height(10.dp))
                }
                content()
            }
        }
    }
}

/** A single-choice M3 segmented control (Material 3 expressive style selector). */
@Composable
fun SegmentedChoice(
    options: List<String>,
    selected: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onSelect: (String) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(
        modifier = modifier.fillMaxWidth(),
        space = SegmentedButtonDefaults.BorderWidth,
    ) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = selected == option,
                onClick = { onSelect(option) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                label = { Text(option) },
            )
        }
    }
}

/** A soft organic "expressive" chip used to anchor empty and error states. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveIconChip(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    size: Dp = 72.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(containerColor, MaterialShapes.Puffy.toShape()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(size * 0.45f),
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
fun InlineSpinner(modifier: Modifier = Modifier.size(18.dp), strokeWidth: Dp = 2.dp) {
    CircularProgressIndicator(modifier = modifier, strokeWidth = strokeWidth, strokeCap = StrokeCap.Round)
}

@Composable
fun ExpressiveLinearProgressIndicator(modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        strokeCap = StrokeCap.Round,
        modifier = modifier,
    )
}

/** Determinate (with %) or indeterminate progress bar used inside operation cards. */
@Composable
fun OpProgressBar(
    progress: com.uliteamr.rustupmanager.rustup.OpProgress?,
    modifier: Modifier = Modifier,
) {
    val fraction = progress?.fraction
    Column(modifier = modifier) {
        if (fraction != null) {
            LinearProgressIndicator(
                progress = { fraction.coerceIn(0f, 1f) },
                strokeCap = StrokeCap.Round,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            ExpressiveLinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Text(
            text = progressCaption(progress),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
    }
}

/** "43% · 21.3 MiB / 48.9 MiB" when the download size is known, falling back to the label. */
private fun progressCaption(progress: com.uliteamr.rustupmanager.rustup.OpProgress?): String {
    val progress = progress ?: return ""
    val detail = buildString {
        progress.fraction?.let { append("${(it * 100).roundToInt()}%") }
        progress.downloadedBytes?.let { downloaded ->
            if (isNotEmpty()) append(" · ")
            append(formatBytes(downloaded))
            progress.totalBytes?.let { total -> append(" / ").append(formatBytes(total)) }
        }
    }
    return detail.ifEmpty { progress.label }
}

private fun formatBytes(bytes: Long): String {
    val value = bytes.toDouble()
    return when {
        bytes >= 1L shl 30 -> "%.1f GiB".format(value / (1L shl 30))
        bytes >= 1L shl 20 -> "%.1f MiB".format(value / (1L shl 20))
        bytes >= 1L shl 10 -> "%.1f KiB".format(value / (1L shl 10))
        else -> "$bytes B"
    }
}

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    multiline: Boolean = false,
    monospace: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val textFieldState = rememberTextFieldState(initialText = value)
    LaunchedEffect(value) {
        if (textFieldState.text.toString() != value) {
            textFieldState.setTextAndPlaceCursorAtEnd(value)
        }
    }
    val fieldStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurface,
        fontFamily = if (monospace) FontFamily.Monospace else null,
    )
    val fieldModifier = modifier
        .clip(FieldShape)
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        .border(
            width = if (focused) 2.dp else 1.dp,
            color = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            shape = FieldShape,
        )
    BasicTextField(
        state = textFieldState,
        enabled = enabled,
        textStyle = fieldStyle,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        interactionSource = interactionSource,
        modifier = fieldModifier,
        lineLimits = if (multiline) TextFieldLineLimits.MultiLine() else TextFieldLineLimits.SingleLine,
        decorator = { innerTextField ->
            Box(
                modifier = Modifier.padding(
                    horizontal = 16.dp,
                    vertical = if (multiline) 14.dp else 12.dp,
                ),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (textFieldState.text.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = if (monospace) FontFamily.Monospace else null,
                        ),
                    )
                }
                innerTextField()
            }
        },
    )
}
