# Verifying a UI change

The cloud sandbox has no Android SDK (dl.google.com is blocked), so rendering happens in GitHub Actions.

## Design Screenshots workflow
- File: `.github/workflows/design-screenshots.yml`; test: `app/src/test/java/com/example/design/DesignScreenshotTest.kt`.
- Runs on pushes to `main` and `claude/**` that touch `app/src/main/java/com/example/ui/**`, `res/**` or the test,
  and on manual dispatch. It never gates the deploy.
- Renders each screen in `SCREENS` × `light`, `dark`, `font130` (font scale 1.3), `rtl` at 390 × 844 dp, Robolectric
  native graphics. A screen that throws writes `<screen>_<variant>.error.txt` instead of a PNG — read it, fix the
  harness (usually a missing hermetic dependency), don't ignore it.
- Normal `testDebugUnitTest` skips it (it only runs when `roborazzi.test.record=true`).

## Getting the images
```bash
# newest run for a commit
gh api "repos/geodev122/prohost/actions/runs?head_sha=$(git rev-parse HEAD)" \
  --jq '.workflow_runs[]|select(.name=="Design Screenshots")|[.id,.status,.conclusion]|@tsv'
# artifact id, then download + unzip into the scratchpad (never into the repo)
gh api repos/geodev122/prohost/actions/runs/<RUN>/artifacts --jq '.artifacts[]|[.id,.name]|@tsv'
gh api repos/geodev122/prohost/actions/artifacts/<ID>/zip > "$SCRATCH/shots-<sha>.zip"
unzip -o -q "$SCRATCH/shots-<sha>.zip" -d "$SCRATCH/shots-<sha>"
```
Then `Read` the PNGs — they display as images. For before/after, keep the previous commit's folder and compare
the same file names side by side; build a contact sheet with Python/Pillow if many changed.

## Adding a screen
Add its name to `SCREENS` and a branch in `Screen()`: use a `*Content` composable with mock data from
`ui/preview/AppScreensPreview.kt`, or the real screen with `ProHostViewModel(hermeticRepository())`. Never construct
`ProHostViewModel()` without a repository (it reaches production Firebase).

## Gates before pushing to main
1. `node scripts/prohost-debugger.js` — 0 critical/high.
2. Push to the feature branch; wait for **Design Screenshots** and check every changed screen in all four variants.
3. Merge to `main`; **ProHost CI/CD** (unit tests + compile + backend) must go green.
4. Write in the commit/report what was checked visually and what wasn't.
