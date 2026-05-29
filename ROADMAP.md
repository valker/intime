# Intime v2 Roadmap

This roadmap describes the path from the current `chatgpt` branch to a releasable
new version of Intime. The main principle is to preserve the reliability of the
existing reminder behavior while gradually introducing a modern UI and cleaner
architecture.

## Goal

Release a modern Android version of Intime that:

- keeps compatibility with existing user data;
- reliably reminds about periodic tasks;
- works offline without accounts, ads, or paid features;
- has a clear, modern UI;
- is maintainable for future development.

## v1 / v2 Code Strategy

Intime v2 is not a side-by-side rewrite where both UIs stay live. The repository may
still contain v1 code for reference, but only the v2 stack is built, shipped, and
extended.

### Active v2 code (used and evolving)

- Entry point and screens under the v2 flow, for example `MainActivityV2`,
  `AddTaskActivity`, `TaskDetailsActivity`, `SettingsActivity`.
- Shared infrastructure that v2 depends on: Room (`AppDatabase`, `TaskEntity`,
  `TaskDao`, `TaskRepository`), domain logic (`ReminderCalculator`),
  scheduling (`SchedulingCoordinator`), receivers and workers wired for v2.
- New features and bug fixes land here first.

### Legacy v1 code (reference only, not executed)

- Old UI and data-access paths kept in the repo as historical reference, for
  example `MainActivity`, `NewTaskActivity`, `DatabaseUtil`, `OneTask`,
  `TaskRecyclerViewAdapter`, and related XML layouts. These files could be removed at some moment.
- Legacy code must not be reachable at runtime:
  - not declared as launcher or exported entry points in `AndroidManifest.xml`;
  - not started via `Intent` from v2 code;
  - not referenced by v2 receivers, workers, repositories, or ViewModels.
- Legacy code does not need to compile against every new v2 change, but it
  should remain readable enough to compare behavior when migrating or debugging.

### Rules for new work

1. Do not add new dependencies from v2 code to legacy v1 classes.
2. Do not fix v2 bugs by extending legacy SQLite helpers (`InTimeOpenHelper`,
   `DatabaseUtil`) unless the change is explicitly a one-time migration step.
3. When behavior is moved to v2, update manifest and call sites so the old path
   is dead; leave the old file in place only as reference until cleanup.
4. Prefer deleting or moving legacy files to a clearly named package (for
   example `legacy/`) only after v2 parity is confirmed, not as part of every
   small change.

### Done when (code strategy)

- `MainActivityV2` is the only launcher activity.
- No v2 component imports or calls `MainActivity`, `NewTaskActivity`, `OneTask`,
  or `DatabaseUtil` for normal operation.
- Notification, boot, and ACK flows use Room and v2 repositories only.
- `TECH_NOTES.md` lists active vs legacy files and stays aligned with the manifest.

## Phase 0: Product Definition

Purpose: make the product rules explicit before larger implementation work.

Deliverables:

- `PRODUCT.md` with core concepts, scenarios, and non-goals.
- A short list of required user flows.
- A clear definition of task states: upcoming, caution, due, overdue, acknowledged.
- Product decisions for acknowledgement, overdue behavior, caution time,
  notification actions, and import behavior.

Done when:

- The expected behavior of task creation, acknowledgement, editing, deletion, and
  notification is written down.
- Acknowledgement is defined as recalculation from the current acknowledgement
  time.
- Import behavior is defined as full replacement for the current task list.
- Future AI-assisted changes can be checked against the same product rules.

## Phase 1: Technical Stabilization

Purpose: make the current app behavior safe before major UI changes.

Focus areas:

- Verify Room schema history and migrations.
- Confirm compatibility with the old production database.
- Review notification scheduling.
- Decide how `AlarmManager`, `WorkManager`, boot restore, and notification
  permissions should work together.
- Separate "just became overdue" notifications from later overdue reminder
  notifications.
- Create a notification channel for Android 8+.
- Restore or replace boot handling for scheduled reminders.

Done when:

- Existing tasks survive app upgrade.
- A newly created task triggers a reminder at the expected time.
- Acknowledgement recalculates the next reminder from the current time.
- Overdue tasks wait for acknowledgement and do not accumulate multiple overdue
  occurrences.
- Reminders still work after device reboot.
- Notification permission denial is handled gracefully.
- Opening any v2 screen dismisses all active Intime notifications.

## Phase 2: Architecture Modernization

