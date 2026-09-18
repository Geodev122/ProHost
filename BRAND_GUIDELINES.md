# 🎨 ProHost Official Brand Identity & Design System

**ProHost** is engineered as a high-prestige commercial real estate and clinical workspace marketplace. The brand aesthetic merges **executive authority** with **clinical hygiene** and **warm human energy**.

---

## 🏛️ 1. Core Brand Color Palette

| Color Name | Hex Code | Visual Sample | Primary Brand Role |
| :--- | :---: | :---: | :--- |
| **Oxford Blue** | `#384152` | 🟦 | **Primary Brand Anchor**: Headers, Top App Bar, Primary Navigation & Structural Framework. |
| **Carnation Orange** | `#F25F4C` | 🟧 | **Primary Action & CTA**: High-conversion buttons, "Book Now" actions, highlight badges, and booking confirmations. |
| **Fresh Green** | `#4CAF72` | 🟩 | **Availability & Cedar Assurance**: Verified status badges, live availability slots, and Lebanese Cedar regional identity. |
| **Cool Gray** | `#283544` | ⬛ | **Typography & Architecture**: Primary text, card headers, titles, dark neutral surfaces, and borders. |
| **Light Gray** | `#E2E4E8` | ⬜ | **Background Canvas**: Card backgrounds, subtle dividers, search bar fills, and workspace containers. |
| **Pure White** | `#FFFFFF` | ⚪ | **Clean Surfaces**: Card surfaces, dialog backgrounds, and crisp text contrast on dark cards. |

---

## ⚡ 2. Accent & Regional Partner Colors

- **Vibrant Blue (`#246BEE`)**: Category icons, location badges, and AI/smart matching accents.
- **Bright Orange (`#F98B1D`)**: Pending host review, reservation holds, and alert warnings.
- **Crimson Red (`#D32F2F`)**: Cancelled applications and error alerts.
- **Whish Brand Red (`#E2001A`)**: Payment sheet branding for Whish Money Lebanon transactions.
- **WhatsApp Green (`#25D366`)**: Instant direct host communication action button.

---

## 💖 3. Brand Feels, Tones & Emotions

```
                  ┌───────────────────────────────────────────┐
                  │          THE PROHOST EXPERIENCE           │
                  └─────────────────────┬─────────────────────┘
                                        │
        ┌───────────────────────────────┼───────────────────────────────┐
        ▼                               ▼                               ▼
 🛡️  TRUST & AUTHORITY            ✨ VITALITY & ACTION             🍃 HYGIENE & BREATHABILITY
   (Oxford Blue / Cool Gray)      (Carnation Orange / Vibrant Blue)      (Fresh Green / Light Gray)
• Executive Professionalism     • Instant Host Engagement        • Uncluttered Clinical Workspaces
• Server-Verified Security     • Flex-Leasing Empowerment       • Transparent & Open Pricing
• Financial Peace of Mind      • Modern Workspace Innovation    • Serene Practice Environment
```

1. **Prestige & Authority (Oxford Blue `#384152`)**:
   - Gives doctors, specialists, and corporate hosts confidence that ProHost is a serious, secure, institutional platform.

2. **Action & Innovation (Carnation Orange `#F25F4C`)**:
   - Breaks the monotony of traditional corporate software with warm, human-centric energy that drives rentals and bookings.

3. **Assurance & Health (Fresh Green `#4CAF72`)**:
   - Evokes immediate peace of mind — green signifies open availability, verified hosts, and active leases.

4. **Clarity & Hygiene (Pure White `#FFFFFF` & Light Gray `#E2E4E8`)**:
   - Provides an airy, un-crowded canvas that feels like stepping into a fresh, well-lit medical suite or private executive office.

---

## 🌈 4. Dynamic Listing Category Markers

ProHost uses distinct dual-gradient markers on interactive maps to represent space categories:

- **Private Office (ST-01)**: `#246BEE` *(Vibrant Blue)*
- **Center / Suite (ST-02)**: `#F25F4C` *(Carnation Orange)*
- **Polyclinic / Medical (ST-03)**: `#4CAF72` *(Fresh Green)*
- **Co-working Space (ST-04)**: `#8B5CF6` *(Vibrant Violet)*
- **Executive Boardroom (ST-05)**: `#C99A2E` *(Executive Gold)*
- **Consultation Suite (ST-06)**: `#14B8A6` *(Clinical Teal)*

---

## 🚀 Live Material 3 Theme Integration

The brand palette is enforced globally in the Kotlin codebase via `Color.kt` and `Theme.kt`:

```kotlin
// Material 3 Light Theme Mapping
primary = OxfordBlue         // #384152
secondary = CarnationOrange   // #F25F4C
tertiary = FreshGreen        // #4CAF72
background = PureWhite       // #FFFFFF
onSurface = CoolGray         // #283544
surfaceVariant = LightGray   // #E2E4E8
```
