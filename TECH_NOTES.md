# Intime Technical Notes

These notes summarize the current technical state of the `chatgpt` branch and
collect decisions that should be made before larger refactoring.

## Current State

- Branch: `chatgpt`.
- Android application module: `app`.
- Current implementation language: mostly Java.
- Current UI: XML layouts with AppCompat, RecyclerView, Material Components, and
  CardView.
- New entry activity: `MainActivityV2`.
- Persistence: Room database with `TaskEntity`.
- Background work: WorkManager worker for notifications.
- Legacy alarm-related classes still exist.
- Some manifest receivers for alarm and boot handling are currently commented
  out.
- Target SDK: 35.
- Min SDK: 24.

## Important Files

- `app/src/main/java/com/vpe_soft/intime/intime/activity/MainActivityV2.java`
- `app/src/main/java/com/vpe_soft/intime/intime/activity/AddTaskActivity.java`
- `app/src/main/java/com/vpe_soft/intime/intime/activity/TaskDetailsActivity.java`
- `app/src/main/java/com/vpe_soft/intime/intime/database/AppDatabase.java`
- `app/src/main/java/com/vpe_soft/intime/intime/database/entities/TaskEntity.java`
- `app/src/main/java/com/vpe_soft/intime/intime/database/dao/TaskDao.java`
- `app/src/main/java/com/vpe_soft/intime/intime/database/repositories/TaskRepository.java`
- `app/src/main/java/com/vpe_soft/intime/intime/receiver/AlarmUtil.java`
- `app/src/main/java/com/vpe_soft/intime/intime/workers/TaskNotificationWorker.java`

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

New code should move toward:

- Kotlin for new source files;
- a domain layer for reminder calculations;
- Room entities and DAOs as the persistence layer;
- repositories that expose observable task data;
- ViewModels that expose UI state;
- Compose for new screens;
- tests for domain logic and database migrations.

The migration should be gradual. The existing Java/XML implementation can remain
working while new Kotlin/Compose pieces are introduced.

## Scheduling Model

The app needs one documented scheduling model. Current product decisions:

- Acknowledgement always recalculates the next reminder from the current
  acknowledgement time.
- An overdue task waits for acknowledgement and does not accumulate multiple
  overdue occurrences.
- Caution time remains a fixed percentage of the full interval.
- A just-overdue task notification may include an `ACK` action.
- Later reminders about existing overdue tasks should not include `ACK`; they
  should open the task list.

A likely technical approach:

1. The database is the source of truth.
2. At any time, schedule only the nearest future due task with `AlarmManager`.
3. When that alarm fires, mark matching tasks as overdue/notified and show the
   first due notification.
4. Include quick `ACK` only when the notification refers to a newly overdue task.
5. Use exact alarms when available, otherwise fall back to an inexact alarm.
6. After task changes or acknowledgement, reschedule the nearest future task.
7. Run occasional reconciliation work with `WorkManager`.
8. Use reconciliation for calm reminders that overdue tasks exist.
9. Restore scheduling after reboot.

The technical details still need implementation design, but the product behavior
above is decided.

## Import Behavior

For v2, import should fully replace the current task list. This is mainly a
debugging and migration tool for loading old-version data into the new version.

Implementation expectations:

- parse and validate the backup before deleting current tasks;
- perform replacement transactionally;
- leave existing tasks unchanged if import fails;
- communicate import failure clearly to the user or developer.

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
