# Changelog

All notable changes to InTime are documented in this file.

## [2.0.0] - 2026-05-26

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
- **Data Safety** — Automatic migration of old database format to new schema

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
- **No Breaking Changes** — Full compatibility with v1 data through automatic migration

### Migration from v1

If you're upgrading from InTime v1:

1. **Automatic Migration** — Your existing tasks will be automatically migrated to the new database format
2. **Data Preserved** — All task information (description, interval, dates) is preserved
3. **No Manual Action** — Simply install the app and your tasks will appear
4. **Backup Recommended** — Consider exporting your tasks as backup before updating

**If something goes wrong:**
- Your original data is never deleted during migration
- You can restore from Settings → Export/Import feature
- Check Release Notes below for troubleshooting

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

Previous versions available on GitHub. Data from v1 is automatically migrated to v2.

---

## Support

Found a bug? Have a feature request? Open an issue on GitHub:
https://github.com/valker/intime/issues

## Privacy

InTime is fully offline. Your data never leaves your device. See [PRIVACY_POLICY.md](PRIVACY_POLICY.md) for details.
