# ProHost data model (Firestore)

Where each piece of data lives, who writes it and who reads it. The app model is `data/model/DataModels.kt`
(`toFirestoreMap` / `fromFirestoreMap`); rules are `firestore.rules` (unit-tested in `tests/rules`).

## Listings: `workspace_listings/{spaceId}`

Written by the host's app (Create listing wizard, Manage page) through
`SpaceListing.keepingServerOwnedFields`; server-owned fields are denied to clients by the rules.

| Field | Meaning |
|---|---|
| `title`, `description`, `spaceCategoryId/Name`, `spaceType` | What the place is (category from the admin schema; `spaceType` is the legacy enum) |
| `imageUrls` | `[0]` is the brand cover (logo or brand photo). An undivided listing's photos follow it |
| `lat`, `lng` | The pin. Required to publish |
| `streetAddress`, `district`, `city` | Filled from the pin in the app's language (an Arabic app writes Arabic) and editable |
| `cityEn` | English copy of the city (1.0.45), so search, filters and the Instagram finder match either language |
| `country` | Standard English name. Blank on legacy listings = Lebanon (`effectiveCountry`) |
| `governorate` | Lebanese enum kept for legacy readers; nearest centre to the pin. Never shown for a listing abroad: cards print `locationLine()` = city, country (1.0.46) |
| `pricing` | `RentalPricingConfig` of an **undivided** listing (the whole place) |
| `capacity` | People an undivided listing fits (1.0.45); divisions carry their own |
| `subdivisions[]` | The divisions of a **divided** listing (see below). Empty for a whole place |
| `essentialFacilities`, `complementarySpecialties` | Shared facilities, discipline hashtags |
| `rules` | House rules. `visitorPolicy` is no longer asked; old values are kept |
| `schedule` | Opening hours and days of the space |
| `status` | `DRAFT` / `ACTIVE` / `PAUSED`; the server demotes an ACTIVE listing that fails `validateListingForPublish` |
| `favoriteCount`, `favoritesByMonth`, `unsavesByMonth`, `lastFavoriteEvents` | Saves (1.0.46, `users/favoritesSync.ts`): people who have it saved now (owner excluded), saves / un-saves per UTC month `yyyymm` (13 kept), and the last 50 trigger event ids so a retried event never counts twice. Maintenance `favrecount` rebuilds `favoriteCount` from `user_profiles.savedSpaceIds` |
| Server-owned | `isVerified`, `displayCode`, `isOwnerSuspended`, `isOwnerPackageLapsed`, counters, save fields, `searchName`, `ownerEmail` |

### Whole place or divisions

The wizard always configures divisions (cards in Step 3). `ui/util/ListingDivisions.kt` (unit-tested) maps them:

- **One division** that is not per-person and follows the space's hours → saved as the **whole place**:
  its pricing becomes `pricing`, its capacity `capacity`, its photos follow the cover, its amenities join
  `essentialFacilities`, `subdivisions = []`.
- **A per-person division, one with its own hours, or two or more** → `subdivisions[]`, `pricing` = default.
- Opening an undivided listing shows it as one card again.

Server checks (`listings/publishValidation.ts`) accept both shapes: a cover photo, a pin, and a real price on
the whole place or on at least one division.

### Division (`subdivisions[]` item)

`id` (`SUB-…`, stable: bookings reference it), `displayCode` (`D-…`, server), `name`, `type` (`Level2Type`),
`imageUrls`, `amenities`, `hashtags`, `pricing` (`RentalPricingConfig`), `pricingMode` (`STRATEGY_BASED` = fixed
price per slot, `PER_ATTENDEE` = price per person from `attendeeTiers`, slots carry availability markers),
`capacity` (fits up to N people), `minAttendees`, `attendeeTiers[]`, `scheduleOverride` (own hours).

## Bookings

- `booking_requests/{bookingId}`: created PENDING by a specialist; per-side transitions only (rules). Totals in
  `totalAmountUsd`. Price changes only through the `changeSlotPrice` callable.
- `booking_occupancy/{bookingId}`: person-free projection of ACCEPTED bookings (server-written, readable by any
  signed-in user) so specialists see held slots.

## People and accounts

- `user_profiles/{uid}`: profile; billing/entitlement mirror fields are server-only.
  `user_profiles/{uid}/notifications/{id}`: in-app notification centre (48 h).
- `display_codes/{code}`: registry of `U-`/`L-`/`D-`/`B-` codes (server-only).
- `subscriptions/{sha256(token)}`, `play_purchase_links`, `play_billing_pending`: Play billing (server-only).

## Instagram agent (`ig_*`, server-only)

The agent **reads** app data and **writes only its own collections**. This is enforced by
`functions/src/instagram/readOnlyAccess.test.ts`, which fails if any agent source writes outside `ig_*` and its
own config documents (`app_config/instagram`, `instagram_knowledge`, `instagram_offers`).

| Reads (read-only) | What for |
|---|---|
| `workspace_listings` (ACTIVE, publicly browsable) + `booking_occupancy` | Listing finder: rooms, types, capacity, prices (`listings/listingPrices.ts`), city / `cityEn` / area, facilities, views and bookings |
| `user_profiles`, own listings, own `booking_requests` | One consented lookup by registration email (`aiAccountInsight.ts`); facts are never recited back |
| Play catalog (`readPlayCatalog`) | Live offers only |

| Writes | |
|---|---|
| `ig_threads`, `ig_events`, `ig_contacts`, `ig_leads`, `ig_learn`, `ig_stats`, `ig_rules` | Conversations, dedupe, contacts, health |
| `app_config/instagram*` | Mode, knowledge and the 15-min offers cache |

## Other collections

- Server-only (rules deny clients): `mail`, `mailing_*`, `mail_campaigns`, `email_otps`, `email_send_limits`,
  `auth_password_invites`, `audit_security_logs`, `inquiry_rate_limits`, `maintenance_requests`, most of `app_config/*`.
- Public read, admin write: `schema_architecture` (categories, division types, amenities), `legal_documents` (+ immutable
  `versions`). Public read, server write: `system_metadata`. Signed-in read, server write: `hashtag_usage`.
- Retired (read-only for old apps or denied): `package_plans`, `promo_campaigns`, `subscription_formulas`,
  `whish_transactions`.


## Instagram multi-brand registry (server-only)

`ig_brands/{brandId}`: profile (name, assistantName, purpose, persona, knowledgeBase, buttons, playbooks, integrations, tabPrefix, contactEmail, languages),
operating settings (mode, killSwitch, settings, ai, sheetId, enabled) and `platforms.instagram` / `platforms.whatsapp` (account ids, app ids). Sub-collections:
events, contacts, threads, leads, learn, rules, stats, `private/secrets` (tokens, app secrets, AI keys), `private/knowledge`. The first brand, `prohost`, keeps
the original `ig_*` collections and `app_config/instagram*`, plus an optional `ig_brands/prohost` profile overlay.
