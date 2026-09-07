package com.krrishkumar.focustimer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.krrishkumar.focustimer.ui.theme.AppColors
import com.krrishkumar.focustimer.ui.theme.LocalAppColors

/**
 * Shared "give this a name" dialog, used for naming the running activity on the
 * Timer and Stopwatch and for renaming a recorded session in History.
 */
@Composable
fun NameDialog(
    title: String,
    placeholder: String,
    initialValue: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    /** Supplied only where the thing being named can also be removed (History). */
    onDelete: (() -> Unit)? = null
) {
    val colors = LocalAppColors.current
    var text by remember { mutableStateOf(initialValue) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(300.dp)
                .background(colors.surface, RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceRaised, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                if (text.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textMuted
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = { if (it.length <= 40) text = it },
                    textStyle = TextStyle(
                        color = colors.textPrimary,
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSave(text) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                )
            }

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DialogButton("Cancel", filled = false, colors = colors, onClick = onDismiss, modifier = Modifier.weight(1f))
                DialogButton("Save", filled = true, colors = colors, onClick = { onSave(text) }, modifier = Modifier.weight(1f))
            }

            if (onDelete != null) {
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDelete
                        )
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Delete session",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.danger
                    )
                }
            }
        }
    }
}

@Composable
private fun DialogButton(
    label: String,
    filled: Boolean,
    colors: AppColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(if (filled) colors.accent else colors.surfaceRaised, RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = if (filled) colors.onAccent else colors.textSecondary
        )
    }
}
