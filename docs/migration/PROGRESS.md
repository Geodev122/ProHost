# Migration progress log

One entry per executed step, newest at the bottom. Every step names its evidence (CI run, file) and the unit protocol outcome (learn → plan → execute → compare → confirm). Plan: `MASTER_PLAN.md`; decisions and findings: `DECISIONS.md`; per-file tracker: `PARITY.md`; spike results: `SPIKE_REPORT.md`.

Gate tooling in place (8 Nov 2026): `Parity Gate` workflow (build + unit tests + R8 + 280-render diff vs `refs/design-shots/golden-v1047`, never deploys), `Migration Checks` (shared TS/Kotlin test vectors, parity-table check, diff-tool tests), `scripts/migration/{diff_shots,fetch_ref,compare_android_ios,export_vectors,gen_parity_table,web_smoke}`.

## P0 (freeze, knowledge, tooling) — 8 Nov 2026
| Step | Result | Evidence |
|---|---|---|
| Golden baseline of the untouched app (280 renders) | done; reproducible (3 renders, 280/280 identical with 2 masked dynamic regions) | `refs/design-shots/golden-v1047` (commit `d841ccc`), runs 42474484795 / 42475366438 / 42476676584 |
| Shared Kotlin/TypeScript test vectors (booking terms, listing prices, legacy pricing) | done; Kotlin and TypeScript both pass | `tests/vectors/*.json`, Migration Checks run 42476213531 |
| **Fix requested by the owner:** server booking end dates now clamp to the last day of the month like the app | done; 12 month-end cases pass on both sides; app code unchanged; goes live when merged to `main` | commit `e9aff19`, `DECISIONS.md` |
| Skills, subagents, scripts, parity tables (147 files, 70 admin items) | done | `.claude/skills/*`, `.claude/agents/*`, `PARITY.md`, `ADMIN_PARITY.md` |
| Toolchain spike (rounds 1-4) | Kotlin 2.3.20 + CMP 1.12.2 + AGP 9.4.1 + Gradle 9.7.1 builds Android, iOS (+ simulator tests), wasmJs, JVM host; Arabic resources pass | run 42478543724, `SPIKE_REPORT.md` |
| Spike rounds 5-6 (Splash parity, font cap, web + Firebase JS) | running | `spike-kmp.yml` run 7 |

## P2 (de-Android the Android app in place, no visible change)
### P2.1 Remove dependencies nothing uses — executed locally, awaiting Parity Gate
- **Learn:** `app/build.gradle.kts` declared Navigation-Compose, Room (runtime + ktx), KSP with two processors (Room compiler, Moshi codegen) and the serialization compiler plugin. Greps over `app/src` (main, test, androidTest): no import of `androidx.navigation`, `androidx.room`, `com.squareup.moshi`, `retrofit2`, `androidx.camera`, `androidx.datastore`; no `@Entity`/`@Dao`/`@Database`/`@JsonClass`/`@Serializable` anywhere, so KSP had nothing to process. `kotlinx.serialization.json` **is** used (by `VectorsTest`) and is kept; OkHttp is on the classpath through Coil, so its `-dontwarn` stays.
- **Plan / Execute:** removed the 3 `implementation` lines, 2 `ksp` lines, 2 plugin aliases (app + root), stale commented camera/datastore lines, 16 unused version-catalog entries and 14 unused library aliases; proguard: removed only keeps for classes no longer on the classpath (Room, Moshi, Retrofit), kept all `-keepattributes` and `-dontwarn` guards. A script confirmed every `libs.*` alias used by the build files still exists in the catalog.
- **Compare, run 1 (42480395541, head 5f1826e):** compile debug + release, all unit tests and the **R8 release shrink PASS**; 280 renders: **275 identical, 5 differ** (`request_detail_*`, 0.011-0.017 %, ≈300-450 px), 0 missing/extra/resized. Those are the clock-time renders already masked in a 59x46 px box, so the residue is probably the clock text spilling past the mask, not P2.1 (no Kotlin source changed). Not ticked: the claim is checked, not assumed.
- **Next:** `diff_shots.py` now prints the bounding box of every changed file (unit-tested), so the next gate log shows where the residue is. Same box on the clock line ⇒ enlarge only that mask to the full clock text and record the evidence in `golden-masks.json`/DECISIONS.md; anywhere else ⇒ real regression, bisect. Tolerance is never widened.
- **Confirm:** tick after a gate run shows 280/280.

## Open owner items created by this work
See `docs/OWNER_TODO.md` items 50-53 and `DECISIONS.md` "Open owner decisions".
