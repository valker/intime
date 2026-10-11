# Changelog

All notable changes to InTime are documented in this file.

## [2.0.0] - Unreleased

Final versionName 2.0.0 agreed by the user October 11; selected versionCode 28 is
absent from the fresh saved APK/AAB/track snapshot (highest observed 27).
This version remains unreleased; recheck the code before upload. Default builds
remain 1.1.8 with a `.dev` package; productionRelease requires explicit parameters.
Final APK/AAB 28/2.0.0 built offline October 11; upload signatures, manifests,
bundle structure and embedded R8 mapping verified and archived. Additional
build/cache/report files stayed below 100 MB. Fresh API31/33/35/36 matrix passed:
UI 3/3 and final release CRUD/ACK, current/historical JSON, restart/reboot 9/9
phases on each API. Subsequent final notification matrix verified UI3/3 +
release15/15 on all four APIs: delivery/action ACK and notification/channel
denial/restoration. Initial API35 SystemUI ANR failure and unchanged successful
retry, plus API36 batch reserve-stop after completed smoke, are recorded.
Remaining final28 fallback/idle/worker/upgrade reruns waived by the user October 11
using candidate27 evidence. Runtime sources and archived R8 mapping match27;
these scenarios are not reported as fresh passes on28. Release preparation is
next; no upload/publication of28. API24/16-KB device/OEM coverage remains limited.
Previous rc candidate:27/2.0.0-rc3. Signing, AAB structure and the corresponding
Play-signed APK are verified. Play25→27 passed on dedicated API35 with a separate
JSON-derived copy of17 real tasks; the original phone database/preferences are
outside coverage. No production rollout performed. See ROADMAP.md and
[UPGRADE.md](UPGRADE.md) for the checked status.

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
- **Data Safety** — Migrations 4 → 5 → 6 and 5 → 6 are implemented; Play25→27 preserves the tested separate copy of17 tasks

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
- **Upgrade Compatibility** — Play25→27 verified on dedicated API35 within the documented synthetic/JSON-copy scope

### Migration from v1

The intended path is an update of `com.vpe_soft.intime.intime` with a compatible
signing certificate and a higher versionCode, retaining its `main` database.
Current `.dev` APKs install separately and do not read production data.
The installation upgrade25→27 and schema5→6 passed with synthetic tasks and
a separate JSON-derived copy of17 real tasks. This does not verify the original
phone SQLite/WAL/preferences. Historical schema-4/5 fixtures have migration tests;
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
