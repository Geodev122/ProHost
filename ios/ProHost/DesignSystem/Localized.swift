import Foundation

/// Text from the String Catalog by Android key (same keys as `R.string.*`). Never hard-code user-facing text.
func L(_ key: String) -> String {
    Bundle.main.localizedString(forKey: key, value: nil, table: nil)
}

/// Formatted text: Android `%1$s` / `%d` placeholders arrive converted (`%1$@`, `%lld`).
func L(_ key: String, _ args: CVarArg...) -> String {
    String(format: L(key), locale: Locale.current, arguments: args)
}
