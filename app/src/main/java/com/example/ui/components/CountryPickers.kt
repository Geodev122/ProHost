package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.COUNTRIES
import com.example.data.model.Country
import com.example.ui.theme.OxfordBlue

/**
 * Search-and-tap country chooser, used to let the user correct an auto-detected dial
 * code (see [PhoneNumberField] / com.example.util.PhoneCountryDetector) rather than
 * hunting through a long anchored dropdown — that dropdown-menu approach is what this
 * replaces for phone-number entry specifically.
 */
@Composable
fun CountryPickerDialog(
    selected: Country,
    onSelect: (Country) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) {
        if (query.isBlank()) {
            COUNTRIES
        } else {
            COUNTRIES.filter {
                it.name.contains(query, ignoreCase = true) || it.dialCode.contains(query)
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose your country") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search country or code") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(filtered) { country ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(country) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(country.flagEmoji, fontSize = MaterialTheme.typography.bodyLarge.fontSize)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                country.name,
                                modifier = Modifier.weight(1f),
                                fontWeight = if (country == selected) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(country.dialCode, fontWeight = FontWeight.Bold, color = OxfordBlue)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

/**
 * A single continuous phone number field: the dial code appears as a fixed, non-editable
 * prefix inside the same field (e.g. "🇱🇧 +961" ahead of the cursor) rather than as a
 * separate tappable dropdown beside it — the user types only the local number, and
 * [onCountryChange] only ever fires from the "Change" action if the auto-detected
 * country needs correcting. The caller is responsible for combining [country]'s dial
 * code with [number]'s digits into the E.164 string Firebase Phone Auth needs.
 */
@Composable
fun PhoneNumberField(
    country: Country,
    onCountryChange: (Country) -> Unit,
    number: String,
    onNumberChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Phone Number",
    placeholder: String = "3 123 456"
) {
    var showPicker by remember { mutableStateOf(false) }
    InputField(
        value = number,
        onValueChange = onNumberChange,
        label = label,
        placeholder = placeholder,
        prefix = "${country.flagEmoji} ${country.dialCode} ",
        leadingIcon = androidx.compose.material.icons.Icons.Default.Phone,
        trailingIcon = {
            TextButton(onClick = { showPicker = true }) {
                Text("Change", style = MaterialTheme.typography.labelSmall)
            }
        },
        modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        singleLine = true
    )
    if (showPicker) {
        CountryPickerDialog(
            selected = country,
            onSelect = {
                onCountryChange(it)
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }
}

/**
 * Full "Country" dropdown field for the registration form — a separate concept from
 * [PhoneNumberField]'s dial code (a registrant's operating country doesn't have to
 * match the dial code of the phone they registered with, e.g. a Lebanese expat with a
 * foreign number), even though both default to Lebanon.
 */
@Composable
fun CountryDropdownField(
    selectedCountry: Country,
    onCountrySelected: (Country) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Country"
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
            shape = MaterialTheme.shapes.medium
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Public, contentDescription = null, tint = OxfordBlue, modifier = Modifier.size(20.dp))
                    Column {
                        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "${selectedCountry.flagEmoji} ${selectedCountry.name}",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 360.dp)
        ) {
            COUNTRIES.forEach { country ->
                DropdownMenuItem(
                    text = { Text("${country.flagEmoji} ${country.name}") },
                    onClick = {
                        onCountrySelected(country)
                        expanded = false
                    }
                )
            }
        }
    }
}
