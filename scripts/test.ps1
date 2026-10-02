<#
Запускает тесты проекта и сохраняет результат в .test-tools/runs.
local — JUnit и Robolectric на компьютере; device — тесты на отдельном Android-эмуляторе.
Пример: powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test.ps1 -Mode local -Offline
Коды завершения: 0 — успех, 1 — падение тестов, 2 — ошибка окружения, 124 — таймаут.
#>
[CmdletBinding()]
param(
    # Режим запуска и необязательный фильтр класса/метода теста.
    [ValidateSet('local', 'device')][string]$Mode = 'local',
    [string]$Filter,
    # Пути можно задать явно; иначе скрипт найдёт SDK и JDK в окружении.
    [string]$SdkPath,
    [string]$JdkPath,
    # Путь установленного образа Android относительно каталога SDK.
    [string]$SystemImage = 'system-images\android-35\google_apis_playstore_ps16k\x86_64',
    # Отдельные ограничения времени для Gradle и загрузки эмулятора, в секундах.
    [ValidateRange(1, 3600)][int]$TimeoutSeconds = 900,
    [ValidateRange(1, 1200)][int]$BootTimeoutSeconds = 300,
    # Чётный порт определяет адрес устройства adb, например emulator-5580.
    [ValidateRange(5554, 5682)][int]$EmulatorPort = 5580,
    # Offline запрещает загрузки зависимостей; KeepEmulator оставляет загруженный эмулятор.
    [switch]$Offline,
    [switch]$KeepEmulator
)

