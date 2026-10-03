# Intime Technical Notes

These notes summarize the current technical state of the `chatgpt` branch and
collect decisions that should be made before larger refactoring.

See `ROADMAP.md` (**Runtime Code Strategy**) for the rule that the deleted v1
UI/data-access tree should not be restored as a normal runtime path.

## Current State

- Branch: `chatgpt`.
- Android application module: `app`.
- Implementation language: mostly Java (Kotlin planned for new code).
- **Active UI:** XML screens; launcher is `MainActivityV2`.
- **Persistence (runtime):** Room with `TaskEntity`, schema version 6.
- **Scheduling (runtime):** `SchedulingCoordinator` + `AlarmManager`; `BootReceiver`
  and `AlarmReceiver` registered; `TaskNotificationWorker` for reconciliation.
- **Legacy UI/data paths:** removed from source after the current stack reached
  parity for the main flows.
- Target SDK: 35; min SDK: 24.

## Runtime Code Layout

| Role | Examples | Rule |
|------|----------|------|
| Active app code | `MainActivityV2`, `AddTaskActivity`, `TaskRepository`, `SchedulingCoordinator` | Extend, wire in manifest, test |
| Removed v1 code | old `MainActivity`, `NewTaskActivity`, `DatabaseUtil`, `InTimeOpenHelper`, `OneTask`, legacy adapters/views/layouts | Inspect through git history only; do not restore as runtime paths |
| Shared / transitional | `AlarmUtil` (date strings / reminder helpers), remaining Java activities and adapters | Prefer Room/domain/repository APIs; shrink over time |

New features and bug fixes go through the current Room-backed stack only.

## Active Files (runtime)

- `activity/MainActivityV2.java` — launcher, list, permission prompt, worker enqueue
- `activity/AddTaskActivity.java`, `activity/TaskDetailsActivity.java` —
  task editing with add/update flows
- `activity/SettingsActivity.java` — backup import/export, permission status,
  exact-alarm configuration
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

## Removed v1 Files

The old v1 UI/data-access tree has been deleted from the working source tree:

- legacy activities such as `MainActivity.java` and `NewTaskActivity.java`;
- legacy SQLite helpers such as `DatabaseUtil.java` and `InTimeOpenHelper.java`;
- legacy task model/adapters/views such as `OneTask.java`,
  `TaskRecyclerViewAdapter.java`, and old custom view helpers;
- v1-only layouts and animation resources.

Use git history to compare old behavior when needed. Do not restore these files
for normal app execution.

## v2 Foreground Visibility

- `UiVisibility` counts started v2 activities (`V2Activity.onStart` / `onStop`).
- `AlarmReceiver` and `TaskNotificationWorker` skip posting when
  `UiVisibility.isV2UiVisible()`; pending tasks remain unnotified for later reconciliation.
- v2 screens extend `V2Activity`: `MainActivityV2`, `AddTaskActivity`,
  `TaskDetailsActivity`, `SettingsActivity`.
- `MainActivityV2.onPause` still writes `LAST_USAGE_TIMESTAMP` for boot
  reconciliation.
- `V2Activity.onStart` calls `NotificationHelper.dismissAllAppNotifications()` so
  overdue notifications disappear when the user opens the app (launcher or
  notification tap).

The old `MainActivity.isOnScreen` path has been removed; current foreground
visibility goes through `UiVisibility`.

## Current Risks

### Notification Accuracy

`WorkManager` periodic work has a minimum interval and is not designed for exact
reminders. It is useful as a background safety mechanism, but it should not be
the only mechanism for user-visible reminder timing.

Implemented policy:

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

Settings UI shows exact-alarm status (available/unavailable on Android 12+) with a
button to open system alarm settings. Users can manage this permission from the app.
The UI also explains that reminders may be delayed if exact alarms are unavailable.

### Notification Channel

Android 8+ requires notification channels. `NotificationHelper` creates the
stable overdue-task channel before posting task notifications.

If `POST_NOTIFICATIONS` is not granted on Android 13+, notification workers
should finish successfully without marking tasks as notified. Permission denial is
an app state, not a worker failure.

Settings UI shows notification permission status with a button to open system
notification settings. Users can manage the permission from the app.

