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

### Build Status

**Build Date:** 2026-05-26  
**APK Size:** 3.0 MB (unsigned)  
**Build Status:** ✅ SUCCESS  
**ProGuard:** ✅ Enabled  
**R8:** ✅ Enabled  

### Test on Real Device

For signed release, you need the production keystore. Once configured:

```bash
# Install release APK
adb install -r app/build/outputs/apk/release/app-release.apk

# Or using bundletool for AAB:
bundletool build-apks --bundle=app-release.aab \
  --output=app.apks \
  --ks=intime-release.jks \
  --ks-pass=pass:password \
  --ks-key-alias=intime \
  --key-pass=pass:password

adb install-multiple app.apks
```

### Manual Test Checklist

After installation, verify:

- [ ] **Cold start** - App launches without crash
- [ ] **Main screen** - Task list visible and responsive
- [ ] **Create task** - Can add new task successfully
- [ ] **Edit task** - Can modify task description/interval
- [ ] **Acknowledge task** - Clicking acknowledge updates next_alarm
- [ ] **Delete task** - Confirmation dialog appears, task is deleted
- [ ] **Empty state** - Shown when no tasks exist
- [ ] **Permissions banner** - Shows if notifications disabled
- [ ] **Settings screen** - Opens and displays all sections
- [ ] **Import/Export** - Can backup and restore tasks
- [ ] **Notifications** - Test with alarm set to near future

### Check for Crashes

```bash
adb logcat | grep -i "crash\|exception\|fatal"
```

### ProGuard Verification

ProGuard output (line counts):
- Input: Check `build/outputs/mapping/release/mapping.txt`
- Verify obfuscated names are present
- Check that our code was not over-obfuscated

---

## R6.3: App Icons

### Status: READY FOR DESIGNER

**Design Guide:** `ICON_DESIGN_GUIDE.md` — Complete specifications for designers

### Current Icon Status

- Dev icons in use: `app_icon_dev.xml` with green DEV badge
- Base icon: `app/src/main/res/drawable-xhdpi/app_icon.png`
- Round icon: `app/src/main/res/drawable-xhdpi/app_icon_round.png`

### What's Needed

**Launcher Icon:**
- All sizes (ldpi through xxxhdpi)
- Remove DEV badge
- Professional, clean design
- Suitable for productivity/reminder app

**Adaptive Icon (Android 8+):**
- Foreground: 108x108 dp with transparency
- Background: Solid color
- Safe zone: 72x72 dp

**Round Icon (Optional):**
- Same as launcher but with rounded corners
- For devices supporting icon shapes

### File Locations After Design

```
app/src/main/res/
├── drawable-ldpi/app_icon.png (36x36)
├── drawable-mdpi/app_icon.png (48x48)
├── drawable-hdpi/app_icon.png (72x72)
├── drawable-xhdpi/app_icon.png (96x96)
├── drawable-xxhdpi/app_icon.png (144x144)
├── drawable-xxxhdpi/app_icon.png (192x192)
├── drawable-*/app_icon_round.png
└── mipmap-anydpi-v33/
    └── ic_launcher.xml (adaptive icon)
```

### Manifest Updates Required

Change in `AndroidManifest.xml`:
```xml
<!-- FROM: -->
android:icon="@drawable/app_icon_dev"
android:roundIcon="@drawable/app_icon_round_dev"

<!-- TO: -->
android:icon="@drawable/app_icon"
android:roundIcon="@drawable/app_icon_round"
```

### Next Steps

1. Share `ICON_DESIGN_GUIDE.md` with designer
2. Designer creates icons per specifications
3. Place PNG files in correct directories
4. Update manifest references
5. Test on different devices (phone, tablet, wear)
6. Include in release build

### Using Android Studio Image Asset

Alternative: Use Android Studio's built-in tool:
1. Right-click `res/` → New → Image Asset
2. Paste base icon image
3. Studio generates all sizes
4. Perfect for consistent scaling

---

## R6.4: Privacy Policy

✅ **Status:** DONE

**Location:** `PRIVACY_POLICY.md`

The privacy policy clearly states:
- ✅ No data leaves the device (fully offline)
- ✅ No accounts required
- ✅ No analytics or telemetry
- ✅ No ads
- ✅ Data is stored locally only
- ✅ Permissions usage explained
- ✅ Export/backup process documented

**For Play Store:** Copy the content from PRIVACY_POLICY.md to the Play Store listing

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

✅ **Status:** DONE

**Location:** `CHANGELOG.md`

Comprehensive changelog for v2.0.0 including:
- ✅ New features (Material Design, permission UI, import/export)
- ✅ Improvements (scheduling accuracy, database modernization)
- ✅ Bug fixes (overdue tasks, boot recovery, permission handling)
- ✅ Migration guide for v1 users
- ✅ Technical notes
- ✅ Known limitations
- ✅ Support and privacy information

**For Release Notes:** Use content from CHANGELOG.md when publishing to Google Play Store

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
