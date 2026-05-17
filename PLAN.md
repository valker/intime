# Intime v2 Implementation Plan

This document turns the product roadmap and technical notes into a concrete,
reviewable work plan for the `chatgpt` branch. It assumes `PRODUCT.md`,
`ROADMAP.md`, and `TECH_NOTES.md` remain the source of truth for behavior and
architecture direction.

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

Gaps that block a releasable v2:

- Scheduling still reads the database through legacy `InTimeOpenHelper` / raw
  SQLite while the UI uses Room.
- `MainActivityV2` does not reschedule alarms on app startup.
- `WorkManager` periodic work is still the main background entry point from the
  launcher activity; exact alarm reconciliation is not fully unified.
- Export backup is missing from settings.
- Room migration and failed-import rollback lack automated tests.
- No user-facing guidance for exact-alarm permission on Android 12+.
- UI does not yet meet all Phase 4 criteria (relative time, caution states,
  polished empty/error states).
- Release readiness (signing, R8, final icons, privacy policy) is not started.

## Goals

1. Make reminder behavior reliable and testable before large UI changes.
2. Use Room as the single source of truth for scheduling decisions.
3. Preserve product rules: ACK recalculates from now, one overdue state per task,
   calm follow-up reminders, full-replacement import.
4. Reach a releasable v2 without cloud, accounts, ads, or history analytics.

## Non-Goals (unchanged from PRODUCT.md)

- Cloud sync, accounts, collaboration, ads, paid features.
- Server-side logic and complex calendar recurrence.
- Timestamp history table and recommendations (future only).
- Full Compose rewrite in the first execution pass of this plan.

## Workstreams

### WS1: Unified Scheduling (Phase 1 + Phase 3)

**Problem:** `TaskRepository` mutates Room, but `AlarmUtil.setupAlarmIfRequired`
queries via `InTimeOpenHelper`. `AlarmReceiver` still uses `DatabaseUtil` on the
legacy path. This can diverge and is hard to test.

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
| S1.8 | Document final scheduling flow in `TECH_NOTES.md` | Diagram or numbered flow matches code |

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
| T2.4 | Add test: invalid import leaves existing tasks unchanged | Transaction rollback asserted |
| T2.5 | Add test: successful import replaces all tasks and triggers reschedule hook | Mockito/spy or integration hook verified |

---

### WS3: Backup and Migration (Phase 5)

**Tasks:**

| ID | Task | Done when |
|----|------|-----------|
| B3.1 | Document backup JSON format (short spec, example file) | `docs/backup-format.md` or section in `TECH_NOTES.md` |
| B3.2 | Add export to JSON from Settings (symmetric with import) | User can round-trip export → import on device |
| B3.3 | Validate import before delete (already intended; verify in code review) | Invalid file never calls `deleteAll` |
| B3.4 | Test migration from old production DB on a real backup | Checklist item recorded in plan or test readme |

---

### WS4: Permissions and Settings UX (Phase 1 + Phase 4)

**Tasks:**

| ID | Task | Done when |
|----|------|-----------|
| U4.1 | Settings section: notification permission state + link to app notification settings | User can recover from denial |
| U4.2 | Settings section: exact-alarm availability (Android 12+) with explanation | Copy matches product: precision matters for reminders |
| U4.3 | User-initiated button to open exact-alarm system settings (UI only, not from receivers) | No background settings intents |
| U4.4 | Show degraded-precision message when exact alarms unavailable | Visible on settings or list when relevant |

---

### WS5: UI MVP on Existing XML (Phase 4)

Complete the v2 experience on current AppCompat/XML before a Compose pilot.

**Tasks:**

| ID | Task | Done when |
|----|------|-----------|
| V5.1 | Task list sorted by `next_alarm` | Matches product "view tasks" flow |
| V5.2 | Visual states: upcoming, caution, due, overdue | User can scan list quickly |
| V5.3 | Relative time strings (today, tomorrow, in N hours, overdue by N days) | RU + EN resources |
| V5.4 | Empty state when no tasks | Clear CTA to add task |
| V5.5 | Permission-denied and import error states | No silent failures |
| V5.6 | Confirm delete on task details | Destructive action is confirmable |
| V5.7 | Normalize source encoding for Russian strings and comments | No garbled text in IDE or builds |

**Optional follow-up (separate PR after WS1–WS5):**

| ID | Task | Done when |
|----|------|-----------|
| V5.8 | Kotlin + Compose pilot for task list only | Coexists with legacy screens |

---

### WS6: Release Readiness (Phase 6)

Start only after WS1–WS3 acceptance checks pass.

**Tasks:**

| ID | Task | Done when |
|----|------|-----------|
| R6.1 | Configure release signing | Release build succeeds locally |
| R6.2 | Test release build with R8/ProGuard | No crash on cold start and ACK flow |
| R6.3 | Final app icon and adaptive icon | Dev icons removed from release manifest flavor |
| R6.4 | Privacy policy (offline, local-only data) | URL or bundled doc ready for Play Console |
| R6.5 | Play Console declarations aligned with permissions | `SCHEDULE_EXACT_ALARM`, notifications, boot |
| R6.6 | Changelog for existing users | Upgrade messaging ready |
| R6.7 | Smoke matrix: API 24, 31, 33, 35 | Documented results |

---

## Suggested Execution Order

```
WS1 (scheduling) → WS2 (tests) in parallel where possible
                 → WS3 (backup/export)
                 → WS4 (permissions UX)
                 → WS5 (UI polish)
                 → WS6 (release)
```

WS2 migration and import tests can start early if schema fixtures exist, but
scheduling unification (WS1) should merge before treating notification behavior
as stable.

## Sprint 1 Proposal (first implementation pass)

Scope for the first coding sprint after plan approval:

1. **S1.1–S1.6** — Room-based scheduling coordinator, startup + boot reschedule,
   align `AlarmReceiver` with Room.
2. **S1.7–S1.8** — Worker role clarification and `TECH_NOTES` scheduling doc.
3. **T2.4** — Failed import leaves DB unchanged (test).
4. **Manual smoke** — checklist from WS1 acceptance checks.

Out of scope for Sprint 1: Compose, export UI, release signing, full UI polish.

## Sprint 2 Proposal

1. **B3.1–B3.2** — Backup format doc + export.
2. **T2.1–T2.3, T2.5** — Calculator edge cases and migration tests.
3. **U4.1–U4.4** — Settings permission UX.
4. **V5.1–V5.3** — List sort, states, relative time.

## Sprint 3 Proposal

1. **V5.4–V5.7** — Empty/error states, delete confirm, encoding cleanup.
2. **B3.4, R6.1–R6.7** — Migration verification and release checklist.

## Review Checklist

Before implementation starts, please confirm or edit:

- [ ] Room-only scheduling (WS1) is the agreed first priority.
- [ ] `WorkManager` remains reconciliation-only, not primary exact reminders.
- [ ] Import stays full-replacement for v2; failed import must not wipe data.
- [ ] ACK action only on first newly overdue notification.
- [ ] XML UI completion before Compose pilot is acceptable.
- [ ] Sprint boundaries and ordering match your timeline.

## References

- `PRODUCT.md` — behavior and non-goals
- `ROADMAP.md` — phase definitions and done criteria
- `TECH_NOTES.md` — current risks and scheduling direction
