# Intime Release Guide

This document describes the process for building and releasing Intime.

> Актуальный статус — в ROADMAP.md, путь обновления v1 — в [UPGRADE.md](UPGRADE.md).
> По умолчанию release имеет пакет `.dev`. Production-кандидат включается явно
> через `-PproductionRelease=true`; его проверка описана ниже.
> Старый APK из Google Play проверен; обновление 24 → 25 прошло с debug-подписью
> и синтетическими данными на API 35. Сертификат JKS отличается от подписи Play APK;
> пользователь предоставил оба сертификата Console: JKS соответствует Upload key,
> а APK — App signing key. Нужен production AAB и APK, подписанный приложению через Play.
> Подпись приложения через Play и копия реальных данных ещё не проверены; исторические
> отметки сборки ниже не подтверждают готовность production-релиза.

## R6.1: Release Signing Configuration

### Актуальный AAB — 4 октября 2026

После этого кандидата заменены launcher icons и собраны отдельные свежие APK/AAB:
`.test-tools/runs/release-icons-20261004-221247/`. Подписи/Upload key,
bundletool/манифест/mapping прошли, UI 3/3 и release smoke 14/14 на API 36,
иконка осмотрена в launcher. Хеши и границы — DEVICE_TEST_RESULTS.md.
Кандидат ниже сохраняется как исторический артефакт до замены иконок.

Подписанный кандидат **25/2.0.0-rc1** из коммита `a0c055f` собран с R8 и
lintVitalRelease. `jarsigner` подтвердил подпись, сертификат совпал с Upload key,
кэшированный bundletool 1.17.2 успешно выполнил validate/dump manifest без загрузок.
Манифест внутри AAB: исходный package, minSdk 24, targetSdk/compileSdk 36,
debuggable отсутствует. Встроенная карта R8 совпадает с сохранённой mapping.txt.
Отчёт/артефакты: `.test-tools/runs/production-aab-20261004-214440/`;
подробности и SHA-256 — [DEVICE_TEST_RESULTS.md](DEVICE_TEST_RESULTS.md).

Проверенный вызов PowerShell (JDK 17, SDK и локальные signing credentials настроены):

```powershell
./gradlew.bat ':app:bundleRelease' '-PproductionRelease=true' '-PreleaseVersionCode=25' '-PreleaseVersionName=2.0.0-rc1' --offline --no-daemon --no-watch-fs --no-configuration-cache
```

Версию передавать в кавычках. В этом шаге unit/device-тесты не запускались,
runtime не изменялся; ранее проверенный APK и новый AAB — отдельные артефакты.
Новый AAB не опубликован, финальная версия/Play коды не согласованы,
обновление Play v1 и оставшиеся device-проверки открыты. Более ранние записи
ниже описывают состояние на момент соответствующего прогона.

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

