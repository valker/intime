# Changelog

All notable changes to InTime are documented in this file.

## [2.0.0] - 2026-05-26

Draft target release. The current build is still 1.1.8 with a `.dev` package;
production release and installation upgrade are not confirmed. See ROADMAP.md
and [UPGRADE.md](UPGRADE.md) for the checked status.

### New Features

- **Modern Material Design UI** — Complete redesign with Material Design 3 components
- **Exact Reminder Scheduling** — Precise reminders using AlarmManager with fallback to inexact alarms on older Android versions
- **Permission Management UI** — Notification and exact-alarm permission status in Settings with quick access buttons
- **Task Import/Export** — Backup and restore your tasks to JSON format
- **Empty State** — Clear message with call-to-action when no tasks exist
- **Better Error Handling** — Informative error dialogs instead of silent failures
- **Permission Warning Banner** — On-screen notification if permissions are disabled, with quick fix button

### Improvements

- **Database Modernization** — Migrated from raw SQLite to Room ORM with proper migrations
- **Notification Accuracy** — Improved scheduling reliability with WorkManager fallback
- **Boot Recovery** — Reminders are properly restored after device reboot
- **Code Architecture** — Clean separation between UI, domain logic, and data access layers
- **Multi-Language Support** — Full support for English and Russian with proper text encoding
- **Offline-First** — Complete offline functionality, no server dependency
- **Data Safety** — Room 5 → 6 migration is implemented; older production database paths need verification

### Fixed

- **Overdue Task Handling** — Tasks no longer get multiple "new overdue" notifications before acknowledgement
- **ACK Recalculation** — Next reminder now correctly calculated from acknowledgement time, not planned time
- **Notification Permissions** — Graceful handling when notifications are disabled
- **Boot Time Reminders** — Reminders properly restored after device power cycles
- **Data Loss Prevention** — Import validation prevents silent data corruption from invalid files

### Technical

- **Minimum SDK:** 24 (Android 7.0)
- **Target SDK:** 35 (Android 15)
- **Architecture:** MVVM with LiveData and ViewModels
- **Database:** Room 2.6.1 with schema versioning
- **Scheduling:** AlarmManager + WorkManager coordination
- **Upgrade Compatibility** — Not yet confirmed for installed production v1; historical schema 4 has no migration path yet

### Migration from v1

The intended path is an update of `com.vpe_soft.intime.intime` with a compatible
signing certificate and a higher versionCode, retaining its `main` database.
Current `.dev` APKs install separately and do not read production data.
Migration of the actual v1 database and the installation upgrade still require
R4.2 checks. A schema-4 database currently has no route to schema 6.
See UPGRADE.md before preparing an update; JSON import is a separate replacement
operation and does not prove automatic migration.

### Known Limitations

- **No Cloud Sync** — Tasks are local-only (intentional for privacy)
- **No History Analytics** — We don't store reminder history (future feature)
- **No Shared Tasks** — No collaboration features (intentional for simplicity)

### Developer Notes

- Full test coverage for reminder calculation logic
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

InTime is fully offline. Your data never leaves your device. See [PRIVACY_POLICY.md](PRIVACY_POLICY.md) for details.
