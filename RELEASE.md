# Intime Release Guide

This document describes the process for building and releasing Intime.

> Актуальный статус — в ROADMAP.md, путь обновления v1 — в [UPGRADE.md](UPGRADE.md).
> По умолчанию release имеет пакет `.dev`. Production-кандидат включается явно
> через `-PproductionRelease=true`; его проверка описана ниже.
> Старый APK из Google Play проверен; обновление 24 → 25 прошло с debug-подписью
> и синтетическими данными на API 35. Сертификат JKS отличается от подписи Play APK;
> пользователь предоставил оба сертификата Console: JKS соответствует Upload key,
> а APK — App signing key. Нужен production AAB и APK, подписанный приложению через Play.
> Production-подпись и копия реальных данных ещё не проверены; исторические
> отметки сборки ниже не подтверждают готовность production-релиза.

## R6.1: Release Signing Configuration

### Production candidate — 3 октября 2026

Обычные сборки остаются `.dev`/21/1.1.8. Production-режим сохраняет исходный пакет
`com.vpe_soft.intime.intime`, требует явных releaseVersionCode (>24) и releaseVersionName.
Разрешены только `:app:assembleRelease` и `:app:bundleRelease`; совмещение с upgradeSmoke
отклоняется. Release использует R8. Код 25 и имя 2.0.0-rc1 — локальный кандидат;
перед загрузкой проверить все использованные коды Console и согласовать финальную версию.

Неподписанный кандидат для проверки упаковки (PowerShell, JDK 17 и SDK настроены):

```powershell
./gradlew.bat ':app:assembleRelease' ':app:bundleRelease' '-PproductionRelease=true' '-PunsignedProduction=true' '-PreleaseVersionCode=25' '-PreleaseVersionName=2.0.0-rc1' --offline --no-daemon --no-watch-fs --no-configuration-cache
```

`unsignedProduction` игнорирует signing credentials. Для подписанного AAB убрать этот флаг
и предварительно настроить все четыре SIGNING_KEY_* значения в окружении или пользовательском
Gradle-файле. Путь: `C:/Users/Valentin/vpe_soft.jks`, alias `intime`; пароли вводятся локально.
Production-сборка без полного набора параметров подписи завершается ошибкой.
Перед сборкой проверяется целостность keystore и публичный SHA-256 upload-сертификата
`97b60110a8e894791b4770826a8d214dc5e3a551cd24bd41d646c29f64a1dd26`.
Доступность приватного ключа с заданным key password проверяется при подписании артефакта.

Текущий кандидат имеет targetSdk 35. Для загрузки обновления в Play требуется API 36:
следующий отдельный шаг — переход SDK и регрессия поведения на Android 16.
Источник: [требования Google Play](https://developer.android.com/google/play/requirements/target-sdk),
проверены 3 октября 2026. Подпись upload key не делает локальный APK совместимым
обновлением Play v1; нужен APK с app signing key через Play Console (UPGRADE.md).
Сборка кандидата не публикует приложение.

Неподписанная упаковка 3 октября прошла (R8 и lintVitalRelease), артефакты сохранены
в `.test-tools/runs/production-candidate-20261003`. Полная local suite — 97 тестов;
подписанные APK/AAB тоже собраны 3 октября, подпись и upload-сертификат проверены:
`.test-tools/runs/production-signed-20261003-163519`. Настройки приватного ключа работают.
Проверка release на устройстве ещё не выполнена; кандидат по-прежнему targetSdk 35.
Подробные результаты и границы — DEVICE_TEST_RESULTS.md.

### Create a Keystore (first time only)

For a new application only, if you don't have a keystore yet, create one.
For an update to existing v1, use its compatible signing key; a newly generated
unrelated key cannot update installed APKs. With Play App Signing, distinguish
the upload key from the app signing key (see UPGRADE.md).

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

Use user-level `~/.gradle/gradle.properties` outside the repository (on Windows,
`C:\Users\<user>\.gradle\gradle.properties`). Do not put passwords in the tracked
project `gradle.properties`:

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

### Historical Build Status (not current release verification)

**Build Date:** 2026-05-26  
**APK Size:** 3.0 MB (unsigned)  
**Build Status:** ✅ SUCCESS  
**ProGuard:** ✅ Enabled  
**R8:** ✅ Enabled  

### Test on a Dedicated Device

For signed release, use a verified compatible signing key. An APK signed locally
with an upload key is not the APK signed by Google for distribution. Check UPGRADE.md
before updating. Personal devices require explicit authorization.
These commands are templates; the current release still uses `.dev`:

```bash
# Install release APK
adb -s emulator-5580 emu avd name # must be Intime_Test_API35
adb -s emulator-5580 install -r app/build/outputs/apk/release/app-release.apk

# Or using bundletool for AAB:
bundletool build-apks --bundle=app-release.aab \
  --output=app.apks \
  --ks=intime-release.jks \
  --ks-pass=pass:password \
  --ks-key-alias=intime \
  --key-pass=pass:password

bundletool install-apks --apks=app.apks --device-id=emulator-5580
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

✅ **Status:** DONE

**Location:** `google_play/PLAY_CONSOLE_DECLARATIONS.md`

The Play Console declarations are aligned with the current manifest:

- ✅ `POST_NOTIFICATIONS` — reminders only, requested at runtime on Android 13+
- ✅ `SCHEDULE_EXACT_ALARM` — reminder app use case, with fallback when unavailable
- ✅ `RECEIVE_BOOT_COMPLETED` — reschedules user-created reminders after restart
- ✅ No ads, analytics, tracking, accounts, cloud sync, or third-party data sharing
- ✅ Data safety can state that no user data is collected or shared
- ✅ App access does not require reviewer credentials

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

- [ ] API 24 (Android 7.0) — Min SDK; full smoke pending
- [ ] API 31 (Android 12) — Exact alarm threshold; full smoke pending
- [ ] API 33 (Android 13) — Debug notification/reboot checks passed; full release smoke pending
- [ ] API 35 (Android 15) — Debug tests and synthetic upgrade passed; full release smoke pending

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
- [x] R6.5: Play Console declarations reviewed
- [ ] R6.6: Changelog prepared
- [ ] R6.7: Smoke tests passed on all API levels
- [ ] Version code bumped in build.gradle
- [ ] Git tag created: `v2.0.0`
- [ ] Release notes prepared for users
