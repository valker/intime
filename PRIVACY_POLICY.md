# Privacy Policy for InTime

**Last updated:** October 11, 2026

InTime is developed by Valentin Pimenov and provided as a free application.

## Task data and local operation

InTime works without an account or an internet connection. It stores task
descriptions, reminder intervals, due dates and acknowledgement times in a local
Room/SQLite database in the app's internal storage. Local settings also record
session and notification timing. The developer does not receive this information.
InTime has no application server, advertising, analytics or tracking SDKs, and
does not perform its own network uploads or cloud synchronization.

## Android backup and device transfer

The application permits Android system backup (`allowBackup=true`). Depending
on your device, backup service and settings, Android may include app data in a
system backup, including a cloud backup, or transfer it to another device.
These operations are handled by the operating system and its selected service,
not by an InTime server. Manage system backups in your device settings.
The app does not guarantee that data always remains on one device.

## Import and export

Where supported by your installed version, you can export tasks as an unencrypted
UTF-8 JSON file. InTime 2.0 also supports importing a compatible file. These
operations use Android's document picker. The selected provider may be local storage
or a cloud service. That provider handles storage or transmission under its own
terms. InTime does not choose or automatically upload to a cloud provider.
Protect exported files yourself; they contain task descriptions and dates.
Successful import replaces the current task list rather than merging it.

## Permissions and external applications

| Permission | Purpose |
| --- | --- |
| POST_NOTIFICATIONS | Display reminders; you can deny or disable notifications. |
| SCHEDULE_EXACT_ALARM | Schedule the nearest due task precisely when Android permits it; otherwise scheduling is less precise. |
| RECEIVE_BOOT_COMPLETED | Restore scheduling and, when appropriate, notify about missed tasks after restart. |
| WAKE_LOCK | WorkManager background execution. |
| ACCESS_NETWORK_STATE | WorkManager infrastructure; does not permit internet uploads. |
| FOREGROUND_SERVICE | Declared by WorkManager; the current worker does not request foreground execution. |

The build also uses an application-specific signature permission for AndroidX
receiver protection. It has no INTERNET permission. Store links open an external
store or browser, whose data practices are separate from InTime.

## Security and deletion

Android's app storage isolation and device security protect local data. InTime
does not add database or export-file encryption. Avoid placing sensitive
information in task descriptions displayed by notifications.
You can delete tasks in the app or clear app data/uninstall it to remove local
app storage. Exported files and system/provider backups must be managed
separately; clearing local app data does not promise deletion of those copies.

## Changes and contact

Policy changes will be recorded with an updated date. For questions, contact the
developer through the InTime store listing or the
[project issue tracker](https://github.com/valker/intime/issues).
