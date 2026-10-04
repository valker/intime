<#
Проверка второго естественного периода WorkManager после завершения процесса.
Перед запуском: test.ps1 -Mode device -TestApi 36 -Filter ...ScreenPlatformTest -Offline -KeepEmulator.
Используется только существующий Intime_Test_API36; новые SDK-образы не нужны.
Устанавливаются собственные .dev APK, синтетическая файловая Room удаляется вместе
с этой установкой в finally. Уже установленный .dev пакет скрипт не перезаписывает.
Ожидание до 50 минут: часы и период не ускоряются, jobs вручную не запускаются.
ReduceBatching временно разрешает одиночный готовый CPU job; это отдельное условие теста.
#>
[CmdletBinding()]
param(
    [ValidateRange(16, 60)][int]$WaitMinutes = 50,
    [switch]$ReduceBatching
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$work = Join-Path $root '.test-tools'
$run = Join-Path $work ('runs\' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-periodic-smoke')
New-Item -ItemType Directory -Path $run -Force | Out-Null
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$serial = 'emulator-5584'
$package = 'com.vpe_soft.intime.intime.dev'
$testPackage = "$package.test"
$runner = "$testPackage/androidx.test.runner.AndroidJUnitRunner"
$installedApp = $false
$installedTests = $false
$batchConfigChanged = $false
$batchConfigOriginal = $null
$lock = $null
$summary = @{ status = 'RUNNING'; reports = $run; testApi = 36; passedPhases = @() }

# Каждый вызов адресован одному проектному AVD. Stderr с exit 0 тоже сохраняется;
# код завершения, а не наличие текста в stderr, определяет успех.
function Invoke-PeriodicAdb([string[]]$Arguments, [string]$Name) {
    $previous = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = & $adb -s $serial @Arguments 2>&1
        $code = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previous }
    $text = ($output | ForEach-Object { "$_" }) -join "`n"
    Set-Content (Join-Path $run "$Name.log") $text -Encoding UTF8
    if ($code -ne 0) { throw "adb failed ($code): $Name" }
    return $text
}

# Требуем один исполненный тест и отвергаем Assume/ignored, crash и abort даже при adb exit 0.
function Invoke-PeriodicPhase([string]$Method) {
    $class = "com.vpe_soft.intime.intime.receiver.PeriodicRecoveryTest#$Method"
    $output = Invoke-PeriodicAdb @('shell', 'am', 'instrument', '-w', '-r', '-e', 'periodicPhase', 'true', '-e', 'class', $class, $runner) $Method
    if ($output -notmatch 'OK \(1 test\)' -or $output -match 'FAILURES!!!|INSTRUMENTATION_FAILED|Test run aborted|shortMsg=|INSTRUMENTATION_STATUS_CODE:\s*-(3|4)') { throw "Phase failed: $Method" }
    $summary.passedPhases += $Method
    Write-Output "PASSED: $Method"
    return $output
}

