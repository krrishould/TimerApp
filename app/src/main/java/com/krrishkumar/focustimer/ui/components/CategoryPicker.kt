package com.krrishkumar.focustimer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.krrishkumar.focustimer.AlltimeApp
import com.krrishkumar.focustimer.data.Category
import com.krrishkumar.focustimer.data.CategoryStore
import com.krrishkumar.focustimer.ui.theme.AppColors
import com.krrishkumar.focustimer.ui.theme.LocalAppColors

/** A category's colour, or an empty ring when there is none. */
@Composable
fun CategoryDot(color: Int?, size: Dp = 10.dp) {
    val colors = LocalAppColors.current
    Box(
        Modifier
            .size(size)
            .then(
                if (color != null) Modifier.background(Color(color), CircleShape)
                else Modifier.border(1.5.dp, colors.textMuted, CircleShape)
            )
    )
}

/** The tappable line above the time showing what this stretch is being spent on. */
@Composable
fun CategoryChip(category: Category?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        CategoryDot(category?.color, size = 9.dp)
        Text(
            text = category?.name ?: "Choose category",
            style = MaterialTheme.typography.labelMedium,
            color = if (category != null) colors.textSecondary else colors.textMuted
        )
        PencilIcon(color = colors.textMuted)
    }
}

/**
 * Pick, add or delete a category. Reads the shared list itself, so every screen that opens
 * it sees the same categories.
 */
@Composable
fun CategoryPicker(
    selectedId: Long?,
    onSelect: (Category?) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Category"
) {
    val colors = LocalAppColors.current
    val store = (LocalContext.current.applicationContext as AlltimeApp).categoryStore
    val categories by store.categories.collectAsState()
    var newName by remember { mutableStateOf("") }
    var confirmingDelete by remember { mutableStateOf<Long?>(null) }

    fun add() {
        if (newName.isBlank()) return
        store.create(newName) { created -> onSelect(created) }
        newName = ""
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(320.dp)
                .heightIn(max = 580.dp)
                .background(colors.surface, RoundedCornerShape(24.dp))
                .padding(vertical = 22.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                PickerRow(
                    name = "No category",
                    dotColor = null,
                    selected = selectedId == null,
                    colors = colors,
                    onClick = { onSelect(null) }
                )
                categories.forEach { category ->
                    if (confirmingDelete == category.id) {
                        ConfirmDeleteRow(
                            category = category,
                            colors = colors,
                            onDelete = {
                                store.delete(category)
                                confirmingDelete = null
                            },
                            onKeep = { confirmingDelete = null }
                        )
                    } else {
                        PickerRow(
                            name = category.name,
                            dotColor = category.color,
                            selected = category.id == selectedId,
                            colors = colors,
                            onClick = { onSelect(category) },
                            onDeleteRequest = { confirmingDelete = category.id }
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(colors.surfaceRaised, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 11.dp)
                ) {
                    if (newName.isEmpty()) {
                        Text("New category", style = MaterialTheme.typography.bodyLarge, color = colors.textMuted)
                    }
                    BasicTextField(
                        value = newName,
                        onValueChange = { if (it.length <= CategoryStore.MAX_NAME_LENGTH) newName = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = colors.textPrimary,
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize
                        ),
                        cursorBrush = SolidColor(colors.accent),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { add() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                val canAdd = newName.isNotBlank()
                Box(
                    modifier = Modifier
                        .background(if (canAdd) colors.accent else colors.surfaceRaised, RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = canAdd,
                            onClick = { add() }
                        )
                        .padding(horizontal = 16.dp, vertical = 11.dp)
                ) {
                    Text(
                        "Add",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (canAdd) colors.onAccent else colors.textMuted
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Deleting a category hides it here. Past sessions keep it.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}

@Composable
private fun PickerRow(
    name: String,
    dotColor: Int?,
    selected: Boolean,
    colors: AppColors,
    onClick: () -> Unit,
    onDeleteRequest: (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) colors.surfaceRaised else Color.Transparent, RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
    ) {
        CategoryDot(dotColor, size = 12.dp)
        Text(
            name,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (selected) CheckGlyph(colors.accent)
        Box(
            modifier = Modifier
                .size(40.dp)
                .then(
                    if (onDeleteRequest != null) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDeleteRequest
                        )
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (onDeleteRequest != null) TrashGlyph(colors.textMuted)
        }
    }
}

@Composable
private fun ConfirmDeleteRow(category: Category, colors: AppColors, onDelete: () -> Unit, onKeep: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceRaised, RoundedCornerShape(14.dp))
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
    ) {
        Text(
            "Delete ${category.name}?",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        TextAction("Keep", colors.textSecondary, onKeep)
        TextAction("Delete", colors.danger, onDelete)
    }
}

@Composable
private fun TextAction(label: String, color: Color, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.titleMedium,
        color = color,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 10.dp)
    )
}

@Composable
private fun CheckGlyph(color: Color) {
    Canvas(Modifier.size(16.dp)) {
        val w = size.width
        val stroke = w * 0.13f
        drawLine(color, Offset(w * 0.18f, w * 0.52f), Offset(w * 0.42f, w * 0.76f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.42f, w * 0.76f), Offset(w * 0.84f, w * 0.26f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun TrashGlyph(color: Color) {
    Canvas(Modifier.size(16.dp)) {
        val w = size.width
        val stroke = w * 0.1f
        drawLine(color, Offset(w * 0.14f, w * 0.24f), Offset(w * 0.86f, w * 0.24f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.4f, w * 0.12f), Offset(w * 0.6f, w * 0.12f), stroke, StrokeCap.Round)
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.24f, w * 0.34f),
            size = androidx.compose.ui.geometry.Size(w * 0.52f, w * 0.56f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f),
            style = Stroke(width = stroke)
        )
    }
}
