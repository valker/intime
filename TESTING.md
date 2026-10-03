# Running tests

The Windows runner works with Windows PowerShell 5.1 and JDK 17. Android Studio
does not need to be open. Use the repository's Gradle Wrapper (8.11.1), AGP 8.9.1
and installed compile SDK 36. Device API 33/35 is independent of compile/target SDK.

## Commands

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test.ps1 -Mode local
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test.ps1 -Mode local -Offline
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test.ps1 -Mode local -Filter '*ReminderCalculatorTest'
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test.ps1 -Mode device
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test.ps1 -Mode device -Offline
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test.ps1 -Mode device -TestApi 33 -Offline
```

Local mode runs JUnit and Robolectric, including Room/import/scheduling integration
checks. Device mode runs four Room migration checks, an ACK PendingIntent check,
and four platform notification checks (real AlarmManager delivery/ACK, denied
POST_NOTIFICATIONS, silent worker output and a disabled channel). Two reboot-phase
tests are skipped in a normal suite and run separately by the smoke script.
The denied-permission test skips when permission is already granted; the smoke
script always revokes it before a separate run. A skipped test is not verification.
Robolectric does not replace testing Android platform behavior on a device.

## Комментарии к тестам

Каждый новый тест должен содержать подробный комментарий на русском языке
в формате Javadoc (`/** ... */`) непосредственно перед аннотацией `@Test`.
Правило распространяется на все юнит- и интеграционные тесты в `app/src/test`
и `app/src/androidTest`, включая инструментальные тесты Android.

В комментарии необходимо описать:

- исходные условия и значимые тестовые данные;
- действие или вызов, поведение которого проверяется;
- ожидаемый результат и конкретные проверяемые утверждения;
- существенные границы проверки, если название теста или сценарий могут
  создать впечатление более широкого покрытия.

Комментарий должен соответствовать фактическому коду: не приписывайте тесту
проверки, которых в нём нет. При изменении сценария или утверждений обновляйте
комментарий вместе с тестом. Используйте существующие комментарии как образец.

## Environment

- JDK 17 with java.exe and javac.exe; discovered through JAVA_HOME or PATH.
  Override discovery with -JdkPath.
- Android SDK platform 36. SDK path is read from local.properties, then
  ANDROID_HOME; -SdkPath overrides runner discovery. Ensure local.properties
  points to the same SDK because Gradle also reads it.
- For device mode: platform-tools, modern emulator/emulator.exe, hardware
  virtualization and a system image. On this machine the installed image is
  system-images/android-35/google_apis_playstore_ps16k/x86_64.
  -TestApi 33 uses system-images/android-33/google_apis/x86_64 (revision 17
  installed here). -SystemImage can choose another installed x86_64 image for
  the selected API, relative to SDK; compilation requires platform 36.
- Initial builds require network access to Google Maven/Maven Central and the
  wrapper distribution. Robolectric also resolves Android runtime JARs. An online
  local run copies available instrumented runtime JARs from the user's Maven cache
  to .test-tools/robolectric. Offline mode disables both Gradle and Robolectric
  downloads and uses that prepared runtime directory.
- Instrumentation has its own dependencies: bootstrap device mode online too.
- After changing Gradle/AGP versions, repeat both online bootstraps: cached JARs
  do not guarantee that the new Gradle has usable dependency metadata offline.
- The Codex sandbox may need an approved escalation to access the user Gradle
  cache. This is independent of whether the emulator is running.

## Emulator ownership

The runner creates a small dedicated AVD inside .test-tools/avd, launches it
without a window, waits for boot completion and sets ANDROID_SERIAL explicitly.
Default port is 5580 for API 35 and 5582 for API 33; use -EmulatorPort with another
even port if occupied. Each API has its own Intime_Test_API<API> AVD.
It refuses to use an emulator of another name on that port. No existing personal
AVD is copied or reset. AVD disk data persists, but Gradle removes test APKs after
the suite, which can remove the test app's data. The smoke script installs APKs
once before its phases and does not reinstall them between preparation and reboot
verification, so that fixture must survive the actual reboot.

By default the runner stops an emulator it started. -KeepEmulator retains it for
another run; a reused emulator is left running. To stop the retained test emulator:

```powershell
adb -s emulator-5580 emu avd name # must print Intime_Test_API35
adb -s emulator-5580 emu kill
```

Do not run Gradle or tests in this checkout concurrently with the runner. A lock
prevents two runner invocations from sharing reports or the AVD.

## Реальная доставка и перезагрузка

Для каждого API сначала выполняется обычная suite с сохранением AVD, затем smoke:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test.ps1 -Mode device -TestApi 35 -Offline -KeepEmulator
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-notification-smoke.ps1 -TestApi 35
# После остановки AVD API 35 повторить оба запуска с -TestApi 33.
```

Smoke использует стандартные порты 5580/5582. Перед сменой API остановите прежний
проектный AVD: запись владения рассчитана на один живой экземпляр. Скрипт проверяет
имя AVD, API, PID и время старта, блокирует параллельные прогоны и сохраняет логи
в `.test-tools/runs/*-notification-smoke`. Он оставляет AVD запущенным; остановка
показана выше (для API 33 используйте `emulator-5582` и ожидайте `Intime_Test_API33`).

