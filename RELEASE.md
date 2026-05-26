# Intime Release Guide

This document describes the process for building and releasing Intime.

## R6.1: Release Signing Configuration

### Create a Keystore (first time only)

If you don't have a keystore yet, create one:

```bash
keytool -genkey -v -keystore intime-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias intime
```

This will prompt you for:
- Keystore password
- Key password
- Organization information

**Save the keystore file securely** - you'll need it for all future releases.

### Configure Signing Properties

Set signing credentials via one of these methods:

#### Option 1: Environment Variables (CI/CD)

```bash
export SIGNING_KEY_STORE_PATH="/path/to/intime-release.jks"
export SIGNING_KEY_STORE_PASSWORD="your_keystore_password"
export SIGNING_KEY_ALIAS="intime"
export SIGNING_KEY_PASSWORD="your_key_password"
```

#### Option 2: gradle.properties (Local Development)

Edit `gradle.properties` and uncomment/fill:

```properties
SIGNING_KEY_STORE_PATH=path/to/intime-release.jks
SIGNING_KEY_STORE_PASSWORD=your_keystore_password
SIGNING_KEY_ALIAS=intime
SIGNING_KEY_PASSWORD=your_key_password
```

**⚠️ WARNING:** Do NOT commit gradle.properties with passwords to version control!

### Build Signed Release APK

```bash
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

### Build Signed Release AAB (for Google Play)

```bash
./gradlew bundleRelease
```

Output: `app/build/outputs/bundle/release/app-release.aab`

---

## R6.2: Test Release Build

After building release, test on real devices:

```bash
# Install release APK
adb install -r app/build/outputs/apk/release/app-release.apk

# Verify ProGuard didn't break anything
# - App starts
# - Can add/edit/delete tasks
# - Can acknowledge tasks
# - Notifications work
# - Import/export works
```

Check for crashes:
```bash
adb logcat | grep -i "crash\|exception\|error"
```

---

## R6.3: App Icons

### Current Icon Status

- Dev icon: `app/src/main/res/drawable/app_icon_dev.png`
- Dev adaptive icon: `app/src/main/res/drawable/app_icon_round_dev.png`
- Release icon: Create production versions

### Replace Icons for Release

1. Prepare production icon (192x192 PNG for launcher icon)
2. Create adaptive icon variant if needed
3. Update `build.gradle` flavor or update manifest icon reference

For now, dev icons are used. Update this before submitting to Play Store.

---

## R6.4: Privacy Policy

The app is fully offline with no server communication. Privacy policy should state:

- No data leaves the device
- No accounts required
- No analytics or telemetry
- No ads
- Data is stored locally only

**Current location:** Add to the Play Store listing when uploading

---

## R6.5: Play Console Declarations

When uploading to Google Play:

1. **App access**
   - Device & app history: Required for boot reminders
   - Calendar: Not required

2. **Permissions**
   - POST_NOTIFICATIONS (Android 13+): Required for reminders
   - SCHEDULE_EXACT_ALARM (Android 12+): Required for precision
   - RECEIVE_BOOT_COMPLETED: Required for boot reminders

3. **Advertising**
   - No ads in this app

4. **Content rating**
   - Utility app, no sensitive content

---

## R6.6: Changelog

**v2.0.0** (First v2 Release)

New:
- Modern Material Design UI
- Exact reminder scheduling with AlarmManager
- Permission management and status in Settings
- Import/export tasks to JSON backup
- Better error handling with clear messages
- Empty state with call-to-action

Improved:
- Notification accuracy and reliability
- Database schema with migration support
- Offline-first architecture

Fixed:
- Overdue task handling
- Boot reminder recovery
- Notification permissions handling

---

## R6.7: Smoke Tests Matrix

Test on these Android versions before release:

- ✅ API 24 (Android 7.0) — Min SDK
- ✅ API 31 (Android 12) — Exact alarm threshold
- ✅ API 33 (Android 13) — POST_NOTIFICATIONS introduction
- ✅ API 35 (Android 15) — Target SDK, latest

### Test Checklist

- [ ] App launches successfully
- [ ] Can create new task
- [ ] Can edit task
- [ ] Can delete task (with confirmation)
- [ ] Can acknowledge task
- [ ] Task list shows correct order (next_alarm)
- [ ] Empty state shown when no tasks
- [ ] Permission warning shown if notifications disabled
- [ ] Import/export works
- [ ] Settings screen accessible
- [ ] No crashes in logcat

---

## Release Checklist

- [ ] R6.1: Release signing configured
- [ ] R6.2: Release build tested on real device
- [ ] R6.3: App icons finalized
- [ ] R6.4: Privacy policy ready
- [ ] R6.5: Play Console declarations reviewed
- [ ] R6.6: Changelog prepared
- [ ] R6.7: Smoke tests passed on all API levels
- [ ] Version code bumped in build.gradle
- [ ] Git tag created: `v2.0.0`
- [ ] Release notes prepared for users
