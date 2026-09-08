package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.COUNTRIES
import com.example.data.model.Country
import com.example.ui.theme.OxfordBlue

/**
 * Compact dial-code selector meant to sit beside a phone number [InputField] — tap to
 * pick a country, the field shows just the flag + dial code (e.g. "🇱🇧 +961"). Combine
 * the selected [Country.dialCode] with the typed local number to build the full E.164
 * number Firebase Phone Auth needs (see LoginAuthScreen's registration/sign-in flow).
 */
@Composable
fun CountryCodeSelector(
    selectedCountry: Country,
    onCountrySelected: (Country) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedCard(
            modifier = Modifier
                .clickable { expanded = true }
                .height(56.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(selectedCountry.flagEmoji, fontSize = 16.sp)
                Text(selectedCountry.dialCode, fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose country code", modifier = Modifier.size(16.dp))
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 360.dp)
        ) {
            COUNTRIES.forEach { country ->
                DropdownMenuItem(
                    text = { Text("${country.flagEmoji} ${country.name}   ${country.dialCode}", fontSize = MaterialTheme.typography.bodySmall.fontSize) },
                    onClick = {
                        onCountrySelected(country)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Full "Country" dropdown field for the registration form — a separate concept from
 * [CountryCodeSelector] (a registrant's operating country doesn't have to match the
 * dial code of the phone they registered with, e.g. a Lebanese expat with a foreign
 * number), even though both default to Lebanon.
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
            shape = RoundedCornerShape(10.dp)
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
