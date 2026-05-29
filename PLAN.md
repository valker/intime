# Intime v2 Implementation Plan

This document turns the product roadmap and technical notes into a concrete,
reviewable work plan for the `chatgpt` branch. It assumes `PRODUCT.md`,
`ROADMAP.md`, and `TECH_NOTES.md` remain the source of truth for behavior and
architecture direction.

## v1 / v2 Code Strategy (from ROADMAP)

- **v2 is the only running app:** `MainActivityV2` is the launcher; v2 screens,
  Room, `TaskRepository`, and `SchedulingCoordinator` are extended for all new work.
- **v1 stays in the repo for reference:** `MainActivity`, `NewTaskActivity`,
  `DatabaseUtil`, `OneTask`, etc. are not registered as entry points and must not
  be called from v2 code. These files could be removed at some moment.
- **No new v2 → v1 dependencies.** Fixes to scheduling, ACK, and UI do not go
  through legacy SQLite helpers.
- **Cleanup is optional later:** move legacy files to a `legacy/` package or delete
  after v2 parity, not as part of every feature PR.

## Current Baseline

Work already present on the branch:

- Product rules documented in `PRODUCT.md`.
- Room persistence with `TaskEntity`, `TaskDao`, and `TaskRepository`.
- `ReminderCalculator` with unit tests for intervals, quantization, and calendar
  edge cases.
- `MainActivityV2` and v2 screens for list, add/edit, details, and settings.
- `ViewModel` usage on main flows.
- `NotificationHelper` with an overdue notification channel.
- `AlarmManager` scheduling with exact-alarm fallback in `AlarmUtil`.
- `BootReceiver` registered in the manifest.
- Import from JSON with transactional full replacement and parsing tests.
- Split notification intent: `AlarmReceiver` may offer `ACK` for a newly overdue
  task; `TaskNotificationWorker` opens the task list without `ACK`.
- **WS1 (scheduling):** `SchedulingCoordinator` on Room; boot + cold start
  reschedule; `ImportReplacement` + tests; scheduling documented in `TECH_NOTES.md`.
- **ACK from notification:** `AckReceiver` uses `TaskRepository` on a background
  thread; `SchedulingCoordinator` refuses main-thread Room access.
- **Notification dismiss on open:** `V2Activity` clears all app notifications in
  `onStart` (launcher or notification tap).

Gaps that block a releasable v2:

- **Legacy tree:** v1 classes remain in source (intentional) but must not gain new
  runtime call sites; periodic manifest/import audits.
- Export backup is missing from settings.
- Room migration instrumentation tests (v5 → v6) not yet added.
- No user-facing guidance for exact-alarm permission on Android 12+.
- UI does not yet meet all Phase 4 criteria (relative time, caution states,
  polished empty/error states).
- Release readiness (signing, R8, final icons, privacy policy) is not started.

## Goals

1. Make reminder behavior reliable and testable before large UI changes.
2. Use Room as the single source of truth for scheduling decisions.
3. Preserve product rules: ACK recalculates from now, one overdue state per task,
   calm follow-up reminders, full-replacement import.
4. Keep v1 code archived in-repo while **only v2** is built, shipped, and extended.
5. Reach a releasable v2 without cloud, accounts, ads, or history analytics.

## Non-Goals (unchanged from PRODUCT.md)

- Cloud sync, accounts, collaboration, ads, paid features.
- Server-side logic and complex calendar recurrence.
- Timestamp history table and recommendations (future only).
- Full Compose rewrite in the first execution pass of this plan.

## Workstreams

### WS0: Disconnect v2 from legacy v1 (Phase 2)

**Problem:** v2 runtime still touches v1 types (`MainActivity.isOnScreen`). That
blurs the “reference only” rule and causes confusion when fixing v2 bugs.

**Tasks:**

| ID | Task | Done when |
|----|------|-----------|
| L0.1 | Introduce v2-owned foreground visibility flag (no `MainActivity` import) | Done (`UiVisibility`) |
| L0.2 | v2 activities track visibility via `V2Activity`; session timestamp on launcher pause | Done |
| L0.3 | Audit `AndroidManifest.xml`: no legacy activities as launcher or exported | Done (only `MainActivityV2`) |
| L0.4 | Grep audit: no v2 → `DatabaseUtil` / `OneTask` / `NewTaskActivity` imports | Done (v2 runtime paths clean) |
| L0.5 | Align `TECH_NOTES.md` active vs legacy file lists with manifest | Done |

**Acceptance:** v2 APK behavior unchanged; legacy files remain in tree but are
unreachable from v2 code paths.

---

### WS1: Unified Scheduling (Phase 1 + Phase 3) — largely done

**Problem (resolved):** scheduling used raw SQLite while UI used Room. Replaced by
`SchedulingCoordinator` + `TaskDao`.

**Remaining:** manual smoke on device; optional unit test for `getNearestFutureTask`.