Текущая конфигурация имеет compileSdk/targetSdk 36 (обновлена 3 октября),
AGP 8.9.1 и Gradle 8.11.1. Совместимость инструментов проверена по
[документации Android](https://developer.android.com/build/releases/about-agp).
Образ API 36 установлен; debug-проверки на Android 16 описаны в DEVICE_TEST_RESULTS.md.
Они не заменяют проверку release с R8 и полного пользовательского smoke-сценария.
Для загрузки обновления в Play требуется API 36.
Источник: [требования Google Play](https://developer.android.com/google/play/requirements/target-sdk),
проверены 3 октября 2026. Подпись upload key не делает локальный APK совместимым
обновлением Play v1; нужен APK с app signing key через Play Console (UPGRADE.md).
Сборка кандидата не публикует приложение.

Неподписанная упаковка 3 октября прошла (R8 и lintVitalRelease), артефакты сохранены
в `.test-tools/runs/production-candidate-20261003`. Полная local suite — 97 тестов;
подписанные APK/AAB тоже собраны 3 октября, подпись и upload-сертификат проверены:
`.test-tools/runs/production-signed-20261003-163519`. Настройки приватного ключа работают.
Этот исторический кандидат имел targetSdk 35; после обновления SDK нужны свежие артефакты.
Последующая проверка свежего release APK на API 36 описана ниже.

После перехода на SDK 36 свежие подписанные APK/AAB собраны и проверены:
`.test-tools/runs/production-api36-20261003-165559` (compile/target 36, minSdk 24,
upload-сертификат совпал). 97 local и 18 device-тестов прошли; device выполнялся
на API 35, пять отдельных фаз пропущены. На отдельном AVD Android 16 также прошли
19 debug-тестов и четыре notification/reboot-фазы; ограничения окружения и UI
указаны в отчёте. Эти артефакты ещё не загружались в Play.
Для локальной debug-регрессии закреплён проектный AOSP API 36 на Emulator 37.2.12
с аппаратной графикой; уведомления/reboot и полный набор после reboot прошли.
Это проверка Android API без служб Google, а не release R8 или обновления через Play.
Подробные результаты и границы — DEVICE_TEST_RESULTS.md.

Свежий production APK 25/2.0.0-rc1 с R8 и lintVitalRelease собран отдельно:
`.test-tools/runs/release-r8-20261003-184243` (APK и mapping одной сборки).
Два чистых UI smoke-прогона на AOSP API 36 прошли все пять фаз:
CRUD, ACK из деталей и перенос срока, настройки/Back, сохранение после
перезапуска процесса, постановка/отмена inexact alarm. APK non-debuggable,
подписан upload key; это не проверка обновления Play v1. AAB в этом шаге
не пересобирался. Команда `scripts/test-release-smoke.ps1 -ApkPath <APK>`
и требования — TESTING.md, хеши/логи/границы — DEVICE_TEST_RESULTS.md.
Тот же APK затем прошёл расширенный smoke из семи фаз с JSON export/import
через системный SAF picker, удалением и восстановлением задачи/alarm после
перезапуска процесса. Подробные границы JSON-проверки — DEVICE_TEST_RESULTS.md.
В следующем расширении того же smoke десять фаз прошли на API 36 с настоящим
reboot (тот же alarm восстановлен до открытия UI), доставкой минутного уведомления
через AlarmManager и ACK из шторки с новым сроком/перезапуском процесса.
Для доставки exact разрешено через appops только тестовой установке;
reboot проверял inexact fallback. Отказы разрешений, отключённый канал и
остальные платформы на этом этапе ещё требовали release-проверок;
этот результат не закрывает полную smoke-матрицу или готовность к публикации.

После исправлений lint и слияния локальной master собран новый production APK
25/2.0.0-rc1: `.test-tools/runs/release-permissions-20261003-225632`
(APK и mapping одной сборки; R8 и lintVitalRelease прошли).
Полный smoke на AOSP API 36 прошёл **14/14 фаз**:
`.test-tools/runs/20261003-231028-release-smoke/summary.json`.
Добавлены отказ POST_NOTIFICATIONS и отключение канала через системные настройки:
receiver срабатывает, alarm исчезает, уведомление не появляется до пяти секунд после срока.
После снятия каждого запрета новый UI ACK существующей задачи начинает отсчёт;
уведомление доставляется, ACK из шторки переносит срок.
Это не проверка wasNotified или автоматической доставки старой просроченной задачи
без нового ACK. Проверены чистая установка, upload key и non-debuggable APK;
обновление Play v1, остальные API и Doze остаются открытыми. AAB после последних
изменений не пересобран; прежний AAB является историческим артефактом.
Хеши, частичный неуспешный прогон и границы проверки — DEVICE_TEST_RESULTS.md.

4 октября тот же APK (без изменений runtime) прошёл **14/14 фаз на AOSP API 35**:
`.test-tools/runs/20261004-103427-release-smoke/summary.json`.
Runner принимает `-TestApi 35/36` и сверяет выбранные serial/AVD/API.
Проверенный API 35 профиль: Emulator 37.2.12, AOSP default x86_64 rev 2,
2 CPU, 4 ГБ RAM, 1080×1920/density 420, host GPU; он закреплён в test.ps1.
Прежний Google Play 16 КБ профиль сохранён отдельно после lowmemorykiller
и native падения system_server; успешность на 16 КБ этим AOSP-прогоном не подтверждена.
Оставшиеся API 24/31/33, Doze, обновление Play и новый AAB ещё требуют проверки.

Затем 4 октября тот же APK прошёл **14/14 фаз на Google APIs API 33**:
`.test-tools/runs/20261004-110211-release-smoke/summary.json`.
Bootstrap UI-тестов также прошёл 2/2. Runner теперь принимает `-TestApi 33/35/36`;
на API 33 исправлены действия harness: ожидание фокуса/текста и открытия Edit,
Back для скрытия IME выполняется только при её показе. Runtime APK не менялся.
API 35/36 новой редакцией runner в этом шаге не повторялись.
Следующие платформы — API 31/24; Doze, 16 КБ, Play upgrade и новый AAB открыты.

API 31 затем прошёл **14/14 фаз** на том же APK:
`.test-tools/runs/20261004-142752-release-smoke/summary.json`, UI bootstrap 2/2.
Runner принимает API 31/33/35/36; на Android 12 общий запрет/восстановление
проверены через системный переключатель приложения, канал — отдельно.
Шторка подтверждается свежим XML, имя экспорта сверяется до Save.
Один предыдущий reboot не восстановил alarm; вызов BootReceiver не найден,
причина не установлена. Добавлены package-логи до/после reboot и 15 секунд
ожидания на API 31 перед reboot; это не доказанное исправление причины.
Exact-доступ на API 31 был доступен изначально: его запрет/inexact fallback
этим прогоном не проверен. API 33/35/36 новой редакцией runner не повторялись.
Точечный повтор API 31 без 15-секундной паузы прошёл 3/3 reboot и семь
подготовительных фаз (`20261004-154528-release-smoke`), UI bootstrap 2/2.
Причина прежнего единичного сбоя не установлена; три успешных повтора не являются
доказательством исправления. Runner сохраняет package/broadcast/logcat каждого
повтора. API 24, редкий сбой reboot API 31, Doze, 16 КБ, Play upgrade и новый AAB открыты.

Точечный Doze smoke на существующем AOSP API 36 прошёл 11/11
(`20261004-194701-release-smoke`), UI bootstrap 2/2. Реальный exact alarm
доставил уведомление при подтверждённом IDLE и выключенном экране; после выхода
из сна ACK/перезапуск сохранили новый срок. Изначально отключённый deep idle
временно включён и восстановлен, battery simulation снята. Это одна принудительная
Doze-доставка, не квоты повторных alarm, естественный вход в Doze или inexact
fallback. Новый SDK-образ/AVD не устанавливался; production APK/AAB не пересобирались.

Debug integration на API 36 проверяет настоящий PeriodicWorkRequest/WorkManager:
первое исполнение, тихое уведомление без ACK и будущий срок следующего запуска.
Новый тест прошёл отдельно (`20261004-204057-8bf694bb`) и в классе доставки
(`20261004-204231-840fbc11`, 5 тестов по отчёту, без ошибок). Это не release/R8
проверка worker и не ожидание второго 15-минутного запуска; эти пункты остаются открытыми.

Дополнительные debug-фазы `20261004-210140-periodic-smoke` прошли 2/2:
повтор спустя 23 минуты 57 секунд после завершения прежнего процесса, с сохранением
UUID/данных задачи и дальнейшим периодическим расписанием. Условие прогона:
временно уменьшен пакетный порог одиночных CPU jobs с 3 до 1 после ожидания;
исходный override и эффективный порог восстановлены. Период/часы не ускорялись,
job run вручную не вызывался. Это не default-batching и не release/R8 worker;
срок обычной доставки на устройстве/OEM, Doze и регистрация MainActivity открыты.

Регистрация работы MainActivityV2 отдельно проверена в debug integration на API 36:
одна активная work, 15 минут/BatteryNotLow/без сети; recreate и новое открытие
Activity сохраняют UUID и срок (KEEP). Новый тест прошёл отдельно
(`20261004-213659-9ec40a96`), UI-класс 3/3 (`20261004-213941-eb0b8a0e`).
Это не release/R8 доставка, смерть процесса или проверка остальных API.

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

### Build Signed Production APK/AAB

Default release builds use `.dev`. For the production candidate, use explicit
parameters in PowerShell (JDK 17, SDK and user signing properties configured):

```powershell
./gradlew.bat ':app:assembleRelease' ':app:bundleRelease' '-PproductionRelease=true' '-PreleaseVersionCode=25' '-PreleaseVersionName=2.0.0-rc1' --offline --no-daemon --no-watch-fs --no-configuration-cache
```

Outputs: `app/build/outputs/apk/release/app-release.apk` and
`app/build/outputs/bundle/release/app-release.aab`. Archive the exact artifacts,
SHA-256, source commit, logs and mapping.txt together before another build.
Code 25/name 2.0.0-rc1 are provisional. Local APKs carry the Upload key;
they cannot update the differently signed Play installation.

## R6.2 / R6.7: Release Verification

The uploaded candidate must be tied to its own verification records. See
[DEVICE_TEST_RESULTS.md](DEVICE_TEST_RESULTS.md) for exact artifact hashes,
run identifiers, initial conditions and coverage limits.

| Check | Verified locally | Remaining |
| --- | --- | --- |
| Signed AAB | October 4: R8/lint, Upload key, bundletool validate and actual manifest; embedded mapping matches | Play-generated APKs/installation update; final version |
| API 24 | Runner/test support prepared | No device run: new image exceeds authorized 100 MB disk budget |
| API 31 | Upload-signed APK smoke 14/14, plus three reboot repeats | Earlier isolated reboot failure unexplained; full matrix not closed |
| API 33 | Upload-signed APK smoke 14/14 | Remaining shared coverage and Play upgrade |
| API 35 | Upload-signed APK smoke 14/14 and two natural release periodic repeats, 11/11; synthetic debug upgrade separately | Natural Doze/OEM, real production upgrade/data |
| API 36 | Upload-signed APK smoke 14/14, forced deep Doze scenario and two natural release periodic repeats, 11/11 | Natural Doze/quotas, inexact fallback coverage, remaining matrix |
| WorkManager | Debug execution/KEEP; October 6 release/R8 two natural quiet repeats after process exit on API 35 and 36, each 11/11; earlier controlled run kept separately | Timing guarantees, other API/OEM and Play upgrade coverage open |
| 16 KB pages | Fresh AAB contains no native `.so` files | Separate device/platform verification not performed |
| CI | Configuration and local checks recorded | Successful new remote pipelines not confirmed |

Use the release-smoke runner and commands in [TESTING.md](TESTING.md), only on
the dedicated project AVDs. Do not install or clear a personal device implicitly.
The October 6 release periodic result is PASSED, 10/10, without manual job run
or live jobscheduler dumps during the wait. Installed framework bytecode showed
that textual scheduler dumps can recompute TimeController readiness; those dumps
are now limited to before/after the observation. The subsequent two-repeat run
passed 11/11 with two worker SUCCESS entries and quiet notification gaps of
21m40s from the first alarm and another 26m15s, without intervening scheduler dumps.
This does not promise a fixed 15-minute delivery time.
The API 35 extension also passed 11/11 on the unchanged APK: quiet gaps 21m14s
from the alarm and another 26m15s, two SUCCESS entries for the same worker id.
The observation was continuous, without intervening UI or scheduler dumps;
the dedicated AVD was stopped after cleanup. Ordinary Doze/OEM and other API
periodic delivery remain outside these two active-emulator checks.
The earlier October 4 periodic result uses PASSED_CONTROLLED_PLATFORM_DISPATCH, not a natural
delivery pass. The scheduler remained Ready:true; host then dispatched its ready
job with -s. The APK's WorkManager returned SUCCESS and posted the quiet repeat.
No batching threshold, interval or clock changes were made; no direct doWork call.
Do not infer a current pass from older logs or treat a packaging check as a device
check. Preserve R8 mapping for crash decoding; a renamed class alone does not
prove all runtime paths work.

## R6.3: App Icons

**Implemented October 4:** vector calendar artwork retains the existing palette,
without the DEV marker. Manifest uses mipmap icons: legacy fallback API 24–25,
adaptive API 26+ and monochrome API 33+. The fresh signed APK/AAB, packaged
resources, UI 3/3 and release smoke 14/14 passed on API 36; its launcher icon
was visually checked. See [ICON_DESIGN_GUIDE.md](ICON_DESIGN_GUIDE.md).
Themed launcher mode, other masks/OEM and legacy API 24–25 appearance remain
unverified on device. Play listing artwork is separate from these launcher XMLs.

## R6.4: Privacy Policy

The local [PRIVACY_POLICY.md](PRIVACY_POLICY.md) draft was updated October 4.
The app performs no own network uploads or cloud sync. It permits Android system
backup, and user-selected SAF providers may be cloud services. The policy states
these limits and explains that JSON exports are unencrypted.

Before release, publish matching text at a public policy URL and verify app/store
links. The currently referenced `https://valker.github.io/intime_privacy_policy.html`
was not readable through the web tool in this review; availability and consistency
remain unverified. Repository edits do not update that site or Console.
Google requires a publicly accessible privacy policy URL; see the
[User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311).

## R6.5: Play Console Declarations

The [declaration draft](google_play/PLAY_CONSOLE_DECLARATIONS.md) was reviewed
against the merged signed AAB, source flows and official documentation October 4.
It includes WorkManager permissions and the system-backup/SAF caveats.
Final Data safety answers, content forms and Play acceptance are unverified;
a file in the repository is not a submitted Console declaration.

## R6.6: Changelog and Release Notes

[CHANGELOG.md](CHANGELOG.md) describes an unreleased target version. Final date,
version and user-facing release notes require approval after the remaining checks.
Do not state automatic migration of all v1 installations or exact 15-minute
background delivery. The real-data/Play update remains R4.2.

## Release Checklist

- [x] Local Upload key configured and candidate signature verified.
- [x] Candidate AAB structure, manifest and mapping checked.
- [x] Bounded upload-signed APK smoke on API 31/33/35/36 documented.
- [x] Local privacy/declaration/changelog drafts reviewed against current behavior.
- [x] Launcher/adaptive icons replace DEV artwork (R5.2), API 36 visual check recorded.
- [ ] Remaining device coverage and release worker verified (R4.3/R5.3).
- [ ] Real-data upgrade through Play-compatible signature verified (R4.2).
- [ ] Public privacy page and actual Console declarations verified (R5.4).
- [ ] Final production version and unused Play versionCode confirmed (R5.1).
- [ ] Fresh final artifacts, mapping and hashes archived after all changes.
- [ ] Final release notes, tag and staged rollout prepared (R5.5).

No publication, tag, Console submission or remote pipeline run is implied by
these checked local items.
