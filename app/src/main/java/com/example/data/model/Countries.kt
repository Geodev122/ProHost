package com.example.data.model

/**
 * A country entry used by both the registration form's "Country" dropdown and the
 * WhatsApp phone number field's country-code selector. [flagEmoji] is computed from
 * [isoCode] (ISO 3166-1 alpha-2) via Unicode regional indicator symbols rather than
 * hardcoded per-entry, so adding a country only ever needs its name/ISO code/dial code.
 *
 * This list is a curated, real-world-useful set (not the full ISO 3166 catalog) —
 * Lebanon first since this app's registrations are Lebanon-centric today, then the
 * rest alphabetically. Add more entries here if a registrant's country is missing;
 * nothing else in the app hardcodes this list's size or order.
 */
data class Country(
    val name: String,
    val isoCode: String,
    val dialCode: String
) {
    val flagEmoji: String
        get() = isoCode.uppercase().map { 0x1F1E6 + (it - 'A') }
            .joinToString("") { String(Character.toChars(it)) }

    val displayLabel: String get() = "$flagEmoji $name ($dialCode)"
}

val COUNTRIES: List<Country> = listOf(
    Country("Lebanon", "LB", "+961"),
    Country("Afghanistan", "AF", "+93"),
    Country("Algeria", "DZ", "+213"),
    Country("Argentina", "AR", "+54"),
    Country("Armenia", "AM", "+374"),
    Country("Australia", "AU", "+61"),
    Country("Austria", "AT", "+43"),
    Country("Bahrain", "BH", "+973"),
    Country("Bangladesh", "BD", "+880"),
    Country("Belgium", "BE", "+32"),
    Country("Brazil", "BR", "+55"),
    Country("Bulgaria", "BG", "+359"),
    Country("Cameroon", "CM", "+237"),
    Country("Canada", "CA", "+1"),
    Country("Chile", "CL", "+56"),
    Country("China", "CN", "+86"),
    Country("Colombia", "CO", "+57"),
    Country("Croatia", "HR", "+385"),
    Country("Cyprus", "CY", "+357"),
    Country("Czech Republic", "CZ", "+420"),
    Country("Denmark", "DK", "+45"),
    Country("Egypt", "EG", "+20"),
    Country("Ethiopia", "ET", "+251"),
    Country("Finland", "FI", "+358"),
    Country("France", "FR", "+33"),
    Country("Georgia", "GE", "+995"),
    Country("Germany", "DE", "+49"),
    Country("Ghana", "GH", "+233"),
    Country("Greece", "GR", "+30"),
    Country("India", "IN", "+91"),
    Country("Indonesia", "ID", "+62"),
    Country("Iran", "IR", "+98"),
    Country("Iraq", "IQ", "+964"),
    Country("Ireland", "IE", "+353"),
    Country("Israel", "IL", "+972"),
    Country("Italy", "IT", "+39"),
    Country("Ivory Coast", "CI", "+225"),
    Country("Japan", "JP", "+81"),
    Country("Jordan", "JO", "+962"),
    Country("Kenya", "KE", "+254"),
    Country("Kuwait", "KW", "+965"),
    Country("Libya", "LY", "+218"),
    Country("Malaysia", "MY", "+60"),
    Country("Mexico", "MX", "+52"),
    Country("Morocco", "MA", "+212"),
    Country("Netherlands", "NL", "+31"),
    Country("New Zealand", "NZ", "+64"),
    Country("Nigeria", "NG", "+234"),
    Country("Norway", "NO", "+47"),
    Country("Oman", "OM", "+968"),
    Country("Pakistan", "PK", "+92"),
    Country("Palestine", "PS", "+970"),
    Country("Philippines", "PH", "+63"),
    Country("Poland", "PL", "+48"),
    Country("Portugal", "PT", "+351"),
    Country("Qatar", "QA", "+974"),
    Country("Romania", "RO", "+40"),
    Country("Russia", "RU", "+7"),
    Country("Saudi Arabia", "SA", "+966"),
    Country("Senegal", "SN", "+221"),
    Country("Serbia", "RS", "+381"),
    Country("Singapore", "SG", "+65"),
    Country("South Africa", "ZA", "+27"),
    Country("South Korea", "KR", "+82"),
    Country("Spain", "ES", "+34"),
    Country("Sri Lanka", "LK", "+94"),
    Country("Sudan", "SD", "+249"),
    Country("Sweden", "SE", "+46"),
    Country("Switzerland", "CH", "+41"),
    Country("Syria", "SY", "+963"),
    Country("Thailand", "TH", "+66"),
    Country("Tunisia", "TN", "+216"),
    Country("Turkey", "TR", "+90"),
    Country("Uganda", "UG", "+256"),
    Country("Ukraine", "UA", "+380"),
    Country("United Arab Emirates", "AE", "+971"),
    Country("United Kingdom", "GB", "+44"),
    Country("United States", "US", "+1"),
    Country("Venezuela", "VE", "+58"),
    Country("Vietnam", "VN", "+84"),
    Country("Yemen", "YE", "+967")
)

fun findCountryByIsoCode(isoCode: String): Country =
    COUNTRIES.find { it.isoCode.equals(isoCode, ignoreCase = true) } ?: COUNTRIES.first()

fun findCountryByDialCode(dialCode: String): Country =
    COUNTRIES.find { it.dialCode == dialCode } ?: COUNTRIES.first()

/** [AppUser.country] stores the plain country name (e.g. "Lebanon"), not an ISO code. */
fun findCountryByName(name: String): Country =
    COUNTRIES.find { it.name.equals(name, ignoreCase = true) } ?: COUNTRIES.first()

/**
 * Formats a raw user-entered phone number input into a clean E.164 phone string (e.g., "+96170123456").
 * Removes leading zeroes, international prefixes (00 or +), and handles pasting full dial codes.
 */
fun formatToE164(country: Country, input: String): String {
    val digitsOnly = input.filter { it.isDigit() }
    val dialCodeDigits = country.dialCode.filter { it.isDigit() }

    val numberWithoutDialCode = when {
        digitsOnly.startsWith("00$dialCodeDigits") -> digitsOnly.removePrefix("00$dialCodeDigits").removePrefix("0")
        digitsOnly.startsWith(dialCodeDigits) && digitsOnly.length > dialCodeDigits.length + 4 -> digitsOnly.removePrefix(dialCodeDigits).removePrefix("0")
        else -> digitsOnly.removePrefix("0")
    }

    return "${country.dialCode}$numberWithoutDialCode"
}