Purpose: prepare the v2 codebase for long-term development while v1 code remains
in the repository only as reference.

Recommended direction:

- Follow the v1 / v2 code strategy above: evolve v2, do not wire legacy UI back
  into the running app.
- Use Kotlin for new application code.
- Use Room as the local source of truth for all runtime paths.
- Move business logic out of activities, receivers, and workers.
- Introduce a testable domain layer for interval and reminder calculations.
- Prefer coroutines and Flow for new asynchronous code.
- Introduce dependency injection only when it reduces real wiring complexity.
- Remove cross-links from v2 to legacy helpers (for example `MainActivity.isOnScreen`,
  `OneTask`, `DatabaseUtil` in receivers).

Done when:

- Task calculation logic can be tested without Android framework classes.
- New UI code does not directly manipulate database or scheduling internals.
- The app has a clear separation between UI, domain logic, data access, and
  scheduling.
- v2 entry points and background components do not depend on legacy v1 classes.

## Phase 3: Reminder Logic

Purpose: make the core of Intime trustworthy.

Focus areas:

- Preserve the core product rule: the next reminder is calculated from the
  current acknowledgement time.
- Cover interval types: minutes, hours, days, weeks, months, years.
- Cover quantization behavior.
- Cover edge cases: month length, leap years, daylight saving time, locale, and
  timezone changes.
- Prevent repeated due events for the same task while it waits for
  acknowledgement.
- Allow calm follow-up reminders about existing overdue tasks without making them
  too intrusive.

Done when:

- Reminder calculation has unit tests.
- Scheduling behavior is documented.
- The app can recover scheduled reminders after reboot or app update.

## Phase 4: New UI

Purpose: build the user-facing v2 experience on top of stable behavior.

MVP screens:

- Task list.
- Add/edit task.
- Task details.
- Settings.
- Import/export.
- Notification entry points, including quick `ACK` for a just-overdue task and
  task-list navigation for later overdue reminders.

UI priorities:

- Show tasks ordered by next due time.
- Make overdue tasks visually obvious.
- Show human-friendly relative time, for example "today", "tomorrow",
  "in 3 hours", or "overdue by 2 days".
- Keep acknowledgement available with minimal friction.
- Keep destructive actions confirmable.
- Make overdue reminders informative without encouraging accidental bulk
  acknowledgement.
- Clear all app notifications when the user opens the app (launcher or
  notification tap).

Done when:

- Main task management can be completed in the new UI.
- The UI works in Russian and English resources.
- Empty, loading, permission-denied, and error states are handled.

## Phase 5: Data, Backup, and Migration

Purpose: protect user data during the transition from v1 to v2.

Focus areas:

- Document the backup JSON format.
- Validate imports before replacing local data.
- Keep import behavior as full replacement of the current task list for v2.
- Keep Room schema exports up to date.
- Test migration from old database versions.
- Prepare for future storage of reminder and acknowledgement timestamp history.
- Decide final package/application ID strategy for release builds.

Done when:

- A user can update from the old app without losing tasks.
- Backup import/export works with representative real data.
- Bad backup files produce a clear error and do not corrupt the database.
- Import either completes fully or leaves the existing task list unchanged.

## Phase 6: Release Readiness

Purpose: prepare the app for public distribution and future maintenance.

Checklist:

- Release signing is configured.
- R8/ProGuard release build is tested.
- App icon and adaptive icon are final.
- Play Console declarations match actual app behavior.
- Privacy policy explains offline/local-only behavior.
- Smoke tests pass on supported Android versions.
- Changelog is prepared for existing users.

Done when:

- A release APK/AAB can be built reproducibly.
- Upgrade from the old version has been tested.
- The app is ready for staged rollout.

## Near-Term Backlog

1. Write tests for reminder date calculation.
2. Review current notification implementation.
3. Add notification channel creation.
4. Add user-facing exact-alarm settings guidance for Android 12+.
5. Restore boot-time reminder rescheduling.
6. Extract reminder calculation into a testable class.
7. Define notification actions for just-overdue versus already-overdue tasks.
8. Document and test full-replacement import behavior.
9. Start Kotlin/Compose migration with the task list screen.
10. Disconnect v2 from legacy v1: replace `MainActivity.isOnScreen` with a v2-owned
    visibility flag; ensure receivers and ACK use only `TaskRepository` / Room.
11. Audit manifest and imports so legacy activities are not registered or referenced.