APK устанавливаются из последней сборки через `adb install -r`. Разрешения изменяются
только у `.dev`-приложения на проектном AVD. Запуск launcher и уход домой перед
фикстурой снимают `stopped/notLaunched`: одной instrumentation после установки
недостаточно для получения BOOT_COMPLETED. Отзыв POST_NOTIFICATIONS выполняется
до instrumentation, поскольку Android может завершить процесс при отзыве.

Фазы проверки:

1. Отказ в POST_NOTIFICATIONS и реальная доставка AlarmManager с ACK.
2. Создание двух синтетических задач в файловой Room-базе тестового приложения.
3. Настоящий `adb reboot`; ожидание нового BOOT_COUNT и восстановленного будильника
   AlarmReceiver с точно тем же сроком в `dumpsys alarm`.
4. Проверка сохранности задач и опубликованной сводки BootReceiver без ACK,
   затем удаление только задач фикстуры и восстановление отметки использования.

`sys.boot_completed=1` не означает, что BOOT_COMPLETED доставлен приложению.
Instrumentation до доставки может подавить ожидающий broadcast, поэтому smoke
сначала ждёт восстановления будильника. Тест не заменяет системный reboot вызовом
обработчика. При сбое до второй фазы фикстура может остаться; её проверка и очистка
выполняются отдельным запуском `RebootRecoveryTest#verifyAfterReboot` с тем же APK.

Проверки подтверждают публикацию в Android NotificationManager, параметры тишины,
ACK и восстановление расписания. Прочтение уведомления и физическое звучание на
реальном телефоне ими не подтверждаются. После успешного smoke исходные
пользовательские `main*.db` в рабочем каталоге не используются и не изменяются.

## Обновление APK с синтетическими данными

После извлечения комплекта старых APK по UPGRADE.md запустить выделенный API 35,
затем smoke (вторая команда использует сохранённый AVD):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test.ps1 -Mode device -TestApi 35 -Filter com.vpe_soft.intime.intime.database.HistoricalMigrationPlatformTest -Offline -KeepEmulator
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-upgrade-smoke.ps1 -ApkDirectory .test-tools/upgrade-apks/20261003-113647-3c8591ca -Offline
```

Путь заменить на папку своего извлечённого комплекта со source.json. Текущий сценарий
рассчитан на production-пакет версии 24 и проектный Intime_Test_API35 на emulator-5580.
Если зависимостей ещё нет в кеше, первый запуск выполнить без -Offline.
Smoke проверяет PID/время старта, имя/API AVD, хеши APK и отсутствие установленного
production-пакета; использует общий run.lock. Личные устройства не используются.

Скрипт собирает специальный debug APK с кодом 25, переподписывает копии старых APK
тем же debug-ключом, запускает старый launcher и создаёт синтетическую фикстуру.
Между prepare и verify выполняется install -r без uninstall/pm clear. Фазы проверяют
сохранение данных, Room-миграцию, CRUD/ACK, повторный запуск и активный alarm.
Подробные границы покрытия — в UPGRADE.md. Результат не подтверждает production-подпись
или реальные пользовательские данные. Исходные APK и main*.db не изменяются.

По завершении удаляются установленные этим smoke пакеты, останавливается принадлежащий
runner проектный AVD; -KeepEmulator оставляет его для следующей проверки.
Логи и summary.json сохраняются в `.test-tools/runs/*-upgrade-smoke-*`.
Три фазовых @Test имеют русские комментарии; обычная suite пропускает их,
как и две отдельные reboot-фазы. Запускать upgrade-тесты следует этим host-скриптом.
Не передавать -PupgradeSmoke=true обычным connected/release-задачам:
Gradle разрешает с этим флагом только три явные debug-задачи, перечисленные в UPGRADE.md.

## Results and diagnosis

Every invocation creates .test-tools/runs/<timestamp>-<id>/ with stdout/stderr,
fresh JUnit XML and summary.json. Gradle HTML reports remain under
app/build/reports/tests/testDebugUnitTest and
app/build/reports/androidTests/connected/debug. Device test failures also capture
logcat. No fresh tests means infrastructure failure, even if Gradle exits zero.

Exit codes: 0 = PASSED, 1 = test failures, 2 = infrastructure error,
124 = timeout. Each run forces the test task with -PforceTestRun=true, retaining
incremental compilation. UP-TO-DATE prerequisites are allowed; old test results
cannot be presented as a fresh pass. Default build timeout is 900 seconds;
boot timeout is 300 seconds. Override with -TimeoutSeconds/-BootTimeoutSeconds.

Gradle filesystem watching hung on this machine in
PosixFileSystemFunctions.listFileSystems. The runner uses --no-watch-fs;
configuration cache remains enabled. --no-daemon scopes process cleanup to the
current build; UTF-8 and UTC are explicit for local test execution.

For a failing run, inspect summary.json and gradle.stderr.log/stdout.log first.
Missing cached artifacts require an online bootstrap. Missing SDK/image or
acceleration must be resolved before tests can execute. Timeout cleanup affects
only the runner's process tree, not unrelated Java processes.

## CI

GitLab jobs use compile SDK 36 and publish JUnit/HTML reports even on failure. The manual
instrumentedTests job requires a runner tagged android-emulator with /dev/kvm
available in its container. Without such a runner, device verification runs
locally; the manual CI job must not be claimed as verified.
