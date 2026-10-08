<#
Проверяет предпосылки естественного deep Doze на выделенном AVD проекта.
Только читает состояние: не выключает экран, не симулирует батарею, не включает
Doze, не делает force-idle/step и не устанавливает приложение. Готовность среды
не означает подтверждённого естественного входа или доставки уведомления.
#>
[CmdletBinding()]
param([ValidateSet(31,33,35,36)][int]$TestApi = 36)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$run = Join-Path $root ('.test-tools/runs/' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-natural-doze-preflight')
New-Item -ItemType Directory -Path $run | Out-Null
$adb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
$serial = @{31='emulator-5586';33='emulator-5582';35='emulator-5580';36='emulator-5584'}[$TestApi]
$counter = 0
$summary = @{status='INFRASTRUCTURE_ERROR';testApi=$TestApi;reports=$run;readOnly=$true;naturalEntryValidated=$false;notificationDeliveryValidated=$false}

# Разбираем именно текущие поля DeviceIdleController, а не историю состояний.
# При неизвестном формате отказываемся утверждать готовность. Включённый deep
# и доступный используемый контроллером датчик — лишь предпосылки, не PASS Doze.
# Отсутствие требуемого motion sensor и отключённый deep сохраняются независимо.
function Get-NaturalDozeCapability([string]$Idle, [string]$DeepEnabled) {
    $enabled = [regex]::Match($Idle, '(?m)^\s*mLightEnabled=(true|false)\s+mDeepEnabled=(true|false)(?:\s|$)')
    $motion = [regex]::Match($Idle, '(?m)^\s*mUseMotionSensor=(true|false)\s+mMotionSensor=([^\r\n]+)')
    $force = [regex]::Match($Idle, '(?m)^\s*mForceIdle=(true|false)(?:\s|$)')
    if (-not $enabled.Success -or -not $motion.Success -or -not $force.Success -or $DeepEnabled -notin @('0','1')) { throw 'Unknown DeviceIdleController capability format' }
    $deep = $enabled.Groups[2].Value -eq 'true'
    if ($deep -ne ($DeepEnabled -eq '1')) { throw 'Deep enabled snapshots disagree' }
    if ($force.Groups[1].Value -ne 'false') { throw 'Existing forced idle; natural preflight refused' }
    $required = $motion.Groups[1].Value -eq 'true'
    $motionValue = $motion.Groups[2].Value.Trim()
    if($motionValue -ne 'null' -and $motionValue -notmatch '^\{Sensor(?:\s|$)'){throw 'Unknown motion sensor format'}
    $missing = $motionValue -eq 'null'
    $reasons = @()
    if (-not $deep) { $reasons += 'DEEP_DISABLED' }
    if ($required -and $missing) { $reasons += 'REQUIRED_MOTION_SENSOR_MISSING' }
    return @{
        status=$(if($reasons.Count){'NATURAL_DEEP_UNAVAILABLE'}else{'NATURAL_DEEP_PREREQUISITES_PRESENT'})
        reasons=$reasons;deepEnabled=$deep;lightEnabled=($enabled.Groups[1].Value -eq 'true')
        usesMotionSensor=$required;motionSensorMissing=$missing;forcedIdle=$false
    }
}

# Каждый adb процесс ограничен 30 секундами; останавливаем только этот процесс
# при тайм-ауте. Все обращения адресованы проверенному выделенному serial.
function Invoke-NaturalPreflightAdb([string[]]$Arguments) {
    $script:counter++
    $out=Join-Path $run "adb-$script:counter.stdout.log"
    $err=Join-Path $run "adb-$script:counter.stderr.log"
    $process=Start-Process $adb -ArgumentList (@('-s',$serial)+$Arguments) -WindowStyle Hidden -PassThru -RedirectStandardOutput $out -RedirectStandardError $err
    $null=$process.Handle
    if(-not $process.WaitForExit(30000)){Stop-Process -Id $process.Id;throw 'Own adb call timed out'}
    if($process.ExitCode -ne 0){throw "adb failed: $([IO.File]::ReadAllText($err))"}
    return [IO.File]::ReadAllText($out)
}

try {
    $state=Get-Content (Join-Path $root '.test-tools/emulator.json') -Raw|ConvertFrom-Json
    $owned=Get-Process -Id $state.processId -ErrorAction Stop
    if($state.serial -ne $serial -or $owned.StartTime.ToUniversalTime().ToString('o') -ne ([datetimeoffset]$state.started).UtcDateTime.ToString('o')){throw 'Project emulator ownership mismatch'}
    if((Invoke-NaturalPreflightAdb @('emu','avd','name')).Split("`n")[0].Trim() -ne "Intime_Test_API$TestApi"){throw 'Dedicated AVD name mismatch'}
    if((Invoke-NaturalPreflightAdb @('shell','getprop','ro.build.version.sdk')).Trim() -ne "$TestApi"){throw 'Device API mismatch'}
    # Сохраняем полные снимки трёх служб и списка датчиков для независимой сверки.
    # Не читаем приватные данные приложения и пользовательские базы.
    foreach($service in @('deviceidle','power','alarm','battery','sensorservice')) {
        $dump=Invoke-NaturalPreflightAdb @('shell','dumpsys',$service)
        [IO.File]::WriteAllText((Join-Path $run "$service.log"),$dump,[Text.UTF8Encoding]::new($true))
        if($service -eq 'deviceidle'){$idle=$dump}
        if($service -eq 'battery' -and $dump -match 'UPDATES STOPPED'){throw 'Existing battery simulation; preflight refused'}
    }
    $deep=(Invoke-NaturalPreflightAdb @('shell','cmd','deviceidle','enabled','deep')).Trim()
    $summary.capability=Get-NaturalDozeCapability $idle $deep
    $summary.status=$summary.capability.status
    $summary.buildFingerprint=(Invoke-NaturalPreflightAdb @('shell','getprop','ro.build.fingerprint')).Trim()
    $summary.cleanup=$true # Диагностика не вносила изменений в питание/Doze.
}catch{$summary.message=$_.Exception.Message}
finally{$summary|ConvertTo-Json -Depth 7|Set-Content (Join-Path $run 'summary.json') -Encoding UTF8}
Write-Output "$($summary.status). Reports: $run"
if($summary.status -eq 'INFRASTRUCTURE_ERROR'){exit 2}
# Недоступная среда даёт отдельный ненулевой код, а не успешный тест приложения.
if($summary.status -eq 'NATURAL_DEEP_UNAVAILABLE'){exit 3}
