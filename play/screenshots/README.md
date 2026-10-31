# Google Play screenshots

| Play Console field | Folder | Size | How the app is shown |
|---|---|---|---|
| Phone screenshots | `phone/` | 1080 × 1920 (9:16) | phone, 360 dp |
| 7-inch tablet screenshots | `tablet-7in/` | 1080 × 1920 (9:16) | tablet, app at 600 dp |
| 10-inch tablet screenshots | `tablet-10in/` | 1440 × 2560 (9:16) | tablet, app at 800 dp |
| Desktop screenshots | `desktop/` | 1920 × 1080 (16:9) | resizable app window (ChromeOS / Play Games on PC style) |
| Android XR screenshots | `android-xr/` | 1920 × 1080 (16:9) | app panel floating in Home Space |

Every folder holds the same 8 screens in the same order (file names below). On wide screens the app keeps its
layout centred at ≤ 840 dp (Discovery, Owner Hub, Progress, Profile), bottom sheets at ≤ 640 dp and the floating nav at
≤ 480 dp, following the Compose layouts (Material 3 caps sheets at 640 dp). All PNG, 24-bit, no alpha, each under 2.1 MB.

**Spatial XR video** (YouTube, 360°/180°/3D) can't be produced from screenshots: it needs a spatial capture or an
equirectangular 360° render uploaded to YouTube (public or unlisted, ads off, not age-restricted). The field is optional.

## Phone order and captions

Upload `01…08` to Play Console › Store presence › Main store listing, **in this order**
(the first three appear in search results, so they carry the specialist story; 06–08 speak to space owners).

| # | File | Caption on the image |
|---|------|----------------------|
| 1 | `01-explore-map.png` | Rooms for your practice, on one map |
| 2 | `02-search-and-filter.png` | By the hour, shift, day or month |
| 3 | `03-room-details.png` | See each room before you book |
| 4 | `04-book-slots.png` | Pick your slots, send a request |
| 5 | `05-my-rentals.png` | All your rentals in one place |
| 6 | `06-list-your-space.png` | List your rooms, fill empty hours |
| 7 | `07-accept-requests.png` | Accept requests in one tap |
| 8 | `08-occupancy-and-revenue.png` | Your occupancy and revenue at a glance |

Specs: 1080 × 1920 PNG (9:16), 24-bit, no alpha, each under 8 MB — meets Play's phone-screenshot and
promotion-eligibility requirements (≥ 4 screenshots, 9:16, ≥ 1080 px).

Policy notes (Play Store metadata policy): captions make no ranking, "best/#1", "free", price or discount claims; listing
names, people and amounts inside the screens are illustrative examples. Screen 1 is a real capture of the Explore map;
the others are rebuilt from the app's Compose layouts (theme colours, cards, booking bar, bottom navigation) so they show
the current UI.

Regenerate: `cd source && python render_shots.py [phone,tab7,tab10,desk,xr]` (Playwright + Pillow). Copy and layout live in
`source/shots.html` (`?f=<format>#<1-8>`); `map-wide.jpg` is a sharpened 2.5× crop of the real Explore map capture used
for the wide formats, `xr-room.jpg` the blurred room behind the XR panel.
