<#
Проверяет install -r копии старого Play APK 24 → debug APK 25 на проектном API 35.
Перед запуском: scripts/test.ps1 -Mode device -Filter com.vpe_soft.intime.intime.database.HistoricalMigrationPlatformTest -Offline -KeepEmulator.
Старые APK копируются и переподписываются стандартным debug-ключом; оригиналы не меняются.
Проверка не подтверждает совместимость production-ключа. Данные только синтетические.
#>
[CmdletBinding()]
param([Parameter(Mandatory = $true)][string]$ApkDirectory, [switch]$Offline, [switch]$KeepEmulator)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$work = Join-Path $root '.test-tools'
$run = Join-Path $work ('runs\' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-upgrade-smoke-' + [guid]::NewGuid().ToString('N').Substring(0, 8))
New-Item -ItemType Directory -Path $run | Out-Null
$sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$adb = Join-Path $sdk 'platform-tools\adb.exe'
$java = 'C:\Program Files\Java\jdk-17\bin\java.exe'
$signer = Join-Path $sdk 'build-tools\35.0.0\lib\apksigner.jar'
$aapt = Join-Path $sdk 'build-tools\35.0.0\aapt.exe'
$debugKey = Join-Path ([Environment]::GetFolderPath('UserProfile')) '.android\debug.keystore'
$serial = 'emulator-5580'
$package = 'com.vpe_soft.intime.intime'
$runner = "$package.test/com.vpe_soft.intime.intime.database.UpgradeSmokeInstrumentation"
$statePath = Join-Path $work 'emulator.json'
$lock = $null
$owned = $false
$installed = $false
$testsInstalled = $false
$summary = [ordered]@{ status='RUNNING'; started=(Get-Date).ToString('o'); reports=$run; phases=@(); signatureScope='debug-key copies, not production signing'; serial=$serial }