try {
    $lock = [IO.File]::Open((Join-Path $work 'run.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
    $owner = Get-Content (Join-Path $work 'emulator.json') -Raw | ConvertFrom-Json
    if ($owner.serial -ne $serial) { throw 'Expected owned API36' }
    $process = Get-Process -Id $owner.processId -ErrorAction Stop
    if ($process.StartTime.ToUniversalTime().ToString('o') -ne ([datetime]$owner.started).ToUniversalTime().ToString('o')) { throw 'Emulator PID ownership mismatch' }
    $avd = Invoke-PeriodicAdb @('emu', 'avd', 'name') 'avd'
    if ($avd.Split("`n")[0].Trim() -ne 'Intime_Test_API36') { throw 'Refusing another AVD' }
    if ((Invoke-PeriodicAdb @('shell', 'getprop', 'ro.build.version.sdk') 'api').Trim() -ne '36') { throw 'Expected API36' }
    if ((Invoke-PeriodicAdb @('shell', 'dumpsys', 'window') 'window-before') -match 'Application Not Responding:') { throw 'ANR dialog before periodic smoke' }
    foreach ($candidate in @($package, $testPackage)) {
        $packages = Invoke-PeriodicAdb @('shell', 'pm', 'list', 'packages', $candidate) "existing-$candidate"
        if ($packages -match ('(?m)^package:' + [regex]::Escape($candidate) + '\s*$')) { throw "Refusing existing installation: $candidate" }
    }
    if ($ReduceBatching) {
        # Android может держать единственный CPU job готовым до 31 минуты.
        # Опционально меняем только пакетный порог, не WorkManager interval/часы.
        # Сохраняем точный override (включая null), чтобы вернуть его в finally.
        $batchConfigOriginal = (Invoke-PeriodicAdb @('shell', 'device_config', 'get', 'jobscheduler', 'min_ready_cpu_only_jobs_count') 'batch-config-before').Trim()
        if ($batchConfigOriginal -notmatch '^(null|[0-9]+)$') { throw 'Unknown JobScheduler override' }
        $summary.batchThresholdTemporarilyRelaxed = $true
        $summary.originalBatchOverride = $batchConfigOriginal
        $batchConfigChanged = $true
        Invoke-PeriodicAdb @('shell', 'device_config', 'put', 'jobscheduler', 'min_ready_cpu_only_jobs_count', '1') 'reduce-batching' | Out-Null
    }
    Invoke-PeriodicAdb @('install', (Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk')) 'install-app' | Out-Null
    $installedApp = $true
    Invoke-PeriodicAdb @('install', (Join-Path $root 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk')) 'install-tests' | Out-Null
    $installedTests = $true
    # Первый запуск снимает stopped/notLaunched, затем Home позволяет worker отправлять уведомления.
    Invoke-PeriodicAdb @('shell', 'pm', 'grant', $package, 'android.permission.POST_NOTIFICATIONS') 'grant' | Out-Null
    Invoke-PeriodicAdb @('shell', 'am', 'start', '-W', '-n', "$package/com.vpe_soft.intime.intime.activity.MainActivityV2") 'launch' | Out-Null
    Invoke-PeriodicAdb @('shell', 'input', 'keyevent', '3') 'home' | Out-Null
    $prepared = (Invoke-PeriodicPhase 'prepareFirstRun') -join "`n"
    $firstMatch = [regex]::Match($prepared, 'periodic_first_post=(\d+)')
    $descriptionMatch = [regex]::Match($prepared, 'periodic_description=(PERIODIC_RECOVERY_\d+)')
    if (-not $firstMatch.Success -or -not $descriptionMatch.Success) { throw 'Fixture status missing' }
    $firstPost = [long]$firstMatch.Groups[1].Value
    $description = $descriptionMatch.Groups[1].Value
    $summary.firstPost = $firstPost
    $summary.description = $description
    # am kill убивает только фоновый процесс, сохраняя jobs и stopped=false.
    # Force-stop запрещает фоновые запуски и не моделирует обычную смерть процесса.
    Invoke-PeriodicAdb @('shell', 'am', 'kill', $package) 'kill-background-process' | Out-Null
    Start-Sleep -Seconds 2
    $processes = Invoke-PeriodicAdb @('shell', "ps -A | grep '$package' || true") 'processes-after-kill'
    if ($processes.Trim()) { throw 'App process did not terminate' }
    $packageState = Invoke-PeriodicAdb @('shell', 'dumpsys', 'package', $package) 'package-after-kill'
    if ($packageState -notmatch 'User 0:.*stopped=false') { throw 'App is stopped after kill' }
    $summary.processAbsentAfterKill = $true
    $limit = (Get-Date).AddMinutes($WaitMinutes)
    $repeated = $false
    $poll = 0
    Write-Output 'Waiting for second natural periodic run (at least 15 minutes)...'
    do {
        # Повторно пишем лишь последние снимки: ожидание не раздувает папку отчётов.
        # До второго уведомления не запускаем Activity/instrumentation/JobScheduler run.
        $notifications = Invoke-PeriodicAdb @('shell', 'dumpsys', 'notification', '--noredact') 'notifications-poll'
        $record = [regex]::Match($notifications, '(?ms)^\s*NotificationRecord\([^\r\n]*pkg=com\.vpe_soft\.intime\.intime\.dev [^\r\n]*\r?\n(?:(?!^\s*NotificationRecord\().)*')
        if ($record.Success -and $record.Value.Contains("android.text=String ($description)")) {
            $when = [regex]::Match($record.Value, '(?m)^\s*when=(\d+)/')
            if ($when.Success -and [long]$when.Groups[1].Value -ge $firstPost + 900000) {
                $summary.secondNotificationWhen = [long]$when.Groups[1].Value
                $repeated = $true
                break
            }
        }
        $poll++
        if ($poll % 2 -eq 0) { Write-Output "Still waiting: $([math]::Round(($poll * 30) / 60, 1)) minutes observed" }
        Start-Sleep -Seconds 30
    } while ((Get-Date) -lt $limit)
    if (-not $repeated) { throw 'Second periodic notification timed out' }
    # Снимки до instrumentation доказывают доставку без её побочного пробуждения приложения.
    Invoke-PeriodicAdb @('shell', 'dumpsys', 'jobscheduler') 'jobs-before-verification' | Out-Null
    Invoke-PeriodicAdb @('logcat', '-d') 'logcat-before-verification' | Out-Null
    Invoke-PeriodicPhase 'verifySecondRun' | Out-Null
    $summary.status = 'PASSED'
} catch {
    $summary.status = 'FAILED'
    $summary.message = $_.Exception.Message
    Write-Output $summary.message
} finally {
    if ($batchConfigChanged) {
        try {
            if ($batchConfigOriginal -eq 'null') {
                Invoke-PeriodicAdb @('shell', 'device_config', 'delete', 'jobscheduler', 'min_ready_cpu_only_jobs_count') 'restore-batch-config' | Out-Null
            } else {
                Invoke-PeriodicAdb @('shell', 'device_config', 'put', 'jobscheduler', 'min_ready_cpu_only_jobs_count', $batchConfigOriginal) 'restore-batch-config' | Out-Null
            }
            $restored = (Invoke-PeriodicAdb @('shell', 'device_config', 'get', 'jobscheduler', 'min_ready_cpu_only_jobs_count') 'batch-config-restored').Trim()
            if ($restored -ne $batchConfigOriginal) { throw 'JobScheduler override restoration mismatch' }
            $summary.batchConfigOverrideRestored = $true
        } catch { $summary.batchCleanupError = $_.Exception.Message; $summary.status = 'FAILED' }
    }
    if ($installedApp) {
        try {
            $log = Invoke-PeriodicAdb @('logcat', '-d') 'logcat-final'
            if ($log -match 'ANR in|FATAL EXCEPTION') { $summary.healthError = 'ANR or fatal exception in device logs'; $summary.status = 'FAILED' }
        } catch { $summary.diagnosticError = $_.Exception.Message; $summary.status = 'FAILED' }
    }
    # Удаляем только установленные этим запуском APK; фикстура/Room/jobs удалятся с ними
    # даже если prepare/verify упали. Эмулятор остаётся для контролируемой остановки.
    if ($installedTests) { try { Invoke-PeriodicAdb @('uninstall', $testPackage) 'uninstall-tests' | Out-Null } catch { $summary.cleanupError = $_.Exception.Message; $summary.status = 'FAILED' } }
    if ($installedApp) { try { Invoke-PeriodicAdb @('uninstall', $package) 'uninstall-app' | Out-Null } catch { $summary.appCleanupError = $_.Exception.Message; $summary.status = 'FAILED' } }
    if ($lock) { $lock.Dispose() }
    $summary | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $run 'summary.json') -Encoding UTF8
    Write-Output "$($summary.status). Reports: $run"
}
if ($summary.status -ne 'PASSED') { exit 1 }
