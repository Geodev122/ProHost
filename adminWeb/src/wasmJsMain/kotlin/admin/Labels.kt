package admin

import com.example.data.billing.PlayCatalog

/** "Google Play" / "App Store" / "Admin grant" for an `entitlementSource` or a subscription's `platform` (D6, 22 Nov 2026). */
fun storeLabel(sourceOrPlatform: String?): String = when (sourceOrPlatform?.trim()?.lowercase()) {
  "google_play", "android" -> "Google Play"
  "app_store", "ios" -> "App Store"
  "admin_forced" -> "Admin grant"
  null, "" -> "—"
  else -> sourceOrPlatform
}

/** A plan id (Play base plan, App Store product, or the admin grant) as the console names it, with the tier. */
fun planLabel(id: String?): String {
  val v = id?.trim()?.lowercase().orEmpty()
  if (v == "admin_forced" || v == "admin_unlimited_grant") return "Pro Host (granted)"
  val tier = if (PlayCatalog.tierOf(id) == PlayCatalog.PlanTier.SINGLE) "1 listing" else "Unlimited"
  val period = when { v.contains("year") -> " · Yearly"; v.contains("mon") -> " · Monthly"; else -> "" }
  return "ProHost Premium · $tier$period"
}
