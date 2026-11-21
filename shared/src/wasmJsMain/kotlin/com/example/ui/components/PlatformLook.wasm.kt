package com.example.ui.components

import androidx.compose.runtime.Composable
import com.example.data.model.Country

@Composable
actual fun rememberCountryDisplayName(): (Country) -> String = { it.name }

@Composable
actual fun rememberReducedMotion(): Boolean = false
