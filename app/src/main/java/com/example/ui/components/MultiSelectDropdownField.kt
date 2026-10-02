package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A dropdown field that lets the user tick several options. The menu stays open while
 * toggling so several choices can be made in one go; the field shows a short summary.
 */
@Composable
fun <T> MultiSelectDropdownField(
    label: String,
    options: List<T>,
    selected: Set<T>,
    optionLabel: (T) -> String,
    onSelectionChange: (Set<T>) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Any",
) {
    var expanded by remember { mutableStateOf(false) }
    val summary = when {
        selected.isEmpty() -> placeholder
        selected.size <= 2 -> options.filter { it in selected }.joinToString { optionLabel(it) }
        else -> "${selected.size} selected"
    }
    Box(modifier = modifier) {
        OutlinedTextField(
            value = summary,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = {
                Icon(
                    if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                    contentDescription = null
                )
            },
            textStyle = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth()
        )
        // A read-only text field still consumes taps, so a transparent layer on top
        // opens the menu.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(enabled = options.isNotEmpty()) { expanded = true }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 360.dp)
        ) {
            if (selected.isNotEmpty()) {
                DropdownMenuItem(
                    text = { Text("Clear selection") },
                    leadingIcon = { Icon(Icons.Default.Clear, contentDescription = null) },
                    onClick = { onSelectionChange(emptySet()) }
                )
            }
            options.forEach { option ->
                val isChecked = option in selected
                DropdownMenuItem(
                    text = { Text(optionLabel(option), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Checkbox(checked = isChecked, onCheckedChange = null) },
                    onClick = { onSelectionChange(if (isChecked) selected - option else selected + option) }
                )
            }
        }
    }
}