On the main task list screen, a warning banner appears if notification permission
is denied. The banner provides a quick button to open notification settings and
disappears automatically when the permission is enabled.

### Boot Handling

Scheduled reminders do not automatically survive device reboot.
`BootReceiver` restores the nearest reminder after `BOOT_COMPLETED` where
permitted.

### Database Schema

Room database:

- Class: `database/AppDatabase.java`
- Database name: `main` (`Constants.dbName`)
- Current schema version: `6`
- Exported schema file:
  `app/schemas/com.vpe_soft.intime.intime.database.AppDatabase/6.json`
- Entities: `TaskEntity`
- Views: none

#### `tasks`

Backs the current task list and scheduling decisions.

```sql
CREATE TABLE IF NOT EXISTS `tasks` (
  `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  `description` TEXT NOT NULL,
  `interval` INTEGER NOT NULL,
  `amount` INTEGER NOT NULL,
  `next_alarm` INTEGER NOT NULL DEFAULT 0,
  `next_caution` INTEGER NOT NULL DEFAULT 0,
  `last_ack` INTEGER NOT NULL DEFAULT 0,
  `quant` INTEGER NOT NULL DEFAULT 1,
  `wasNotified` INTEGER NOT NULL
);
```

| Column | SQLite type | Null | Default | Meaning |
|--------|-------------|------|---------|---------|
| `id` | `INTEGER` | no | auto | Primary key, auto-generated by Room/SQLite. |
| `description` | `TEXT` | no | none | User-visible task text. |
| `interval` | `INTEGER` | no | none | Interval unit: `0` minute, `1` hour, `2` day, `3` week, `4` month, `5` year. |
| `amount` | `INTEGER` | no | none | Number of interval units in the full reminder period. |
| `next_alarm` | `INTEGER` | no | `0` | Next reminder timestamp, Unix epoch milliseconds. |
| `next_caution` | `INTEGER` | no | `0` | Caution-state timestamp, Unix epoch milliseconds. |
| `last_ack` | `INTEGER` | no | `0` | Last acknowledgement timestamp, Unix epoch milliseconds. |
| `quant` | `INTEGER` | no | `1` | Splits the full interval into smaller acknowledgement periods. |
| `wasNotified` | `INTEGER` | no | none in v6 schema, `0` during v5 -> v6 migration | Boolean stored as `0`/`1`; `1` means the current overdue occurrence has already produced its first notification. |

Room exports no indices or foreign keys for `tasks`.

Important DAO queries:

- `getAllTasks()` / `getAllTasksSync()`: list tasks ordered by `next_alarm ASC`.
- `getNearestFutureTask(now)`: nearest task with `next_alarm > now`, used for exact alarm scheduling.
- `getTasksForNotification(now)`: overdue tasks with `wasNotified = 0`, used by notification paths.
- `getOverdueTasks(now)`: all overdue tasks, ordered by deadline and ID, used for worker summaries and repeats.
- `acknowledgeTask(...)`: updates `last_ack`, `next_alarm`, `next_caution`, and resets `wasNotified = 0`.
- `markTaskNotified(taskId)`: sets `wasNotified = 1` after the first overdue notification.
- `countOverdueTasks(now)` and `countSkippedTasks(lastUsage, now)`: notification copy / boot diagnostics.

### Database Migration

Room schema version is currently 6. Migration from v5 → v6 adds the `wasNotified`
column (INTEGER NOT NULL DEFAULT 0) to track whether a task notification has been
sent. Migration 4 → 5 first adds quant (INTEGER NOT NULL DEFAULT 1).

Production identity and the upgrade audit are documented in [UPGRADE.md](UPGRADE.md).
The old package is `com.vpe_soft.intime.intime`; both current build types use `.dev`
and cannot replace it. Historical v1 databases include SQLiteOpenHelper versions
4 and 5. Both 4 → 5 and 5 → 6 are registered through AppDatabase.createBuilder,
which is shared with the singleton. Versions 1–3 remain unsupported.

### Migration Testing

Instrumentation tests (`MigrationTest.java`) verify the exported Room v5 fixture,
not a real production SQLiteOpenHelper database or installation upgrade:
- `migration_5_to_6_addsWasNotifiedColumn`: Verifies new column exists
- `migration_5_to_6_preservesExistingData`: Verifies row count preserved
- `migration_5_to_6_initializeWasNotifiedToZero`: Verifies new column defaults to 0
- `migration_5_to_6_preservesDataIntegrity`: Verifies multiple tasks with various data

Run tests with: `./gradlew connectedAndroidTest`

HistoricalMigrationTest (local) and HistoricalMigrationPlatformTest (Android)
also seed raw file-backed SQLite databases using the historical version-4/5 DDL,
without room_master_table. They check every stored field, default values for new
columns, Room identity, reopening and DAO writes, including the retained
AUTOINCREMENT sequence after deleting a higher ID. An incompatible schema must
roll back the migration without deleting rows. The fixtures are synthetic and
do not establish installation/signing compatibility with a production APK.

### Real Data Testing

Before release, follow R4.2 in UPGRADE.md: open historical SQLite 4/5 fixtures
through Room, compare all fields and match the actual old schema, then test a
separate authorized copy of real data and installation upgrade. A JSON import
does not prove in-place migration. The installed v1 comes from Google Play;
its extracted APKs now confirm version 1.1.11 / code 24 and a common signer
certificate (see UPGRADE.md). Access to a compatible signing path for the new
build and installation upgrade have not yet been verified.

Future database work should consider a separate history table for reminder and
acknowledgement timestamps. This is not required for the first v2 release, but it
should be kept in mind when changing the schema.

### Threading

The repository and receivers use process-lifetime queues owned by `AppExecutors`.
Scheduling reads and platform updates share the `SchedulingCoordinator` lock;
see Background queues and scheduling below for the ordering and transaction rules.

### Encoding

Some Russian text appears garbled in source comments and string literals. Before
large text/UI work, source encoding and resource text should be normalized.

## Suggested Architecture Direction

All **runtime** work follows the current Room-backed app stack. The old v1
Java/XML tree has been removed.

New code should move toward:

- Kotlin for new source files;
- domain layer for reminder calculations (`ReminderCalculator` already extracted);
- Room entities and DAOs as the only persistence path at runtime;
- repositories that expose observable task data;
- ViewModels that expose UI state;
- Compose for new screens (pilot on task list after XML MVP);
- tests for domain logic, import, and database migrations.

Do not restore legacy activities in the manifest or reintroduce old SQLite/model
paths such as `DatabaseUtil`, `InTimeOpenHelper`, or `OneTask`.

## Scheduling Model

Product decisions:

- Acknowledgement always recalculates the next reminder from the current
  acknowledgement time.
- An overdue task waits for acknowledgement and does not accumulate multiple
  overdue occurrences.
- Caution is floor(95% of the scheduled interval after quant division), using integer arithmetic.
- A just-overdue task notification may include an `ACK` action.
- Later reminders about existing overdue tasks should not include `ACK`; they
  should open the task list.

`ReminderCalculator` supports anchors and complete interval ends from epoch zero
through 253402300799999 (9999-12-31T23:59:59.999Z). Amounts are bounded before
Calendar.add to avoid its internal integer overflow; years use Calendar.YEAR
directly. Results must advance the anchor and quant must leave at least 1 ms.
Arithmetic uses checked long operations. Minute/hour intervals measure elapsed
time; days/weeks/months/years follow GregorianCalendar in the current time zone.
An explicit-TimeZone overload supports reproducible calendar/DST checks.
Month-end clipping and leap-day behavior remain covered by regressions.

Implemented flow:

1. Room (`TaskDao`) is the source of truth for scheduling queries.
2. `SchedulingCoordinator.reschedule()` schedules only the nearest task with
   `next_alarm > now` through `AlarmManager`.
3. `AlarmReceiver` reads the fired task from Room rather than trusting Intent text.
   Deleted tasks, future deadlines and already notified tasks only trigger
   rescheduling; eligible tasks use their current description. It shows the first-due notification,
   may include `ACK` for the fired task, marks all pending overdue tasks represented
   by the summary as `wasNotified = 1`, then
   reschedules the next alarm.
4. Exact alarms use `setExactAndAllowWhileIdle` when permitted; otherwise the
   app falls back to `setAndAllowWhileIdle`.
5. `SchedulingCoordinator.reschedule()` runs after task CRUD/ACK/import, on
   `MainActivityV2` startup, and after `BOOT_COMPLETED`.
6. `TaskNotificationWorker` (15-minute periodic work) reconciles missed notifications
   and repeats reminders for all tasks with `next_alarm <= now`. New unnotified
   tasks bypass the repeat cooldown. When all overdue tasks were already notified,
   a repeat requires at least 15 minutes since the last successful app notification.
   Every worker notification is silent, has low priority, opens the task list,
   never adds `ACK`, and does not schedule alarms. ACK/delete removes tasks from
   future summaries once they are no longer overdue or no longer exist.
7. `NotificationHelper.postTaskNotification()` checks `POST_NOTIFICATIONS`,
   app-level notification availability and channel importance. Blocked posts
   do not set `wasNotified`; alarm handling still reschedules future tasks.
   A permission revocation during posting is handled as a blocked post.
8. When any v2 activity starts, all notifications posted by the app are dismissed
   (`NotificationManager.cancelAll()`).
9. Receiver and worker perform selection, posting and marking in a Room transaction
   so competing handlers cannot both post the same pending batch. Pending tasks
   are ordered by deadline and ID. UI-visible or blocked sends leave flags unchanged.
   NotificationManager is external to SQLite: process failure between posting and
   commit can still lead to a later repeat. Background queues and rescheduling
   serialization and rapid operation regression checks are described below.
10. The last successful post time is persisted in SharedPreferences
    (`notification_reminder_state/last_successful_post`) for alarm, worker and boot.
    Blocked sends and UI suppression do not update it. Clock rollback permits one
    repeat, after which the new timestamp starts the cooldown again. This is a
    minimum pause for repeats, not a delivery deadline: Android may delay periodic
    work. Clearing app data resets this timestamp; the Room schema stays at version 6.

ACK PendingIntents use a task-specific data URI (`intime://ack/task/<id>`),
because extras do not participate in PendingIntent identity. Regression tests
cover consecutive notifications, blocked posts and permission recovery via worker.