**Target model** (from `TECH_NOTES.md`):

1. Room is the source of truth.
2. Schedule only the nearest future due task with `AlarmManager`.
3. When the alarm fires, show the first-due notification for newly overdue
   tasks; include quick `ACK` only in that case.
4. Use exact alarms when available; otherwise fall back to inexact alarms.
5. After create, edit, delete, acknowledge, import, app startup, and boot,
   reschedule the nearest alarm.
6. Use `WorkManager` only for periodic reconciliation and calm reminders about
   tasks that are already overdue (no `ACK` action).
7. Do not mark tasks as notified when `POST_NOTIFICATIONS` is denied.

**Tasks:**

| ID | Task | Done when |
|----|------|-----------|
| S1.1 | Add `TaskDao` query for the nearest `next_alarm` in the future | Query is covered by a test or documented contract |
| S1.2 | Introduce `SchedulingCoordinator` (or equivalent) that wraps alarm schedule/cancel | All reschedule calls go through one class |
| S1.3 | Rewrite `AlarmUtil.setupAlarmIfRequired` to use Room, not `InTimeOpenHelper` | No raw SQLite in alarm setup |
| S1.4 | Update `AlarmReceiver` to use Room / repository APIs for overdue count and `markTaskNotified` | Legacy `DatabaseUtil` removed from receiver path |
| S1.5 | Call reschedule from `MainActivityV2` (or `Application`) on cold start | Nearest alarm restored after opening app |
| S1.6 | Ensure `BootReceiver` uses the same coordinator | Reboot smoke test passes |
| S1.7 | Review `TaskNotificationWorker`: only reconciliation / follow-up overdue reminders | Worker does not replace exact first-due notifications |
| S1.8 | Document final scheduling flow in `TECH_NOTES.md` | Done |
| S1.9 | `SchedulingCoordinator` safe on main thread (dispatches to executor) | No Room crash from UI/legacy callers |

**Acceptance checks (manual):**

- New task fires at expected time.
- ACK moves `next_alarm` from acknowledgement time, not old planned time.
- Reboot restores the next alarm.
- Denied notification permission does not corrupt notified state.
- Second notification for same overdue task has no `ACK` and opens task list.

---

### WS2: Tests and Edge Cases (Phase 3 + Phase 5)

**Tasks:**

| ID | Task | Done when |
|----|------|-----------|
| T2.1 | Extend `ReminderCalculatorTest` for minutes/weeks and DST/timezone cases | Tests pass in CI/local `./gradlew test` |
| T2.2 | Add test: overdue task does not get a second "new overdue" event before ACK | Domain or DAO-level test exists |
| T2.3 | Add instrumentation or migration test from schema v5 fixture to v6 | Upgrade path verified |
| T2.4 | Add test: invalid import leaves existing tasks unchanged | Done (`ImportReplacementTest`) |
| T2.5 | Add test: successful import replaces all tasks and triggers reschedule hook | Mockito/spy or integration hook verified |

---

### WS3: Backup and Migration (Phase 5)

**Tasks:**

| ID | Task | Done when |
|----|------|-----------|
| B3.1 | Document backup JSON format (short spec, example file) | Done (`TECH_NOTES.md`) |
| B3.2 | Add export to JSON from Settings (symmetric with import) | Done (`BackupExport`, Settings) |
| B3.3 | Validate import before delete (already intended; verify in code review) | Invalid file never calls `deleteAll` |
| B3.4 | Test migration from old production DB on a real backup | ✓ Done (instrumentation tests + documentation) |

---

### WS4: Permissions and Settings UX (Phase 1 + Phase 4)

**Tasks:**

| ID | Task | Done when |
|----|------|-----------|
| U4.1 | Settings section: notification permission state + link to app notification settings | ✓ Done (SettingsActivity + UI) |
| U4.2 | Settings section: exact-alarm availability (Android 12+) with explanation | ✓ Done (status check + user button) |
| U4.3 | User-initiated button to open exact-alarm system settings (UI only, not from receivers) | ✓ Done (button opens system settings) |
| U4.4 | Show degraded-precision message when exact alarms unavailable | ✓ Done (status text updated) |
| U4.5 | Dismiss all app notifications when a v2 screen opens | Done (`V2Activity` + `NotificationHelper`) |

---

### WS5: UI MVP on Existing XML (Phase 4)

Complete the v2 experience on current AppCompat/XML before a Compose pilot.

**Tasks:**

| ID | Task | Done when |
|----|------|-----------|
| V5.1 | Task list sorted by `next_alarm` | Done (Room query) |
| V5.2 | Visual states: upcoming, caution, due, overdue | Done (`RelativeTimeFormatter`, list colors) |
| V5.3 | Relative time strings (today, tomorrow, in N hours, overdue by N days) | Done (EN + RU plurals) |
| V5.4 | Empty state when no tasks | ✓ Done (empty state layout + toggle logic) |
| V5.5 | Permission-denied and import error states | ✓ Done (AlertDialog + warning banner) |
| V5.6 | Confirm delete on task details | ✓ Done (confirm dialog with icon) |
| V5.7 | Normalize source encoding for Russian strings and comments | ✓ Done (UTF-8 in build.gradle) |

