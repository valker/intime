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

## Runtime Code Strategy

Intime is no longer a side-by-side rewrite. The old v1 UI/data-access tree has
been removed, and only the current Room-backed app stack is built, shipped, and
extended.

### Active code (used and evolving)

- Entry point and screens under the v2 flow, for example `MainActivityV2`,
  `AddTaskActivity`, `TaskDetailsActivity`, `SettingsActivity`.
- Shared infrastructure that v2 depends on: Room (`AppDatabase`, `TaskEntity`,
  `TaskDao`, `TaskRepository`), domain logic (`ReminderCalculator`),
  scheduling (`SchedulingCoordinator`), receivers and workers wired for v2.
- New features and bug fixes land here first.

### Removed v1 code

- Old UI and data-access paths have been deleted from the working source tree,
  including the legacy activities, SQLite helpers, adapters, custom views, and
  v1-only layouts.
- Historical behavior can still be inspected through git history when needed.
- The manifest must keep `MainActivityV2` as the only launcher entry point.

### Rules for new work

1. Do not restore deleted v1 activities, adapters, layouts, or SQLite helpers for
   normal runtime behavior.
2. Fix scheduling, ACK, persistence, and UI through the current Room-backed stack.
3. Keep manifest, documentation, and imports aligned with the current-only source
   tree.
4. Treat any old-database compatibility code as an explicit migration concern, not
   as an alternate runtime data path.

### Done when (code strategy)

- `MainActivityV2` is the only launcher activity.
- Deleted v1 classes are not imported, registered, or recreated for normal
  operation.
- Notification, boot, and ACK flows use Room and v2 repositories only.
- `TECH_NOTES.md` lists active files and stays aligned with the manifest.

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

Purpose: prepare the current codebase for long-term development after the v1
runtime tree has been removed.

Recommended direction:

- Follow the runtime code strategy above: evolve the current stack and do not
  restore legacy UI/data paths.
- Use Kotlin for new application code.
- Use Room as the local source of truth for all runtime paths.
- Move business logic out of activities, receivers, and workers.
- Introduce a testable domain layer for interval and reminder calculations.
- Prefer coroutines and Flow for new asynchronous code.
- Introduce dependency injection only when it reduces real wiring complexity.
- Keep any remaining transitional helpers small, documented, and moving toward
  Room/domain/repository APIs.

Done when:

- Task calculation logic can be tested without Android framework classes.
- New UI code does not directly manipulate database or scheduling internals.
- The app has a clear separation between UI, domain logic, data access, and
  scheduling.
- Entry points and background components depend only on current app classes.

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

1. Run final smoke tests on the supported Android API matrix.
2. Finish final app icon/adaptive icon assets.
3. Keep documentation and dependency cleanup aligned with the removed v1 source tree.
4. Audit manifest and imports periodically so deleted legacy paths are not restored.
5. Remove any remaining unused classes or dependencies discovered after v1 cleanup.
6. Start Kotlin/Compose migration with the task list screen when the XML release
   baseline is stable.

## Future Interaction and History Backlog

These items are planned after the current release-readiness pass:

1. Add swipe-to-acknowledge on task tiles in the main task list.
2. Add a task event table that stores task lifecycle events with timestamps,
   including creation, reminder/alarm firing, acknowledgement, edits, deletion,
   and import-related changes.
3. Record task events from the relevant flows so the table can later support
   statistics and recommendations.
4. Make a short click on a task open the task detail screen, where rename,
   interval changes, future statistics, and recommendations belong.
5. Make a long click on a task enter multi-select mode on the main screen, with
   checkboxes on tasks and top actions such as acknowledge and delete.
6. Add bulk actions for selected tasks, including acknowledgement and deletion.
7. Add a top-left back arrow to every non-main screen so users can return through
   app UI as well as system navigation.
