# Changelog

All notable changes to InTime are documented in this file.

## [2.0.0] - Unreleased

Draft target release. Default builds remain 1.1.8 with a `.dev` package;
an explicit productionRelease candidate requires version parameters.
Upload-key signing and AAB structure were verified October 4, 2026; the Play-signed
installation update and a real-data upgrade remain unverified. See ROADMAP.md
and [UPGRADE.md](UPGRADE.md) for the checked status.

### New Features

- **Java/XML UI** — AppCompat screens with Material components; no Compose rewrite
- **Reminder Scheduling** — AlarmManager uses exact alarms when permitted and falls back to less precise alarms when unavailable
- **Permission Management UI** — Notification and exact-alarm permission status in Settings with quick access buttons
- **Task Import/Export** — Backup and restore your tasks to JSON format
- **Empty State** — Clear message with call-to-action when no tasks exist
- **Better Error Handling** — Informative error dialogs instead of silent failures
- **Permission Warning Banner** — On-screen notification if permissions are disabled, with quick fix button

### Improvements

- **Database Modernization** — Migrated from raw SQLite to Room ORM with proper migrations
- **Background Reminders** — WorkManager reconciles overdue tasks and posts silent repeats; Android may delay execution
- **Boot Recovery** — Reminders are properly restored after device reboot
- **Code Architecture** — Clean separation between UI, domain logic, and data access layers
- **Multi-Language Support** — Full support for English and Russian with proper text encoding
- **Offline-First** — Complete offline functionality, no server dependency
- **Data Safety** — Migrations 4 → 5 → 6 and 5 → 6 are implemented; actual production data still needs verification

### Fixed

- **Overdue Task Handling** — Tasks no longer get multiple "new overdue" notifications before acknowledgement
- **ACK Recalculation** — Next reminder now correctly calculated from acknowledgement time, not planned time
- **Notification Permissions** — Graceful handling when notifications are disabled
- **Boot Time Reminders** — Reminders properly restored after device power cycles
- **Data Loss Prevention** — Import validation prevents silent data corruption from invalid files

### Technical

- **Minimum SDK:** 24 (Android 7.0)
- **Target SDK:** 36 (Android 16)
- **Architecture:** MVVM with LiveData and ViewModels
- **Database:** Room 2.8.4, schema 6 with migrations from historical SQLite 4/5
- **Scheduling:** AlarmManager + WorkManager coordination
- **Upgrade Compatibility** — Not yet confirmed for installed production v1; historical schemas 4/5 now have migration paths

### Migration from v1

The intended path is an update of `com.vpe_soft.intime.intime` with a compatible
signing certificate and a higher versionCode, retaining its `main` database.
Current `.dev` APKs install separately and do not read production data.
Migration of the actual v1 database and the installation upgrade still require
R4.2 checks. Historical schema-4/5 fixtures are covered by new migration tests;
schema versions 1–3 still have no registered migration paths.
See UPGRADE.md before preparing an update; JSON import is a separate replacement
operation and does not prove automatic migration.

### Known Limitations

- **No App Cloud Sync** — Tasks are stored locally; Android backup and selected document providers may transfer copies
- **No History Analytics** — We don't store reminder history (future feature)
- **No Shared Tasks** — No collaboration features (intentional for simplicity)

### Developer Notes

- Regression tests for reminder calculations, calendar boundaries and DST; coverage is bounded by tested cases
- Instrumentation tests for database migrations
- ProGuard/R8 optimized release builds
- Documented release signing process

---

## [1.x] - Legacy

Previous versions are available in Git history. Automatic migration from all v1
versions is not confirmed; supported paths are tracked in UPGRADE.md.

---

## Support

Found a bug? Have a feature request? Open an issue on GitHub:
https://github.com/valker/intime/issues

## Privacy

InTime works offline without its own cloud sync. Android system backup and
user-selected document providers may transfer data. See [PRIVACY_POLICY.md](PRIVACY_POLICY.md).