**Optional follow-up (separate PR after WS1–WS5):**

| ID | Task | Done when |
|----|------|-----------|
| V5.8 | Kotlin + Compose pilot for task list only | v2-only; does not reference legacy activities |

---

### WS6: Release Readiness (Phase 6)

**Tasks:**

| ID | Task | Done when |
|----|------|-----------|
| R6.1 | Configure release signing | ✓ Done (signing config in build.gradle + docs) |
| R6.2 | Test release build with R8/ProGuard | ✓ Done (build successful, 3.0 MB APK, test plan created) |
| R6.3 | Final app icon and adaptive icon | ✓ Ready (design guide created, awaiting designer) |
| R6.4 | Privacy policy (offline, local-only data) | ✓ Done (PRIVACY_POLICY.md) |
| R6.5 | Play Console declarations aligned with permissions | ✓ Done (`google_play/PLAY_CONSOLE_DECLARATIONS.md`) |
| R6.6 | Changelog for existing users | ✓ Done (CHANGELOG.md) |
| R6.7 | Smoke matrix: API 24, 31, 33, 35 | Documented results |

**Documentation:** See `RELEASE.md` for detailed instructions for all tasks.

---

## Suggested Execution Order

```
WS0 (disconnect v2 from legacy) — early, small PRs alongside other work
WS1 (scheduling) — done; device smoke only
WS2 (tests) ─┬─ parallel
WS3 (backup) ┘
→ WS4 (permissions UX)
→ WS5 (UI polish)
→ WS6 (release)
```

WS0 should complete before large UI refactors so new screens do not copy
`MainActivity` patterns. WS2 migration tests can proceed in parallel with WS3.

## Sprint 1 — completed

1. **S1.1–S1.9** — Room scheduling, receivers, startup/boot, `TECH_NOTES` doc.
2. **T2.4** — `ImportReplacementTest`.
3. **ACK fix** — `AckReceiver` / `TaskRepository` off main thread.
4. **Manual smoke** — still recommended on emulator/device.

## Sprint 2 Proposal (current)

1. **L0.1–L0.5** — Remove v2 → legacy coupling; manifest/import audit.
2. **B3.1–B3.2** — Backup format doc + export.
3. **T2.1–T2.3, T2.5** — Calculator edge cases and migration tests.
4. **U4.1–U4.4** — Settings permission UX. ✓ DONE
5. **V5.1–V5.3** — List sort, states, relative time.

## Sprint 4 (current) — Release Readiness

1. **R6.1–R6.7** — Release preparation checklist
   - R6.1: ✓ Done (release signing configured)
   - R6.2: ✓ Done (release build successful, 3.0 MB)
   - R6.3: App icons (requires graphic design)
   - R6.4: ✓ Done (PRIVACY_POLICY.md)
   - R6.5: ✓ Done (Play Console declarations documented)
   - R6.6: ✓ Done (CHANGELOG.md)
   - R6.7: Smoke tests (final testing)

## Sprint 3 — COMPLETED ✅

1. **V5.4–V5.7** — Empty/error states, delete confirm, encoding cleanup. ✓ ALL DONE
   - V5.4: ✓ Done (empty state with CTA button)
   - V5.5: ✓ Done (AlertDialog + permission warning banner)
   - V5.6: ✓ Done (confirm delete dialog with icon)
   - V5.7: ✓ Done (UTF-8 encoding in build.gradle)

2. **B3.4** — Migration verification. ✓ DONE
   - Added 4 instrumentation tests for migration safety
   - Documented migration process in TECH_NOTES.md
   - Verified data integrity across migration

**Sprint 3 is ready for release prep (Sprint 4).**

## Review Checklist

- [x] Room-only scheduling (WS1) for runtime paths.
- [x] v1 code in repo as reference; v2 is the only running stack (see ROADMAP).
- [x] No v2 imports of legacy UI/data helpers (WS0).
- [x] Notification and exact-alarm permission UI with user settings access (U4.1–U4.4).
- [ ] `WorkManager` remains reconciliation-only, not primary exact reminders.
- [ ] Import stays full-replacement for v2; failed import must not wipe data.
- [ ] ACK action only on first newly overdue notification.
- [ ] XML UI completion before Compose pilot is acceptable.
- [ ] Sprint boundaries and ordering match your timeline.

## References

- `PRODUCT.md` — behavior and non-goals
- `ROADMAP.md` — phase definitions, **v1 / v2 code strategy**, done criteria
- `TECH_NOTES.md` — active vs legacy files, risks, scheduling direction
