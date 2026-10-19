# Motion

Motion explains a change the user caused. Material 3 motion tokens, expressed in Compose. Keep 1–2 animated
elements per view; no decorative loops except loaders (shimmer).

| Moment | Compose | Duration / easing |
|---|---|---|
| Tab ↔ tab (shell body) | `AnimatedContent(targetState = destination)` with `fadeIn(tween(180, delayMillis = 60)) togetherWith fadeOut(tween(120))` (fade-through) | 120 out / 180 in, `FastOutSlowInEasing` |
| Open a full-screen page (Premium, Manage listing) | `slideInVertically { it / 10 } + fadeIn` / reverse on exit | 250 in / 200 out, emphasized decelerate in, accelerate out |
| List → details | shared-axis X: `slideInHorizontally { it / 8 } + fadeIn` | 250 |
| Skeleton → content | `Crossfade(targetState = isLoading)` | 200 |
| List insert/remove | `Modifier.animateItem()` | defaults |
| Expand/collapse section | `AnimatedVisibility(expandVertically() + fadeIn())` | 200 |
| Selection (plan tile, chip) | `animateColorAsState`, `animateDpAsState` for border | 150 |
| Nav pill collapse (exists) | keep as is | — |
| Success (request sent, plan active) | one scale-in of the check icon (`spring(dampingRatio = 0.6f)`) + haptic Confirm | ~300 |

Rules
- Enter: decelerate (`LinearOutSlowInEasing`/emphasized decelerate). Exit: accelerate, shorter than enter.
- Interruptible: drive from state (`animate*AsState`, `AnimatedContent`), never from a fire-and-forget coroutine
  whose end the UI depends on.
- Reduced motion: if the user turned animations off (`Settings.Global.ANIMATOR_DURATION_SCALE` = 0), Compose
  `tween` still runs — read the scale once in the shell and pass `snap()` specs via a CompositionLocal
  (`LocalReducedMotion`) that shared transitions check.
- Never animate an open sheet's height (CLAUDE.md); animate content inside it instead.
- Sounds (request sent, purchase) go through the notification audio stream so silent mode mutes them.
