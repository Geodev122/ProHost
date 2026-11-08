# Parity tracker

One row per unit (screen, sheet/dialog, component group, logic file). A unit is done only when every column is ticked and the owner has seen the contact sheet for phase gates.

Protocol: **Learn** (read source, list states/strings/callables) → **Plan** (5-line card) → **Execute** (Android first) → **Compare** (unit tests, golden diff vs `kmp-golden-v1047`, string/plural count, behaviour tests, Android | iOS sheet) → **Confirm** (deviations listed in DECISIONS.md) → **Proceed**.

| Unit | Source file | Class (A1/A2/B/C) | Seam | Goldens | Tests | Android diff = 0 | iOS sheet | Status | Date |
|---|---|---|---|---|---|---|---|---|---|
| _Populated in P0 from the exploration (130 non-admin units; class counts: A1 47 files, A2 25, B 38, C 20)._ | | | | | | | | | |

Platform-owned differences accepted on iOS (cannot be restyled): permission dialogs, Sign in with Apple button/sheet, StoreKit purchase sheet, photo picker, keyboard, status bar/home indicator, swipe-back, text rendering.
