package com.example.ui.util

import com.example.data.model.COUNTRIES
import com.example.data.model.SpaceListing
import java.util.Locale

/**
 * Where a listing is, as the host set it in the Create listing wizard: "City, Country" (country in the app language).
 * Unit-tested in ListingLocationTest. Legacy listings without a city fall back to their area, and only Lebanese ones (blank
 * country = Lebanon, like Explore's effectiveCountry) to the governorate — the enum defaulted to Beirut for every listing,
 * which made places abroad read "…, Beirut".
 */
object ListingLocation {
    const val DEFAULT_COUNTRY = "Lebanon"

    fun line(
        city: String,
        district: String,
        governorateLabel: String,
        country: String,
        countryLabel: (String) -> String,
        separator: String = ", "
    ): String {
        val countryEn = country.trim().ifBlank { DEFAULT_COUNTRY }
        val lebanese = countryEn.equals(DEFAULT_COUNTRY, ignoreCase = true)
        val place = city.trim()
            .ifBlank { district.trim() }
            .ifBlank { if (lebanese) governorateLabel.trim() else "" }
        return listOf(place, countryLabel(countryEn)).filter { it.isNotBlank() }.distinct().joinToString(separator)
    }

    /** A stored English country name in the app language ("Lebanon" → "لبنان" in Arabic); unknown names stay as typed. */
    fun countryLabel(countryEn: String): String {
        val iso = COUNTRIES.firstOrNull { it.name.equals(countryEn.trim(), ignoreCase = true) }?.isoCode ?: return countryEn
        val locale = UiText.resources?.configuration?.locales?.get(0) ?: Locale.getDefault()
        return Locale("", iso).getDisplayCountry(locale).ifBlank { countryEn }
    }
}

/** "City, Country" of this listing, in the app language. */
fun SpaceListing.locationLine(): String = ListingLocation.line(
    city = city,
    district = district,
    governorateLabel = localizedLabel(governorate.displayName),
    country = country,
    countryLabel = ListingLocation::countryLabel,
    separator = if (UiText.resources?.configuration?.locales?.get(0)?.language == "ar") "، " else ", "
)
