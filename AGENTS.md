# Aralıklı TV — coding agent instructions

## Objective
Build a **native Android TV** offline-first exam question study app with remote-control navigation, multi-course selection, persistent per-question progress, evidence-based spaced repetition, and a reliable APK build. Kotlin package: `com.daylak.araliklitv`.

## Current truth / limitations
- `app/src/main/assets/questions.json` currently has 116 automatically extracted **answer-key-matching** questions across two populated decks. These are not all human-verified against every source page. Other deck placeholders are empty, not imported data.
- `Scheduler.kt` contains a simple MVP scheduler. **It is NOT FSRS.** Do not claim FSRS is implemented.
- MainActivity uses Android Views. Compose for TV, Room, JSON USB import, testing on a physical TV and APK build have not been verified.
- Source PDFs are NOT included in this repository. Do not fabricate missing text or marked correct answers.

## Safety invariants
1. Never invent a question, an option, an answer, an exam year, or a source reference. Unreadable or ambiguous entries must be excluded from scored study and reported for review.
2. Retain stable `Question.id` values when importing/migrating, and preserve user progress (legacy SharedPreferences values must migrate correctly).
3. Do not silently overwrite or delete question progress. DB migrations need tests and rollback/backup plan.
4. A source's answer key can be inconsistent with real-world facts. Preserve source fidelity, flag contradictions, never silently "fix" the source.
5. Do not commit original PDFs, personal user data, secrets, API keys, keystores or exported answer histories.
6. App must be fully operable with D-pad, OK and Back. No touch required. Offline mode must always work.
7. Mark guessed/unverified features as TODO rather than completed.
8. Prefer incremental, reviewable PRs: first Gradle build+tests; next UI fixes; then persistent DB and documented FSRS implementation; then imports/coverage.

## Verification order
1. `python3 tools/validate_questions.py`
2. `gradle testDebugUnitTest`
3. `gradle assembleDebug`
4. Android TV emulator D-pad test, including back navigation and screen overflow.
5. Report what ran, what was blocked, and the exact APK artifact path.

## Scope of first Codex task
Read `docs/CODEX_GOREV.md` and `docs/VERI_DURUMU.md`, preserve existing behavior, run the above tests, and open a PR. Do not process/scavenge unrelated private files.