Platform regression checks ran on dedicated API 33 and 35 emulators on 2 October
2026: real AlarmManager delivery and ACK, denied POST_NOTIFICATIONS, disabled
channel and silent worker output. A separate host-controlled smoke test performs
a real reboot, checks BOOT_COUNT, waits for the same AlarmReceiver deadline in
AlarmManager, then verifies persisted tasks and the BootReceiver summary. It
launches the installed app once before reboot and waits for boot delivery before
starting instrumentation. See DEVICE_TEST_RESULTS.md for reports and coverage limits.

Key classes:

- `scheduling/SchedulingCoordinator.java`
- `receiver/AlarmReceiver.java`
- `workers/TaskNotificationWorker.java`
- `database/dao/TaskDao.java` (`getNearestFutureTask`, `countOverdueTasks`,
  `countSkippedTasks`)

## Error Handling

Import/Export errors are shown via AlertDialog with clear error messages instead of
silent failures. User-facing error dialogs provide:
- Title with error context (import failed, export failed, settings failed)
- Detailed error message (invalid JSON, file access errors, etc.)
- OK button to dismiss

This ensures users understand what went wrong and can take corrective action.

## Backup JSON Format

Used for v2 import/export in Settings. Format version 1 is distinct from Room
schema version 6. Files without `meta` are accepted as legacy positional v1;
if `meta` is present it must be an object with integer `version = 1`.
`exportedAt` is optional on import and required only by the current exporter.
New exports include explicit `columns`; older files without it keep the fixed
eight-field order. See [BACKUP_FORMAT.md](BACKUP_FORMAT.md) for the import rules.

