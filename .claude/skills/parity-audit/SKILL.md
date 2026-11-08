---
name: parity-audit
description: Prove a migrated ProHost screen/component looks and behaves identical to the original Android app. Use after every unit in docs/migration/PARITY.md, before ticking "Android diff = 0", and to build Android | iOS review sheets.
---

# Parity audit

Baseline: the untouched Android app v1.0.47 (source commit `d30c331`, app code unchanged at `7ed5728`), 280 renders from `DesignScreenshotTest`
(14 screens + 42 overlays × light, dark, small, rtl, ar) stored as a git commit under `refs/design-shots/golden-v1047` by the Design Screenshots workflow.

## Fetch a stored set
`python3 .claude/skills/prohost-design/scripts/fetch_shots.py --help` (reads `refs/design-shots/<name>` through the GitHub API; see `prohost-design/references/verify.md`).
If the script cannot reach the ref, download the `design-shots` artifact of the run (`mcp__github__actions_get download_workflow_run_artifact`).

## Produce a candidate
Actions → Design Screenshots → Run workflow on the branch with `store_as=<unit-name>` (and `only=<screen prefixes>` to keep it short). Never reuse `golden-v1047`.

## Compare (the gate)
```
python3 scripts/migration/diff_shots.py --golden <golden dir> --candidate <candidate dir> --only light/Explore,dark/Explore \
    --report out/diff.html --json out/diff.json
```
Exit 0 = identical (use `--channel-tol 2 --max-ratio 0.0005` only for proven anti-aliasing noise, and say so in PARITY.md). Open the HTML: golden | candidate | red diff.
The Compose/Material version change in P3-P4 may shift pixels legitimately; do not widen tolerances to hide it: fix tokens/components, or list a deviation for the owner.

## Android | iOS sheets
`python3 scripts/migration/compare_android_ios.py --android <dir> --ios <dir> --out out/sheets` then Read the PNGs/index. Expected platform-owned differences:
permission dialogs, Sign in with Apple, StoreKit sheet, photo picker, keyboard, status bar/home indicator, text rendering. Everything else must match; otherwise file a deviation.

## Beyond pixels
Unit tests green; `VectorsTest` + `functions/src/lib/vectors.test.ts`; string-key and plural counts unchanged (EN = AR, same placeholders); behaviour tests for flows without a
golden (back handling, sheets stop positions, nested-scroll nav pill, deep links); analytics event names unchanged.

## Record
PARITY.md row: Goldens (prefixes), Tests, ☑ Android diff = 0, ☑ iOS sheet, Status; deviations in DECISIONS.md. Never mark done on a failing or skipped check.
