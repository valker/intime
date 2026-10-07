<#
Сверяет принудительный Doze с состоянием PowerManager и AlarmManager.
Работает только с уже запущенным и принадлежащим проекту тестовым AVD.
Не устанавливает APK, не вызывает receiver, не изменяет квоты или часы.
Подтверждает готовность idle-политики, но не её применение к alarm приложения.
#>
[CmdletBinding()]
param(
    [ValidateSet(31,33,35,36)][int]$TestApi = 36,
    # Только при отсутствующем idle-until в уже подтверждённом forced IDLE:
    # один переход через maintenance может восстановить системный alarm.
    # Это управляемая подготовка, а не естественный вход в Doze.
    [switch]$RepairMissingIdleUntil
)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$run = Join-Path $root ('.test-tools/runs/' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-doze-policy')
New-Item -ItemType Directory -Path $run | Out-Null
$summary = @{status='INFRASTRUCTURE_ERROR'; testApi=$TestApi; reports=$run; quotaEnforcementValidated=$false}
$adb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
$serial = @{31='emulator-5586';33='emulator-5582';35='emulator-5580';36='emulator-5584'}[$TestApi]
$changed = $false
$counter = 0

# Каждый вызов имеет собственный журнал и предел времени. При тайм-ауте
# останавливается только созданный этим вызовом процесс adb, не сервер adb.
function Invoke-PolicyAdb([string[]]$Arguments) {
    $script:counter++
    $out = Join-Path $run "adb-$script:counter.stdout.log"
    $err = Join-Path $run "adb-$script:counter.stderr.log"
    $process = Start-Process $adb -ArgumentList (@('-s',$serial)+$Arguments) -WindowStyle Hidden -PassThru -RedirectStandardOutput $out -RedirectStandardError $err
    $null = $process.Handle
    if (-not $process.WaitForExit(30000)) { Stop-Process -Id $process.Id; throw 'Own adb call timed out' }
    if ($process.ExitCode -ne 0) { throw "adb failed: $([IO.File]::ReadAllText($err))" }
    return [IO.File]::ReadAllText($out)
}

# Снимки читают три независимых службы. IDLE команды не подменяет наличие
# системного idle-until alarm. Проверка не делает вывод о насыщении квоты.
function Read-PolicySnapshot([string]$Name) {
    $idle = Invoke-PolicyAdb -Arguments @('shell','dumpsys','deviceidle')
    $power = Invoke-PolicyAdb -Arguments @('shell','dumpsys','power')
    $alarm = Invoke-PolicyAdb -Arguments @('shell','dumpsys','alarm')
    foreach ($entry in @(@('idle',$idle),@('power',$power),@('alarm',$alarm))) {
        [IO.File]::WriteAllText((Join-Path $run "$Name-$($entry[0]).log"), $entry[1], (New-Object Text.UTF8Encoding($true)))
    }
    return @{
        controllerIdle=($idle -match '(?m)^\s*mState=IDLE(?:\s|$)')
        powerIdle=($power -match '(?m)^\s*mDeviceIdleMode=true\s*$')
        idleUntilPresent=($alarm -match '(?m)^\s*(?:Idling until|Pending idle until):\s*Alarm\{')
        # Tag должен принадлежать текущему idle-until alarm, а не старой
        # записи в removal history или другому таймеру контроллера.
        deepIdleAlarmPresent=($alarm -match '(?m)^\s*(?:Idling until|Pending idle until):\s*Alarm\{[^\r\n]*\r?\n\s*tag=\*walarm\*:DeviceIdleController\.deep(?:\s|$)')
        motionSensorMissing=($idle -match 'mUseMotionSensor=true mMotionSensor=null')
        compatQuota=([regex]::Match($alarm,'(?m)^\s*allow_while_idle_compat_quota=(\d+)').Groups[1].Value)
        compatWindow=([regex]::Match($alarm,'(?m)^\s*allow_while_idle_compat_window=([^\r\n]+)').Groups[1].Value.Trim())
    }
}

try {
    $state = Get-Content (Join-Path $root '.test-tools/emulator.json') -Raw | ConvertFrom-Json
    $owned = Get-Process -Id $state.processId -ErrorAction Stop
    if ($state.serial -ne $serial -or $owned.StartTime.ToUniversalTime().ToString('o') -ne $state.started) { throw 'Project emulator ownership mismatch' }
    $name = (Invoke-PolicyAdb -Arguments @('emu','avd','name')).Split("`n")[0].Trim()
    if ($name -ne "Intime_Test_API$TestApi") { throw 'Dedicated AVD name mismatch' }
    if ((Invoke-PolicyAdb -Arguments @('shell','getprop','ro.build.version.sdk')).Trim() -ne "$TestApi") { throw 'Device API mismatch' }
    $battery = Invoke-PolicyAdb -Arguments @('shell','dumpsys','battery')
    $idle = Invoke-PolicyAdb -Arguments @('shell','dumpsys','deviceidle')
    if ($battery -match 'UPDATES STOPPED' -or $idle -notmatch 'mForceIdle=false') { throw 'Existing power simulation or forced idle; refusing changes' }
    $enabled = (Invoke-PolicyAdb -Arguments @('shell','cmd','deviceidle','enabled','deep')).Trim()
    if ($enabled -notin @('0','1')) { throw 'Unknown deep idle enabled state' }
    $summary.deepOriginallyEnabled = $enabled -eq '1'
    $summary.before = Read-PolicySnapshot 'before'
    $changed = $true
    if ($enabled -eq '0') { Invoke-PolicyAdb -Arguments @('shell','cmd','deviceidle','enable','deep') | Out-Null }
    Invoke-PolicyAdb -Arguments @('shell','dumpsys','battery','unplug') | Out-Null
    Invoke-PolicyAdb -Arguments @('shell','input','keyevent','223') | Out-Null
    Invoke-PolicyAdb -Arguments @('shell','cmd','deviceidle','force-idle','deep') | Out-Null
    Start-Sleep -Seconds 5
    $summary.initialForced = Read-PolicySnapshot 'initial-forced'
    $summary.controlledMaintenanceCycle = $false
    if ($RepairMissingIdleUntil -and -not $summary.initialForced.idleUntilPresent) {
        # Не вмешиваемся в неизвестное состояние и не повторяем цикл в ожидании.
        # Сохраняем исходные доказательства; после одного шага maintenance может
        # закончиться сама, поэтому перед вторым шагом читаем актуальное состояние.
        if (-not ($summary.initialForced.controllerIdle -and $summary.initialForced.powerIdle)) { throw 'Missing idle-until outside confirmed forced IDLE' }
        $summary.controlledMaintenanceCycle = $true
        $summary.maintenanceStep = (Invoke-PolicyAdb -Arguments @('shell','cmd','deviceidle','step','deep')).Trim()
        $transition = (Invoke-PolicyAdb -Arguments @('shell','cmd','deviceidle','get','deep')).Trim()
        if ($transition -eq 'IDLE_MAINTENANCE') {
            $summary.idleStep = (Invoke-PolicyAdb -Arguments @('shell','cmd','deviceidle','step','deep')).Trim()
        } elseif ($transition -ne 'IDLE') { throw "Unexpected maintenance transition: $transition" }
        Start-Sleep -Seconds 5
    }
    $summary.forced = Read-PolicySnapshot 'forced'
    Start-Sleep -Seconds 20
    $summary.repeated = Read-PolicySnapshot 'repeated'
    $ready = $true
    foreach ($snapshot in @($summary.forced,$summary.repeated)) {
        if (-not ($snapshot.controllerIdle -and $snapshot.powerIdle -and $snapshot.idleUntilPresent -and $snapshot.deepIdleAlarmPresent)) { $ready=$false }
    }
    $summary.status = if ($ready) {'IDLE_POLICY_READY'} else {'IDLE_POLICY_NOT_CONFIRMED'}
} catch { $summary.message=$_.Exception.Message }
finally {
    # Восстанавливаем только внесённые нами изменения даже при ошибке снимка.
    # Образ, данные AVD, квоты и исходный режим deep idle сохраняются.
    if ($changed) {
        $cleanupErrors=@()
        $commands=@(@('shell','cmd','deviceidle','unforce'),@('shell','dumpsys','battery','reset'),@('shell','input','keyevent','224'))
        if (-not $summary.deepOriginallyEnabled) { $commands+=,@('shell','cmd','deviceidle','disable','deep') }
        foreach ($command in $commands) { try { Invoke-PolicyAdb -Arguments $command | Out-Null } catch { $cleanupErrors+=$_.Exception.Message } }
        try {
            $summary.after = Read-PolicySnapshot 'after'
            $batteryAfter = Invoke-PolicyAdb -Arguments @('shell','dumpsys','battery')
            $idleAfter = Invoke-PolicyAdb -Arguments @('shell','dumpsys','deviceidle')
            $enabledAfter = (Invoke-PolicyAdb -Arguments @('shell','cmd','deviceidle','enabled','deep')).Trim()
            if ($batteryAfter -match 'UPDATES STOPPED' -or $idleAfter -notmatch 'mForceIdle=false' -or $enabledAfter -ne $enabled) { throw 'Power cleanup verification failed' }
        } catch { $cleanupErrors+=$_.Exception.Message }
        $summary.cleanup = $cleanupErrors.Count -eq 0
        if ($cleanupErrors.Count) { $summary.cleanupErrors=$cleanupErrors; $summary.status='INFRASTRUCTURE_ERROR' }
    }
    $summary | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $run 'summary.json') -Encoding UTF8
}
Write-Output "$($summary.status). Reports: $run"
if ($summary.status -eq 'INFRASTRUCTURE_ERROR') { exit 2 }