Schema, expressed as JSON Schema draft 2020-12:

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "type": "object",
  "required": ["tables"],
  "properties": {
    "meta": {
      "type": "object",
      "required": ["version"],
      "properties": {
        "version": { "type": "integer", "const": 1 },
        "exportedAt": { "type": "integer", "minimum": 0, "maximum": 9223372036854775807, "description": "Unix epoch milliseconds" }
      }
    },
    "tables": {
      "type": "object",
      "required": ["tasks"],
      "properties": {
        "tasks": {
          "type": "object",
          "required": ["rows"],
          "properties": {
            "columns": {
              "type": "array", "minItems": 8, "maxItems": 8, "items": false,
              "prefixItems": [
                { "const": "id" }, { "const": "description" },
                { "const": "interval" }, { "const": "amount" },
                { "const": "next_alarm" }, { "const": "next_caution" },
                { "const": "last_ack" }, { "const": "quant" }
              ]
            },
            "rows": {
              "type": "array",
              "items": {
                "type": "array",
                "minItems": 8,
                "maxItems": 8,
                "items": false,
                "prefixItems": [
                  { "type": "integer", "minimum": 1, "maximum": 9223372036854775806, "description": "id" },
                  { "type": "string", "description": "description" },
                  { "type": "integer", "minimum": 0, "maximum": 5, "description": "interval" },
                  { "type": "integer", "minimum": 1, "maximum": 2147483647, "description": "amount" },
                  { "type": "integer", "minimum": 0, "maximum": 253402300799999, "description": "next_alarm, Unix epoch milliseconds" },
                  { "type": "integer", "minimum": 0, "maximum": 253402300799999, "description": "next_caution, Unix epoch milliseconds" },
                  { "type": "integer", "minimum": 0, "maximum": 253402300799999, "description": "last_ack, Unix epoch milliseconds" },
                  { "type": "integer", "minimum": 1, "maximum": 2147483647, "description": "quant" }
                ]
              }
            }
          }
        }
      }
    }
  }
}
```

Example exported from a real database shape:

```json
{
  "meta": {
    "version": 1,
    "exportedAt": 1780419600000
  },
  "tables": {
    "tasks": {
      "columns": ["id", "description", "interval", "amount", "next_alarm", "next_caution", "last_ack", "quant"],
      "rows": [
        [
          1,
          "Water the plants",
          2,
          3,
          1780660800000,
          1780649400000,
          1780401600000,
          1
        ],
        [
          2,
          "Pay internet bill",
          4,
          1,
          1782997200000,
          1782870300000,
          1780318800000,
          1
        ]
      ]
    }
  }
}
```

- Column order matches `BackupImport` / `BackupExport` (8 fields per row).
- `interval` values are `0` minute, `1` hour, `2` day, `3` week, `4` month,
  `5` year.
- Time fields are Unix epoch milliseconds.
- IDs are positive, unique within the file and preserved on import. Zero and
  Long.MAX_VALUE are rejected to avoid autoassignment and immediate AUTOINCREMENT exhaustion.
- `wasNotified` is not stored; imported tasks default to `wasNotified = 0`.
- Rows have exactly eight fields; strings, fractional numbers, booleans and null
  cannot substitute for numeric fields. Explicit columns must match the order above.
  Integer tokens must be represented as Integer/Long by org.json; decimal literals
  such as 1.0 are rejected. This additional restriction and uniqueness by ID are
  runtime rules beyond the schema's semantic integer type. RFC-strict JSON syntax
  is not implemented separately from the existing org.json parser.
- Unknown root/table/meta properties are ignored for compatibility. Unknown
  explicit versions and data after the root object are rejected. Empty rows is valid.
- Import validates all rows before delete; export uses pretty-printed JSON (`toString(2)`).
- Import also verifies that parameters produce a supported, advancing next ACK
  from now and from a positive last_ack. It preserves stored deadlines rather than
  recalculating them. Schema integer ranges alone do not express this dynamic rule.

## Import Behavior

For v2, import should fully replace the current task list. This is mainly a
debugging and migration tool for loading old-version data into the new version.

Implementation:

- `ImportReplacement.replaceAll()` parses via `BackupImport` before any delete;
- replacement runs in a Room transaction;
- invalid JSON leaves existing rows unchanged (`ImportValidationStateTest`);
- a second-insert failure rolls back both deletion and inserted rows, preserving
  all original fields and wasNotified; a retry works after removing the fault;
- a successful import preserves all eight backup fields and IDs, removes old-only
  rows and initializes wasNotified to false, including reused IDs;
- the repository schedules the nearest imported future task after commit, or
  cancels the previous alarm for an empty/past-only list, before the success callback.

`ImportTransactionStateTest` checks asynchronous main-thread callbacks and the
Robolectric alarm model. `ImportTransactionPlatformTest` checks native SQLite/Room
rollback, retry, replacement and empty import in a separate in-memory database.
The fault is an AFTER INSERT trigger raising ABORT on the second row, not a
commit failure or process crash. Room and AlarmManager do not share a transaction.

Settings UI calls `TaskRepository.replaceAllWithImportFromJson()`.

## Testing Priorities

1. Reminder date calculation.
2. Room migrations from old schema versions.
3. Backup import parsing.
4. Acknowledgement behavior.
5. Notification scheduling decisions.
6. Full-replacement import behavior.

## Background queues and scheduling (R2.1–R2.3)

`AppExecutors` owns two lazily started single-thread queues for the lifetime of
the application process: `intime-tasks` for repository operations (including
import/export) and UI scheduling requests, and `intime-receivers` for alarm,
ACK and boot broadcasts. Activity instances do not create or shut down these
queues. WorkManager and Room keep their own managed executors.

Repository requests share FIFO ordering across repository instances. Broadcasts
use a separate queue so a large backup does not directly delay `goAsync` work;
database locks can still delay either queue. Runtime failures are logged with
the operation name, and broadcast completion runs in `finally`.

`SchedulingCoordinator` uses one process-wide lock around the complete Room
selection and AlarmManager update/cancellation. UI requests run on the task queue;
background callers execute inline under the same lock. A later reschedule reads
Room again after acquiring the lock, so an earlier snapshot cannot overwrite a
newer completed reschedule. No extra executor or cross-queue synchronous wait is
introduced; receivers complete scheduling before finishing their broadcast.

Call scheduling after database changes commit, outside Room transactions: taking
the scheduling lock while holding a database transaction can invert lock order.
The current repository/import, alarm and boot call sites follow this rule.
The lock does not make task mutations and platform delivery one atomic operation;
stale delivered alarms still use the receiver's Room checks.

ACK reads the current task parameters and writes acknowledgement times in one
Room transaction. An import with reused IDs therefore cannot replace the row
between that read and write. Scheduling follows the commit, outside the transaction.
Edit requests copy the submitted values before queueing. Their transaction reads
the current row: if ACK changed while the form was open, the edit preserves the
current `lastAck` and recalculates times with the edited interval from that anchor.
Saving an edit of a deleted row does not recreate it.

R2.3 local regressions cover 120 queued insert/edit/ACK/delete operations, import
ordering and callback errors, an ACK/import interleaving with the same ID, stale
forms after ACK and mutable submitted objects. All three receivers are tested
through `onReceive` and a Robolectric `PendingResult`, including injected errors,
rollback where applicable, completion and subsequent queue work. Android tests
also send real ACK PendingIntents for absent tasks and a deliberately closed
in-memory test database, then verify a following successful ACK. These are
bounded scenarios, not a guarantee against OS process death or broadcast timeouts
under arbitrary load. NotificationManager posts are not rolled back with Room.

## Build Notes

Before release, verify:

- debug build;
- release build with R8/ProGuard;
- database schema export;
- app upgrade from the old production package;
- behavior on Android versions that differ in notification and alarm
  permissions.

## Debugging

### Accessing Database Files

To access the SQLite database from the emulator without binary corruption:

1. Stop the application:
   `adb shell am force-stop com.vpe_soft.intime.intime.dev`

2. Download files using `adb exec-out` and `cmd` to avoid PowerShell binary corruption:
   ```cmd
   adb exec-out "run-as com.vpe_soft.intime.intime.dev cat /data/data/com.vpe_soft.intime.intime.dev/databases/main" > main.db
   adb exec-out "run-as com.vpe_soft.intime.intime.dev cat /data/data/com.vpe_soft.intime.intime.dev/databases/main-wal" > main.db-wal
   adb exec-out "run-as com.vpe_soft.intime.intime.dev cat /data/data/com.vpe_soft.intime.intime.dev/databases/main-shm" > main.db-shm
   ```

3. Query the database locally:
   `sqlite3 main.db "SELECT description FROM tasks;"`