# Ошибки переменных и команд должны останавливать текущий этап и попадать в catch.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
# У каждого запуска свой каталог: логи и отчёты разных прогонов не смешиваются.
$root = Split-Path $PSScriptRoot -Parent
$work = Join-Path $root '.test-tools'
$run = Join-Path $work ('runs\' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0, 8))
New-Item -ItemType Directory -Path $run -Force | Out-Null
# До получения свежих результатов считаем запуск ошибкой окружения, а не успехом.
$summary = [ordered]@{ mode = $Mode; status = 'INFRASTRUCTURE_ERROR'; tests = 0; failures = 0; errors = 0; skipped = 0; started = (Get-Date).ToString('o'); reportDirectory = $run }
# Различаем эмулятор, запущенный здесь, и ранее запущенный нашим скриптом.
$ownedEmulator = $null
$booted = $false
$deviceOwned = $false
$timer = [Diagnostics.Stopwatch]::StartNew()
$emulatorState = Join-Path $work 'emulator.json'
$serial = "emulator-$EmulatorPort"
# Запоминаем переменные окружения, чтобы восстановить их даже после ошибки.
$oldAvdHome = $env:ANDROID_AVD_HOME
$oldSerial = $env:ANDROID_SERIAL
$oldSdk = $env:ANDROID_HOME
$lock = $null
$exitCode = 2

# Заключаем аргумент процесса в кавычки для путей с пробелами.
# Кавычки внутри значения и переносы строк не поддерживаются и отклоняются заранее.
function Quote-Argument([string]$value) {
    if ($value.Contains('"') -or $value.Contains("`r") -or $value.Contains("`n")) { throw 'Quotes and line breaks are not supported in arguments.' }
    return '"' + $value.TrimEnd('\') + '"'
}

# Принудительная остановка применяется только к процессу, созданному этим запуском.
function Stop-OwnedProcess($process) {
    if ($null -ne $process -and -not $process.HasExited) {
        # /T завершает также дочерние процессы этого PID, например Gradle daemon.
        & "$env:SystemRoot\System32\taskkill.exe" /PID $process.Id /T /F 2>&1 | Out-Null
    }
}

# Общий запуск внешней программы: отдельные stdout/stderr, таймаут и код завершения.
function Invoke-LoggedProcess([string]$exe, [string[]]$arguments, [string]$name, [int]$seconds) {
    $stdout = Join-Path $run "$name.stdout.log"
    $stderr = Join-Path $run "$name.stderr.log"
    $argumentLine = ($arguments | ForEach-Object { Quote-Argument $_ }) -join ' '
    $process = Start-Process -FilePath $exe -ArgumentList $argumentLine -WorkingDirectory $root -WindowStyle Hidden -PassThru -RedirectStandardOutput $stdout -RedirectStandardError $stderr
    # Получаем дескриптор сразу, чтобы PowerShell корректно сохранил ExitCode.
    $null = $process.Handle
    try {
        if (-not $process.WaitForExit($seconds * 1000)) {
            Stop-OwnedProcess $process
            throw "TIMEOUT: $name exceeded $seconds seconds. Logs: $run"
        }
        # После завершения дожидаемся окончания обработки вывода процесса.
        $process.WaitForExit()
        return $process.ExitCode
    } finally {
        Stop-OwnedProcess $process
        $process.Dispose()
    }
}

function Invoke-Adb([string[]]$arguments) {
    # Даже adb ограничиваем по времени: зависший сервер не должен блокировать весь скрипт.
    $code = Invoke-LoggedProcess $script:adb $arguments 'adb' 15
    if ($code -ne 0) { throw "adb failed ($code): $arguments" }
    return (Get-Content (Join-Path $run 'adb.stdout.log') -Raw).Trim()
}

# Robolectric использует свои JAR-файлы Android, помимо обычных зависимостей Gradle.
# Копируем уже скачанные файлы из Maven-кеша для последующих offline-прогонов.
function Sync-RobolectricCache {
    $cache = Join-Path $work 'robolectric'
    New-Item -ItemType Directory -Path $cache -Force | Out-Null
    $mavenCache = Join-Path ([Environment]::GetFolderPath('UserProfile')) '.m2\repository\org\robolectric\android-all-instrumented'
    foreach ($jar in @(Get-ChildItem $mavenCache -Recurse -Filter '*.jar' -ErrorAction SilentlyContinue)) {
        $destination = Join-Path $cache $jar.Name
        if (-not (Test-Path $destination)) { Copy-Item -LiteralPath $jar.FullName -Destination $destination }
    }
}

try {
    # Эксклюзивный lock-файл запрещает два одновременных запуска в одном проекте.
    # Это защищает общие отчёты Gradle и данные тестового эмулятора.
    $lock = [IO.File]::Open((Join-Path $work 'run.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
    # Проверяем JDK 17: нужны и java, и компилятор javac, а не только Java Runtime.
    if (-not $JdkPath) {
        if ($env:JAVA_HOME) { $JdkPath = $env:JAVA_HOME }
        else { $JdkPath = Split-Path (Split-Path (Get-Command java.exe -ErrorAction Stop).Source -Parent) -Parent }
    }
    $java = Join-Path $JdkPath 'bin\java.exe'
    $javac = Join-Path $JdkPath 'bin\javac.exe'
    if (-not (Test-Path $java) -or -not (Test-Path $javac)) { throw "JDK not found: $JdkPath" }
    if ((Invoke-LoggedProcess $java @('-version') 'java-version' 15) -ne 0) { throw 'Java cannot start.' }
    if ((Get-Content (Join-Path $run 'java-version.stderr.log') -Raw) -notmatch 'version "17\.') { throw 'This runner requires JDK 17.' }
    # Gradle читает SDK из local.properties; снимаем экранирование Windows-пути.
    # Явный SdkPath должен совпадать с ним, иначе скрипт и Gradle выберут разные SDK.
    $configuredSdk = $null
    $properties = Join-Path $root 'local.properties'
    if (Test-Path $properties) {
        $sdkLine = Get-Content $properties | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
        if ($sdkLine) { $configuredSdk = $sdkLine.Substring(8).Replace('\:', ':').Replace('\\', '\') }
    }
    if (-not $SdkPath) {
        $SdkPath = $configuredSdk
        if (-not $SdkPath) { $SdkPath = $env:ANDROID_HOME }
    }
    if (-not $SdkPath -or -not (Test-Path (Join-Path $SdkPath 'platforms\android-35\android.jar'))) { throw "Android SDK 35 not found: $SdkPath" }
    if ($configuredSdk -and [IO.Path]::GetFullPath($SdkPath).TrimEnd('\') -ne [IO.Path]::GetFullPath($configuredSdk).TrimEnd('\')) { throw 'SdkPath must match sdk.dir in local.properties.' }
    $env:ANDROID_HOME = $SdkPath
    $wrapper = Join-Path $root 'gradle\wrapper\gradle-wrapper.jar'
    if (-not (Test-Path $wrapper)) { throw 'Gradle Wrapper JAR is missing.' }
    if ($Filter -and $Filter -notmatch '^[\w.*#$,]+$') { throw 'Invalid test filter.' }

    # Локальным тестам эмулятор не нужен. Всё ниже выполняется только в режиме device.
    if ($Mode -eq 'device') {
        if ($EmulatorPort % 2 -ne 0) { throw 'Emulator port must be even.' }
        $script:adb = Join-Path $SdkPath 'platform-tools\adb.exe'
        $emulator = Join-Path $SdkPath 'emulator\emulator.exe'
        if (-not (Test-Path $script:adb) -or -not (Test-Path $emulator)) { throw 'Install SDK platform-tools and emulator.' }
        $imagePath = Join-Path $SdkPath $SystemImage
        if (-not (Test-Path (Join-Path $imagePath 'system.img'))) { throw "System image missing: $imagePath" }
        # AVD — профиль виртуального устройства. Создаём его внутри проекта,
        # отдельно от личных эмуляторов, настроенных в Android Studio.
        $avdHome = Join-Path $work 'avd'
        $avdName = 'Intime_Test_API35'
        $avdPath = Join-Path $avdHome "$avdName.avd"
        New-Item -ItemType Directory -Path $avdPath -Force | Out-Null
        # Начальная конфигурация: API 35, 2 ядра, 2 ГБ RAM, программная графика.
        $config = @"
AvdId=$avdName
avd.ini.displayname=Intime Test API 35
avd.ini.encoding=UTF-8
abi.type=x86_64
hw.cpu.arch=x86_64
hw.cpu.ncore=2
hw.ramSize=2048
hw.lcd.width=1080
hw.lcd.height=1920
hw.lcd.density=420
hw.gpu.enabled=yes
hw.gpu.mode=software
hw.keyboard=yes
hw.audioInput=no
hw.camera.back=none
hw.camera.front=none
disk.dataPartition.size=2G
image.sysdir.1=$imagePath\
tag.id=google_apis_playstore
target=android-35
showDeviceFrame=no
fastboot.forceColdBoot=yes
"@
        # Сохраняем настройки и данные между прогонами; личные AVD не изменяем.
        $configPath = Join-Path $avdPath 'config.ini'
        if (-not (Test-Path $configPath)) { Set-Content $configPath $config -Encoding ASCII }
        else {
            # Эмулятор переписывает INI с пробелами вокруг «=»; путь может быть относительным.
            $currentImage = Get-Content $configPath | Where-Object { $_ -match '^image\.sysdir\.1\s*=' } | Select-Object -First 1
            if (-not $currentImage) { throw 'Existing test AVD has no system image.' }
            $storedImagePath = ($currentImage -split '=', 2)[1].Trim()
            if (-not [IO.Path]::IsPathRooted($storedImagePath)) { $storedImagePath = Join-Path $SdkPath $storedImagePath }
            if ([IO.Path]::GetFullPath($storedImagePath).TrimEnd('\') -ne [IO.Path]::GetFullPath($imagePath).TrimEnd('\')) { throw 'Existing test AVD uses a different system image; select its original image.' }
        }
        Set-Content (Join-Path $avdHome "$avdName.ini") "avd.ini.encoding=UTF-8`npath=$avdPath`ntarget=android-35" -Encoding ASCII
        $env:ANDROID_AVD_HOME = $avdHome
        $devices = Invoke-Adb @('devices')
        # Если порт занят, проверяем имя AVD, сохранённый PID и время старта процесса.
        # Одного совпадения имени недостаточно: нельзя случайно использовать чужое устройство.
        if ($devices -match "(?m)^$serial\s") {
            $name = Invoke-Adb @('-s', $serial, 'emu', 'avd', 'name')
            if ($name.Split("`n")[0].Trim() -ne $avdName) { throw "Port $EmulatorPort belongs to another emulator. Choose another port." }
            if (-not (Test-Path $emulatorState)) { throw 'Emulator ownership record missing; refusing to reuse an unowned emulator.' }
            $state = Get-Content $emulatorState -Raw | ConvertFrom-Json
            $existing = Get-Process -Id $state.processId -ErrorAction SilentlyContinue
            if (-not $existing -or $state.serial -ne $serial -or $existing.StartTime.ToUniversalTime().ToString('o') -ne $state.started) { throw 'Emulator ownership record is stale; refusing to reuse it.' }
            $deviceOwned = $true
        } else {
            # Новый экземпляр запускаем в фоне после проверки аппаратного ускорения.
            $check = Join-Path $SdkPath 'emulator\emulator-check.exe'
            if ((Invoke-LoggedProcess $check @('accel') 'acceleration' 30) -ne 0) { throw 'Hardware acceleration unavailable. See acceleration logs.' }
            $argsLine = (@('-avd', $avdName, '-port', "$EmulatorPort", '-no-window', '-no-audio', '-no-snapshot', '-gpu', 'software') | ForEach-Object { Quote-Argument $_ }) -join ' '
            $ownedEmulator = Start-Process $emulator -ArgumentList $argsLine -WorkingDirectory $root -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $run 'emulator.stdout.log') -RedirectStandardError (Join-Path $run 'emulator.stderr.log')
            # Запись позволяет безопасно переиспользовать экземпляр после KeepEmulator.
            @{ processId = $ownedEmulator.Id; started = $ownedEmulator.StartTime.ToUniversalTime().ToString('o'); serial = $serial } | ConvertTo-Json | Set-Content $emulatorState -Encoding UTF8
            $deviceOwned = $true
        }
        Write-Output "Waiting for $serial (maximum $BootTimeoutSeconds seconds)..."
        $bootDeadline = (Get-Date).AddSeconds($BootTimeoutSeconds)
        $booted = $false
        # Доступность adb ещё не означает, что Android загрузился: ждём системный флаг.
        while ((Get-Date) -lt $bootDeadline) {
            if ($ownedEmulator -and $ownedEmulator.HasExited) { throw "Emulator exited. Logs: $run" }
            try { $booted = (Invoke-Adb @('-s', $serial, 'shell', 'getprop', 'sys.boot_completed')) -eq '1' } catch { $booted = $false }
            if ($booted) { break }
            Start-Sleep -Seconds 2
        }
        if (-not $booted) { throw 'TIMEOUT: emulator did not boot.' }
        # Явно направляем инструментальные тесты только на выбранное тестовое устройство.
        $env:ANDROID_SERIAL = $serial
        $summary.serial = $serial
    }

    $task = if ($Mode -eq 'local') { ':app:testDebugUnitTest' } else { ':app:connectedDebugAndroidTest' }
    # Запускаем Gradle Wrapper через Java. forceTestRun требует свежего выполнения тестов,
    # но сохраняет инкрементальную компиляцию. no-daemon упрощает очистку при таймауте.
    # no-watch-fs обходит найденное зависание при перечислении файловых систем.
    $gradleArgs = @('-Dfile.encoding=UTF-8', '-classpath', $wrapper, 'org.gradle.wrapper.GradleWrapperMain', $task, '--no-daemon', '--no-watch-fs', '--console=plain', '--stacktrace', '-PforceTestRun=true', "-Dorg.gradle.java.home=$JdkPath")
    if ($Offline) { Sync-RobolectricCache; $gradleArgs += '--offline'; $gradleArgs += '-PtestOffline=true' }
    # У локальных и инструментальных тестов разные способы передачи фильтра.
    if ($Filter) {
        if ($Mode -eq 'local') { $gradleArgs += @('--tests', $Filter) }
        else { $gradleArgs += "-Pandroid.testInstrumentationRunnerArguments.class=$Filter" }
    }
    $sourceReports = if ($Mode -eq 'local') { Join-Path $root 'app\build\test-results\testDebugUnitTest' } else { Join-Path $root 'app\build\outputs\androidTest-results\connected\debug' }
    $executionStart = Get-Date
    Write-Output "Running $task. Logs: $run"
    $gradleCode = Invoke-LoggedProcess $java $gradleArgs 'gradle' $TimeoutSeconds
    $summary.gradleExitCode = $gradleCode
    if ($Mode -eq 'local' -and -not $Offline) { Sync-RobolectricCache }
    # Берём только XML, изменённые после начала запуска: старые отчёты не доказывают успех.
    $reports = @(Get-ChildItem $sourceReports -Filter '*.xml' -Recurse -ErrorAction SilentlyContinue | Where-Object { $_.LastWriteTime -ge $executionStart })
    $reportCopy = Join-Path $run 'junit'
    New-Item -ItemType Directory -Path $reportCopy -Force | Out-Null
    # Сохраняем копии JUnit-отчётов и суммируем тесты, падения, ошибки и пропуски.
    foreach ($report in $reports) {
        Copy-Item -LiteralPath $report.FullName -Destination $reportCopy
        [xml]$xml = Get-Content -LiteralPath $report.FullName -Raw
        $suites = @($xml.SelectNodes('//testsuite'))
        foreach ($suite in $suites) {
            foreach ($field in @('tests', 'failures', 'errors', 'skipped')) {
                $value = $suite.GetAttribute($field)
                if ($value) { $summary[$field] += [int]$value }
            }
        }
    }
    # Успех возможен только при нулевом коде Gradle и ненулевом числе свежих тестов.
    if ($summary.failures -gt 0 -or $summary.errors -gt 0) { $summary.status = 'FAILED'; $exitCode = 1 }
    elseif ($gradleCode -eq 0 -and $summary.tests -gt 0) { $summary.status = 'PASSED'; $exitCode = 0 }
    else { $summary.message = 'Gradle failed or no fresh test results were produced.' }
    # При проблемах на устройстве дополнительно сохраняем последние сообщения Android.
    if ($Mode -eq 'device' -and $exitCode -ne 0) {
        try { $null = Invoke-LoggedProcess $script:adb @('-s', $serial, 'logcat', '-d', '-t', '2000') 'logcat' 20 } catch { }
    }
} catch {
    # Ошибки подготовки и таймауты превращаем в понятный итог, сохраняя причину.
    $summary.message = $_.Exception.Message
    if ($summary.message -like 'TIMEOUT:*') { $summary.status = 'TIMEOUT'; $exitCode = 124 }
    Write-Output $summary.message
    if ($deviceOwned) {
        try { $null = Invoke-LoggedProcess $script:adb @('-s', $serial, 'logcat', '-d', '-t', '2000') 'logcat' 20 } catch { }
    }
} finally {
    # Очистка выполняется и при успехе, и при ошибке. Переиспользованный эмулятор
    # оставляем работать; свой останавливаем, если не задан KeepEmulator или не было загрузки.
    if ($ownedEmulator -and (-not $KeepEmulator -or -not $booted)) {
        if ($booted -and -not $ownedEmulator.HasExited) {
            try {
                # Сначала штатное завершение; принудительная остановка — запасной вариант.
                $null = Invoke-Adb @('-s', $serial, 'emu', 'kill')
                $null = $ownedEmulator.WaitForExit(10000)
            } catch { }
        }
        Stop-OwnedProcess $ownedEmulator
        Remove-Item -LiteralPath $emulatorState -ErrorAction SilentlyContinue
    }
    # Возвращаем окружение, освобождаем блокировку и записываем итог даже при ошибке.
    $env:ANDROID_AVD_HOME = $oldAvdHome
    $env:ANDROID_SERIAL = $oldSerial
    $env:ANDROID_HOME = $oldSdk
    if ($lock) { $lock.Dispose() }
    $summary.finished = (Get-Date).ToString('o')
    $summary.exitCode = $exitCode
    $summary.durationSeconds = [Math]::Round($timer.Elapsed.TotalSeconds, 2)
    $summary | ConvertTo-Json | Set-Content (Join-Path $run 'summary.json') -Encoding UTF8
    Write-Output "$($summary.status): tests=$($summary.tests), failures=$($summary.failures), errors=$($summary.errors). Reports: $run"
}
# Код нужен вызывающей программе или CI, чтобы отличать успех от разных видов ошибки.
exit $exitCode
