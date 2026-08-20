package com.uliteamr.rustupmanager.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uliteamr.rustupmanager.icons.ChevronDown
import com.uliteamr.rustupmanager.rustup.RaFeatureSection
import com.uliteamr.rustupmanager.rustup.RaSubFeature
import com.uliteamr.rustupmanager.rustup.boolAt
import com.uliteamr.rustupmanager.rustup.masterChecked
import com.uliteamr.rustupmanager.rustup.setMaster
import com.uliteamr.rustupmanager.rustup.setPath
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * One feature of the rust-analyzer catalog: a card with the feature's master switch plus an
 * expander beside it. Tapping the switch flips the master; tapping anywhere else on the card
 * (or the chevron) opens the sub-feature drawer below it. Sections without sub-features are
 * plain single-switch cards.
 */
@Composable
fun RaFeatureSectionCard(
    section: RaFeatureSection,
    root: JsonObject,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onCommit: (JsonObject) -> Unit,
) {
    val hasDrawer = section.subFeatures.isNotEmpty()
    SettingsCard(
        title = section.title,
        description = section.description,
        modifier = if (hasDrawer) Modifier.clickable(onClick = onToggleExpanded) else Modifier,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppSwitch(
                    checked = masterChecked(section, root),
                    onCheckedChange = { on -> onCommit(setMaster(section, root, on)) },
                )
                if (hasDrawer) {
                    IconButton(onClick = onToggleExpanded) {
                        Icon(
                            ChevronDown,
                            contentDescription = "Toggle sub-features",
                            modifier = Modifier.rotate(if (expanded) 180f else 0f),
                        )
                    }
                }
            }
        },
        content = if (hasDrawer) {
            {
                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        section.subFeatures.forEachIndexed { index, sub ->
                            SubFeatureRow(
                                sub = sub,
                                root = root,
                                onCommit = onCommit,
                            )
                            if (index != section.subFeatures.lastIndex) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
        } else {
            null
        },
    )
}

@Composable
private fun SubFeatureRow(
    sub: RaSubFeature,
    root: JsonObject,
    onCommit: (JsonObject) -> Unit,
) {
    val checked = boolAt(root, sub.defaultValue, *sub.path.toTypedArray())
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(sub.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                sub.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AppSwitch(
            checked = checked,
            onCheckedChange = { on ->
                onCommit(setPath(root, JsonPrimitive(on), sub.path))
            },
        )
    }
}
