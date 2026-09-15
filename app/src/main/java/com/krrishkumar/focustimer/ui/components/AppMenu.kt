package com.krrishkumar.focustimer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.krrishkumar.focustimer.ui.theme.AppColors
import com.krrishkumar.focustimer.ui.theme.LocalAppColors

/**
 * The three-bar button and the menu it opens: the few things worth reaching in a tap,
 * with the full Settings screen one step further in.
 */
@Composable
fun AppMenuButton(
    canEnterFocus: Boolean,
    isDark: Boolean,
    onThemeChange: (Boolean) -> Unit,
    onEnterFocus: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val colors = LocalAppColors.current
    var expanded by remember { mutableStateOf(false) }
    var managingCategories by remember { mutableStateOf(false) }
    var showingAbout by remember { mutableStateOf(false) }

    Box {
        Box(
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { expanded = true }
                // The icon is a drawing, so it needs a spoken name of its own.
                .semantics { contentDescription = "Menu" }
                .padding(12.dp)
        ) {
            MenuIcon(color = colors.textSecondary)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = colors.surface,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.width(250.dp)
        ) {
            if (canEnterFocus) {
                MenuRow("Focus mode", "Only the time on screen", colors) {
                    expanded = false
                    onEnterFocus()
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onThemeChange(!isDark) }
                    .padding(start = 18.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)
            ) {
                Text("Dark theme", style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary, modifier = Modifier.weight(1f))
                Switch(
                    checked = isDark,
                    onCheckedChange = onThemeChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.onAccent,
                        checkedTrackColor = colors.accent,
                        uncheckedThumbColor = colors.textMuted,
                        uncheckedTrackColor = colors.surfaceRaised,
                        uncheckedBorderColor = colors.border
                    )
                )
            }
            MenuRow("Categories", "Add or delete", colors) {
                expanded = false
                managingCategories = true
            }
            HorizontalDivider(color = colors.border, modifier = Modifier.padding(vertical = 6.dp))
            MenuRow("Settings", null, colors) {
                expanded = false
                onOpenSettings()
            }
            MenuRow("About Alltime", null, colors) {
                expanded = false
                showingAbout = true
            }
        }
    }

    if (managingCategories) {
        CategoryPicker(
            title = "Categories",
            selectedId = null,
            onSelect = {},
            onDismiss = { managingCategories = false },
            manage = true
        )
    }
    if (showingAbout) {
        AboutDialog(colors) { showingAbout = false }
    }
}

@Composable
private fun MenuRow(title: String, subtitle: String?, colors: AppColors, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 18.dp, vertical = 11.dp)
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
        }
    }
}

@Composable
private fun AboutDialog(colors: AppColors, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "unknown"
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(300.dp)
                .background(colors.surface, RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Text("Alltime", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
            Text("Version $version", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
            Spacer(Modifier.height(14.dp))
            Text(
                "A timer, stopwatch and clock that keep track of where your focused time goes.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary
            )
            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceRaised, RoundedCornerShape(14.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Close", style = MaterialTheme.typography.titleMedium, color = colors.textSecondary)
            }
        }
    }
}
