# Intime Technical Notes

These notes summarize the current technical state of the `chatgpt` branch and
collect decisions that should be made before larger refactoring.

See `ROADMAP.md` (**v1 / v2 Code Strategy**) for the rule that v1 code may remain
in the repository as reference but must not be part of the running app.

## Current State

- Branch: `chatgpt`.
- Android application module: `app`.
- Implementation language: mostly Java (Kotlin planned for new code).
- **Active UI:** v2 XML screens; launcher is `MainActivityV2`.
- **Persistence (runtime):** Room with `TaskEntity`, schema version 6.
- **Scheduling (runtime):** `SchedulingCoordinator` + `AlarmManager`; `BootReceiver`
  and `AlarmReceiver` registered; `TaskNotificationWorker` for reconciliation.
- **Legacy UI/data paths:** still present in source for reference, not registered
  as launcher in `AndroidManifest.xml`.
- Target SDK: 35; min SDK: 24.

## v1 / v2 Code Layout

| Role | Examples | Rule |
|------|----------|------|
| Active v2 | `MainActivityV2`, `AddTaskActivity`, `TaskRepository`, `SchedulingCoordinator` | Extend, wire in manifest, test |
| Legacy v1 (reference) | `MainActivity`, `NewTaskActivity`, `DatabaseUtil`, `OneTask` | Do not call from v2; do not register as entry points |
| Shared / transitional | `AlarmUtil` (date strings), `InTimeOpenHelper` (file name only; avoid new SQLite use) | Prefer Room; shrink over time |

New features and bug fixes go through the v2 stack only.

## Active v2 Files (runtime)

- `activity/MainActivityV2.java` — launcher, list, permission prompt, worker enqueue
- `activity/AddTaskActivity.java`, `activity/TaskDetailsActivity.java`,
  `activity/SettingsActivity.java`
- `view_models/TaskViewModel.java`
- `adapters/TaskAdapter.java`
- `database/AppDatabase.java`, `entities/TaskEntity.java`, `dao/TaskDao.java`,
  `repositories/TaskRepository.java`
- `domain/ReminderCalculator.java`
- `scheduling/SchedulingCoordinator.java`
- `import_export/BackupImport.java`, `import_export/ImportReplacement.java`
- `receiver/AlarmReceiver.java`, `receiver/AckReceiver.java`,
  `receiver/BootReceiver.java`
- `notifications/NotificationHelper.java`
- `workers/TaskNotificationWorker.java`
- `receiver/AlarmUtil.java` — reminder math helpers and notification copy (no DB)
- `ui/UiVisibility.java`, `activity/V2Activity.java` — v2 foreground tracking

## Legacy v1 Files (reference only)

Not used by the v2 launcher flow; kept to compare behavior with the old app.

- `activity/MainActivity.java`, `activity/NewTaskActivity.java`
- `database/DatabaseUtil.java`, `database/InTimeOpenHelper.java`
- `OneTask.java`
- `recyclerview/TaskRecyclerViewAdapter.java`
- Related v1 layouts under `res/layout/` (for example `activity_main.xml` if distinct
  from v2)

Do not add new imports from v2 code into these classes.

## v2 Foreground Visibility

- `UiVisibility` counts started v2 activities (`V2Activity.onStart` / `onStop`).
- `AlarmReceiver` skips posting a notification when `UiVisibility.isV2UiVisible()`.
- v2 screens extend `V2Activity`: `MainActivityV2`, `AddTaskActivity`,
  `TaskDetailsActivity`, `SettingsActivity`.
- `MainActivityV2.onPause` still writes `LAST_USAGE_TIMESTAMP` for boot
  reconciliation.

Legacy `MainActivity.isOnScreen` is only used inside v1 `MainActivity` (reference
code); v2 must not read it.

## Current Risks

### Notification Accuracy

`WorkManager` periodic work has a minimum interval and is not designed for exact
reminders. It is useful as a background safety mechanism, but it should not be
the only mechanism for user-visible reminder timing.

Potential direction:

- Use `AlarmManager` for the nearest due reminder.
- Use `WorkManager` for periodic reconciliation.
- Reschedule the nearest reminder after create, edit, delete, acknowledge, app
  startup, and boot.
- Treat the first notification for a newly overdue task differently from later
  reminders about already overdue tasks.

### Android 12+ Exact Alarms

Exact alarms require special handling on modern Android versions. The current
baseline policy is:

- declare `SCHEDULE_EXACT_ALARM`, because Intime is a reminder app and exact
  reminders are part of the product value;