# Каждый процесс получает отдельные журналы и таймаут; останавливается только его PID.
function Invoke-Logged([string]$Exe, [string[]]$Arguments, [string]$Name, [int]$Seconds = 60) {
    $quoted = $Arguments | ForEach-Object {
        if ($_ -match '["\r\n]') { throw 'Quotes/newlines are not supported in process arguments.' }
        '"' + $_.TrimEnd('\') + '"'
    }
    $stdout = Join-Path $run "$Name.stdout.log"
    $stderr = Join-Path $run "$Name.stderr.log"
    $process = Start-Process -FilePath $Exe -ArgumentList ($quoted -join ' ') -WorkingDirectory $root -WindowStyle Hidden -PassThru -RedirectStandardOutput $stdout -RedirectStandardError $stderr
    $null = $process.Handle
    try {
        if (-not $process.WaitForExit($Seconds * 1000)) {
            & "$env:SystemRoot\System32\taskkill.exe" /PID $process.Id /T /F | Out-Null
            throw "TIMEOUT: $Name"
        }
        $process.WaitForExit()
        if ($process.ExitCode -ne 0) { throw "$Name failed ($($process.ExitCode)); see $run" }
        return [string](Get-Content -LiteralPath $stdout -Raw)
    } finally { $process.Dispose() }
}
function Invoke-Adb([string[]]$Arguments, [string]$Name) {
    return Invoke-Logged $adb (@('-s', $serial) + $Arguments) $Name
}
function Invoke-Phase([string]$ClassMethod, [string]$Phase) {
    $output = Invoke-Adb @('shell','am','instrument','-w','-r','-e','class',$ClassMethod,'-e','upgradePhase',$Phase,$runner) $Phase
    if ($output -notmatch 'OK \(1 test\)' -or $output -match 'FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_STATUS_CODE:\s*-(3|4)') {
        throw "Instrumentation failed: $Phase"
    }
    $summary.phases += $Phase
    Write-Output "PASSED: $Phase"
}
function Launch-App([string]$Activity, [string]$Name) {
    $output = Invoke-Adb @('shell','am','start','-W','-n',"$package/$Activity") $Name
    if ($output -notmatch 'Status: ok' -or $output -match 'Error:') { throw "Launcher failed: $Name" }
    Invoke-Adb @('shell','input','keyevent','3') "$Name-home" | Out-Null
}

try {
    # Общая блокировка исключает параллельную сборку и работу других тестов с этим AVD.
    $lock = [IO.File]::Open((Join-Path $work 'run.lock'), 'OpenOrCreate','ReadWrite','None')
    $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
    $emulatorProcess = Get-Process -Id $state.processId -ErrorAction Stop
    if ($state.serial -ne $serial -or $emulatorProcess.StartTime.ToUniversalTime().ToString('o') -ne $state.started) { throw 'Stale emulator ownership record.' }
    $avd = Invoke-Adb @('emu','avd','name') 'avd'
    if ($avd.Split("`n")[0].Trim() -ne 'Intime_Test_API35') { throw 'Refusing another AVD.' }
    if ((Invoke-Adb @('shell','getprop','ro.build.version.sdk') 'api').Trim() -ne '35') { throw 'Expected API 35.' }
    $owned = $true
    if ((Invoke-Adb @('shell','pm','list','packages',$package) 'existing-package') -match '(?m)^package:com\.vpe_soft\.intime\.intime\s*$') { throw 'Production package already present on test AVD; refusing to replace unknown state.' }

    # Проверяем исходную копию и собираем только разрешённые debug-варианты.
    $source = (Resolve-Path -LiteralPath $ApkDirectory).Path
    $manifest = Get-Content -LiteralPath (Join-Path $source 'source.json') -Raw | ConvertFrom-Json
    if ($manifest.package -ne $package) { throw 'Unexpected source package.' }
    $base = Join-Path $source 'base.apk'
    $badging = Invoke-Logged $aapt @('dump','badging',$base) 'source-badging'
    if ($badging -notmatch "package: name='com.vpe_soft.intime.intime' versionCode='24'") { throw 'Expected Play APK versionCode 24.' }
    $gradle = @('-Dfile.encoding=UTF-8','-classpath',(Join-Path $root 'gradle\wrapper\gradle-wrapper.jar'),'org.gradle.wrapper.GradleWrapperMain',':app:assembleDebug',':app:assembleDebugAndroidTest','-PupgradeSmoke=true','--no-daemon','--no-watch-fs','--console=plain')
    if ($Offline) { $gradle += '--offline' }
    Invoke-Logged $java $gradle 'build-upgrade' 900 | Out-Null
    $newApk = Join-Path $run 'new-debug.apk'
    $testApk = Join-Path $run 'tests-debug.apk'
    Copy-Item -LiteralPath (Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk') -Destination $newApk
    Copy-Item -LiteralPath (Join-Path $root 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk') -Destination $testApk
    $newBadging = Invoke-Logged $aapt @('dump','badging',$newApk) 'new-badging'
    if ($newBadging -notmatch "package: name='com.vpe_soft.intime.intime' versionCode='25'") { throw 'Wrong new APK identity.' }
    $newCert = Invoke-Logged $java @('-jar',$signer,'verify','--print-certs',$newApk) 'new-certificate'
    $certificate = [regex]::Match($newCert,'Signer #1 certificate SHA-256 digest: (\w+)').Groups[1].Value
    if (-not $certificate) { throw 'Missing debug signer.' }
    $summary.testCertificateSha256 = $certificate
    $oldCopies = @()
    foreach ($entry in $manifest.files) {
        if ($entry.file -notmatch '^[A-Za-z0-9_.-]+\.apk$') { throw 'Unexpected source filename.' }
        $original = Join-Path $source $entry.file
        if ((Get-FileHash -LiteralPath $original -Algorithm SHA256).Hash -ne $entry.sha256) { throw 'Source hash mismatch.' }
        $copy = Join-Path $run ('old-' + $entry.file)
        Invoke-Logged $java @('-jar',$signer,'sign','--ks',$debugKey,'--ks-key-alias','androiddebugkey','--ks-pass','pass:android','--key-pass','pass:android','--v4-signing-enabled','false','--out',$copy,$original) ('sign-' + $entry.file) | Out-Null
        $oldCert = Invoke-Logged $java @('-jar',$signer,'verify','--print-certs',$copy) ('verify-' + $entry.file)
        if ($oldCert -notmatch [regex]::Escape("Signer #1 certificate SHA-256 digest: $certificate")) { throw 'Debug signer mismatch.' }
        $oldCopies += $copy
    }

    # До подготовки фикстуры запускается именно старый launcher: базу создаёт APK 1.1.11.
    Invoke-Adb (@('install-multiple') + $oldCopies) 'install-old' | Out-Null
    $installed = $true
    Launch-App 'com.vpe_soft.intime.intime.activity.MainActivity' 'launch-old'
    Invoke-Adb @('shell','am','force-stop',$package) 'stop-old' | Out-Null
    Invoke-Adb @('install',$testApk) 'install-tests' | Out-Null
    $testsInstalled = $true
    Invoke-Phase 'com.vpe_soft.intime.intime.database.UpgradePrepareTest#prepareOldApplication' 'prepare'
    Invoke-Adb @('shell','am','force-stop',$package) 'stop-prepared' | Out-Null

    # Между старым и новым APK нет uninstall/pm clear: данные должны пережить install -r.
    Invoke-Adb @('install','-r',$newApk) 'update' | Out-Null
    Invoke-Phase 'com.vpe_soft.intime.intime.database.UpgradeVerificationTest#verifyAfterUpdate' 'verify'
    Invoke-Adb @('shell','am','force-stop',$package) 'stop-updated' | Out-Null
    Launch-App 'com.vpe_soft.intime.intime.activity.MainActivityV2' 'launch-new'
    Invoke-Phase 'com.vpe_soft.intime.intime.database.UpgradeVerificationTest#verifyAfterRestart' 'restart'
    $fixture = Invoke-Adb @('shell','run-as',$package,'cat','shared_prefs/upgrade_fixture.xml') 'fixture'
    $deadline = [regex]::Match($fixture,'<long name="alarm" value="(\d+)"').Groups[1].Value
    if (-not $deadline) { throw 'Missing expected alarm deadline.' }
    $dump = Invoke-Adb @('shell','dumpsys','alarm') 'alarms'
    $pattern = '(?m)^\s*RTC_WAKEUP #\d+: Alarm\{\S+ type 0 origWhen ' + $deadline + ' [^\r\n]* com\.vpe_soft\.intime\.intime\}\r?\n\s+tag=\*walarm\*:com\.vpe_soft\.intime\.intime/(?:com\.vpe_soft\.intime\.intime)?\.receiver\.AlarmReceiver\s*$'
    if ($dump -notmatch $pattern) { throw 'Updated app did not schedule expected alarm.' }
    $summary.alarmDeadline = $deadline
    $summary.phases += 'alarm'
    $summary.status = 'PASSED'
} catch {
    $summary.status = 'FAILED'; $summary.message = $_.Exception.Message
    Write-Warning $summary.message
    if ($owned) {
        try { Invoke-Adb @('logcat','-b','crash','-d') 'failure-crash-buffer' | Out-Null } catch {}
    }
} finally {
    # Удаляются лишь пакеты, установленные этим smoke; личные устройства недоступны.
    try {
        if ($owned -and $testsInstalled) { Invoke-Adb @('uninstall',"$package.test") 'cleanup-tests' | Out-Null }
        if ($owned -and $installed) { Invoke-Adb @('uninstall',$package) 'cleanup-app' | Out-Null }
        if ($owned -and -not $KeepEmulator) {
            $live = Get-Process -Id $state.processId -ErrorAction SilentlyContinue
            if ($live -and $live.StartTime.ToUniversalTime().ToString('o') -eq $state.started) {
                Invoke-Adb @('emu','kill') 'stop-emulator' | Out-Null
                $live.WaitForExit(30000) | Out-Null
                if (Get-Process -Id $state.processId -ErrorAction SilentlyContinue) { throw 'Owned emulator did not stop.' }
                Remove-Item -LiteralPath $statePath
            }
        }
    } catch { $summary.status='FAILED'; $summary.cleanupError=$_.Exception.Message }
    if ($lock) { $lock.Dispose() }
    $summary.finished = (Get-Date).ToString('o')
    $summary | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $run 'summary.json') -Encoding UTF8
}
Write-Output "$($summary.status). Reports: $run"
if ($summary.status -ne 'PASSED') { exit 1 }
