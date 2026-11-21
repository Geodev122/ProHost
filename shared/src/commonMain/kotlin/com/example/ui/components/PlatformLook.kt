package com.example.ui.components

import androidx.compose.runtime.Composable
import com.example.data.model.Country

/**
 * The country's name in the app language (Arabic users read "لبنان"); English keeps the list's own names. Display only:
 * the profile and listings store [Country.name].
 */
@Composable
expect fun rememberCountryDisplayName(): (Country) -> String

/**
 * True when the person turned animations off (Android: Remove animations / animator scale 0; iOS: Reduce Motion).
 * Transitions should then snap.
 */
@Composable
expect fun rememberReducedMotion(): Boolean

/** The screen height in dp (Android: Configuration.screenHeightDp; iOS/web: the window's height). */
@Composable
expect fun rememberScreenHeightDp(): Int

/** BCP-47 tag of the app language now shown (Android: the current Configuration locale, as the screens read it). */
@Composable
expect fun rememberAppLanguageTag(): String