- use `setExactAndAllowWhileIdle` when exact alarms are available;
- fall back to `setAndAllowWhileIdle` when exact alarms are unavailable;
- do not open exact-alarm settings directly from receivers or other background
  paths.

Future UI work should explain degraded reminder precision if exact alarms are not
available and provide a user-initiated way to open the relevant system settings.

### Notification Channel

Android 8+ requires notification channels. The app should create a stable channel
before posting task notifications.

If `POST_NOTIFICATIONS` is not granted on Android 13+, notification workers
should finish successfully without marking tasks as notified. Permission denial is
an app state, not a worker failure.

### Boot Handling

Scheduled reminders do not automatically survive device reboot. Boot handling
should restore the nearest reminder after `BOOT_COMPLETED` where permitted.

### Database Migration

Room schema version is currently 6. Migration compatibility with older app
versions should be tested before release.

Future database work should consider a separate history table for reminder and
acknowledgement timestamps. This is not required for the first v2 release, but it
should be kept in mind when changing the schema.

### Threading

The repository currently creates new single-thread executors per operation in
several places. Future Kotlin code should prefer structured coroutines and
consistent dispatchers.

### Encoding

Some Russian text appears garbled in source comments and string literals. Before
large text/UI work, source encoding and resource text should be normalized.

## Suggested Architecture Direction

All **runtime** work follows the v2 stack. Legacy v1 Java/XML stays in the repo
only as reference until optional cleanup.

New code should move toward:

- Kotlin for new source files;
- domain layer for reminder calculations (`ReminderCalculator` already extracted);
- Room entities and DAOs as the only persistence path at runtime;
- repositories that expose observable task data;
- ViewModels that expose UI state;
- Compose for new screens (pilot on task list after XML MVP);
- tests for domain logic, import, and database migrations.

Do not re-enable legacy activities in the manifest or call `DatabaseUtil` /
`OneTask` from v2 components.

## Scheduling Model

Product decisions:

- Acknowledgement always recalculates the next reminder from the current
  acknowledgement time.
- An overdue task waits for acknowledgement and does not accumulate multiple
  overdue occurrences.
- Caution time remains a fixed percentage of the full interval.
- A just-overdue task notification may include an `ACK` action.
- Later reminders about existing overdue tasks should not include `ACK`; they
  should open the task list.

Implemented flow:

1. Room (`TaskDao`) is the source of truth for scheduling queries.
2. `SchedulingCoordinator.reschedule()` schedules only the nearest task with
   `next_alarm > now` through `AlarmManager`.
3. `AlarmReceiver` handles the exact alarm: shows the first-due notification,
   may include `ACK`, marks the fired task as `wasNotified = 1`, then
   reschedules the next alarm.
4. Exact alarms use `setExactAndAllowWhileIdle` when permitted; otherwise the
   app falls back to `setAndAllowWhileIdle`.
5. `SchedulingCoordinator.reschedule()` runs after task CRUD/ACK/import, on
   `MainActivityV2` startup, and after `BOOT_COMPLETED`.
6. `TaskNotificationWorker` (15-minute periodic work) is reconciliation only:
   notifies for `next_alarm <= now AND wasNotified = 0`, opens the task list,
   never adds `ACK`, and does not schedule alarms.
7. If `POST_NOTIFICATIONS` is denied, workers and receivers exit without
   treating permission denial as a failure.

Key classes:

- `scheduling/SchedulingCoordinator.java`
- `receiver/AlarmReceiver.java`
- `workers/TaskNotificationWorker.java`
- `database/dao/TaskDao.java` (`getNearestFutureTask`, `countOverdueTasks`,
  `countSkippedTasks`)

## Import Behavior

For v2, import should fully replace the current task list. This is mainly a
debugging and migration tool for loading old-version data into the new version.

Implementation:

- `ImportReplacement.replaceAll()` parses via `BackupImport` before any delete;
- replacement runs in a Room transaction;
- invalid JSON leaves existing rows unchanged (`ImportReplacementTest`).

Settings UI calls `TaskRepository.replaceAllWithImportFromJson()`.

## Testing Priorities

1. Reminder date calculation.
2. Room migrations from old schema versions.
3. Backup import parsing.
4. Acknowledgement behavior.
5. Notification scheduling decisions.
6. Full-replacement import behavior.

## Build Notes

Before release, verify:

- debug build;
- release build with R8/ProGuard;
- database schema export;
- app upgrade from the old production package;
- behavior on Android versions that differ in notification and alarm
  permissions.
