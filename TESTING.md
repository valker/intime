# Running tests

The Windows runner works with Windows PowerShell 5.1 and JDK 17. Android Studio
does not need to be open. Use the repository's Gradle Wrapper (8.10.2).

## Commands

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test.ps1 -Mode local
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test.ps1 -Mode local -Offline
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test.ps1 -Mode local -Filter '*ReminderCalculatorTest'
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test.ps1 -Mode device
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test.ps1 -Mode device -Offline
```

Local mode runs JUnit and Robolectric, including Room/import/scheduling integration
checks. Device mode runs Android instrumentation tests: four Room migration checks
and an ACK action check through a real PendingIntent/broadcast. Robolectric does not replace testing Android platform behavior
on a device.

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
- Android SDK platform 35. SDK path is read from local.properties, then
  ANDROID_HOME; -SdkPath overrides runner discovery. Ensure local.properties
  points to the same SDK because Gradle also reads it.
- For device mode: platform-tools, modern emulator/emulator.exe, hardware
  virtualization and a system image. On this machine the installed image is
  system-images/android-35/google_apis_playstore_ps16k/x86_64.
  -SystemImage can choose another installed API 35 x86_64 image relative to SDK.
- Initial builds require network access to Google Maven/Maven Central and the
  wrapper distribution. Robolectric also resolves Android runtime JARs. An online
  local run copies available instrumented runtime JARs from the user's Maven cache
  to .test-tools/robolectric. Offline mode disables both Gradle and Robolectric
  downloads and uses that prepared runtime directory.
- Instrumentation has its own dependencies: bootstrap device mode online too.
- The Codex sandbox may need an approved escalation to access the user Gradle
  cache. This is independent of whether the emulator is running.

## Emulator ownership

The runner creates a small dedicated AVD inside .test-tools/avd, launches it
without a window, waits for boot completion and sets ANDROID_SERIAL explicitly.
Default port is 5580; use -EmulatorPort with another even port if occupied.
It refuses to use an emulator of another name on that port. No existing personal
AVD is copied or reset. Test data survives between runs.

By default the runner stops an emulator it started. -KeepEmulator retains it for
another run; a reused emulator is left running. To stop the retained test emulator:

```powershell
adb -s emulator-5580 emu avd name # must print Intime_Test_API35
adb -s emulator-5580 emu kill
```

Do not run Gradle or tests in this checkout concurrently with the runner. A lock
prevents two runner invocations from sharing reports or the AVD.

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

GitLab jobs use SDK 35 and publish JUnit/HTML reports even on failure. The manual
instrumentedTests job requires a runner tagged android-emulator with /dev/kvm
available in its container. Without such a runner, device verification runs
locally; the manual CI job must not be claimed as verified.
