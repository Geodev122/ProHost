# Play Store listing text (ASO)

`en-US.txt` and `ar.txt`: paste into Play Console › Grow users › Store presence › Main store listing
(Arabic under "Manage translations"). Limits: title ≤ 30, short description ≤ 80, full description ≤ 4000 characters
(all within limits). Screenshots: `../screenshots/phone/`.

Keyword focus (Play indexes the title, short and full description): rent / book · clinic room · therapy room · office ·
meeting room · training room · studio · by the hour · shift · per attendee · Beirut · Lebanon. Play's metadata policy
forbids ranking/"best"/"#1"/price or promotion claims in the title, icon and short description — keep the launch offer
(4 months free monthly, 50% off year one — `functions/src/billing/offerClaims.ts`) in promotional content (Promotional content / LiveOps), not in the listing text.

## App icon

`icon-512.png` is the Play listing icon (512×512, full bleed — Google rounds the corners; upload it under Store presence ›
App icon). The same mark ships in the app: adaptive layers `mipmap-*/ic_launcher_{foreground,background}.png` (mark inside
the 66 dp safe zone) and legacy/round `ic_launcher{,_round}.png`. Steel Blue → navy field, white short stroke, orange long
stroke; no text, so it stays legible at launcher sizes.
