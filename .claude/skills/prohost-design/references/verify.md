# Verifying a UI change

The cloud sandbox has no Android SDK (dl.google.com is blocked), so rendering happens in GitHub Actions.

## Design Screenshots workflow
- File: `.github/workflows/design-screenshots.yml`; test: `app/src/test/java/com/example/design/DesignScreenshotTest.kt`.
- Runs on pushes to `main` and `claude/**` that touch `app/src/main/java/com/example/ui/**`, `res/**` or the test,
  and on manual dispatch. It never gates the deploy.
- Renders each screen in `SCREENS` × `light`, `dark`, `small` (360 dp wide at the app's 1.15 text cap, `FontScaleCap`), `rtl` at 390 × 844 dp, Robolectric
  native graphics. A screen that throws writes `<screen>_<variant>.error.txt` instead of a PNG — read it, fix the
  harness (usually a missing hermetic dependency), don't ignore it.
- Normal `testDebugUnitTest` skips it (it only runs when `roborazzi.test.record=true`).

## Getting the images
Artifact zips are served from Azure blob storage, which the sandbox can't reach. The workflow therefore also
stores the PNGs as a git commit under `refs/design-shots/<branch>` (not a branch) and prints its sha as a
`design-shots` notice. Fetch them through api.github.com:
```bash
pip install -q pillow   # once per session, for contact sheets
python3 .claude/skills/prohost-design/scripts/fetch_shots.py "$(git rev-parse HEAD)" "$SCRATCH/shots-$(git rev-parse --short HEAD)"
```
It writes every PNG plus `sheets/<screen>.png` (light · dark · small · rtl · ar side by side). `Read` the sheets —
they display as images; open a full-size PNG to inspect details. For before/after, fetch the previous commit into
its own folder and compare the same sheet names. Render time ≈ 6–8 min after the push.

## Adding a screen
Add its name to `SCREENS` and a branch in `Screen()`: use a `*Content` composable with mock data from
`ui/preview/AppScreensPreview.kt`, or the real screen with `ProHostViewModel(hermeticRepository())`. Never construct
`ProHostViewModel()` without a repository (it reaches production Firebase).

## Gates before pushing to main
1. `node scripts/prohost-debugger.js` — 0 critical/high.
2. Push to the feature branch; wait for **Design Screenshots** and check every changed screen in all four variants.
3. Merge to `main`; **ProHost CI/CD** (unit tests + compile + backend) must go green.
4. Write in the commit/report what was checked visually and what wasn't.
