<#
Наблюдает штатный light idle на выделенных AVD31/33 после выключения экрана
и виртуальной зарядки через emulator console. Не вызывает force-idle/step,
не меняет таймеры/настройки Doze, не устанавливает release APK и не запускает
receiver/worker. Подтверждение входа среды не доказывает доставку приложения.
После любого исхода восстанавливает виртуальное питание/status и экран.
#>
[CmdletBinding()]
param([ValidateSet(31,33)][int]$TestApi=31,[ValidateRange(6,30)][int]$WaitMinutes=20)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$run=Join-Path $root ('.test-tools/runs/'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'-natural-light-idle')
New-Item -ItemType Directory -Path $run | Out-Null
$adb=Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
$serial=@{31='emulator-5586';33='emulator-5582'}[$TestApi]
$summary=@{status='INFRASTRUCTURE_ERROR';testApi=$TestApi;reports=$run;observations=@();naturalEntryValidated=$false;notificationDeliveryValidated=$false;forcedIdleUsed=$false;timersChanged=$false;virtualPowerChanged=$false;cleanup=$false}
$counter=0;$powerChanged=$false;$lock=$null

# Разбираем независимые снимки контроллера, PowerManager и батареи. Только
# согласованные IDLE/light=true при выключенном экране без зарядки доказывают
# вход; maintenance, force-idle и UPDATES STOPPED не принимаются за него.
function Get-LightIdleObservation([string]$Idle,[string]$Power,[string]$Battery) {
    $fields=@{}
    foreach($name in @('mLightEnabled','mForceIdle','mScreenOn','mCharging')){
        $match=[regex]::Match($Idle,"(?m)^\s*$name=(true|false)(?:\s|$)")
        if(-not $match.Success){throw "Missing controller field: $name"}
        $fields[$name]=$match.Groups[1].Value -eq 'true'
    }
    $state=[regex]::Match($Idle,'\bmLightState=([A-Z_]+)\b')
    $deep=[regex]::Match($Idle,'\bmState=([A-Z_]+)\b')
    $light=[regex]::Match($Power,'(?m)^\s*mLightDeviceIdleMode=(true|false)\s*$')
    $powered=[regex]::Match($Power,'(?m)^\s*mIsPowered=(true|false)\s*$')
    $wake=[regex]::Match($Power,'(?m)^\s*mWakefulness=(\w+)\s*$')
    if(-not $state.Success -or -not $deep.Success -or -not $light.Success -or -not $powered.Success -or -not $wake.Success){throw 'Unknown power/idle format'}
    if($Battery -match 'UPDATES STOPPED' -or $fields.mForceIdle){throw 'Existing battery simulation or forced idle'}
    $batteryPowered=$false
    foreach($source in @('AC','USB','Wireless')){
        $match=[regex]::Match($Battery,"(?m)^\s*$source powered: (true|false)\s*$")
        if(-not $match.Success){throw 'Unknown battery power format'}
        $batteryPowered=$batteryPowered -or $match.Groups[1].Value -eq 'true'
    }
    $ready=$fields.mLightEnabled -and -not $fields.mScreenOn -and -not $fields.mCharging -and -not $batteryPowered -and $powered.Groups[1].Value -eq 'false' -and $wake.Groups[1].Value -in @('Asleep','Dozing')
    return @{lightState=$state.Groups[1].Value;deepState=$deep.Groups[1].Value;lightEnabled=$fields.mLightEnabled;screenOn=$fields.mScreenOn;charging=$fields.mCharging;forceIdle=$false;powerLightIdle=($light.Groups[1].Value -eq 'true');wakefulness=$wake.Groups[1].Value;preparationReady=$ready;entryObserved=($ready -and $state.Groups[1].Value -eq 'IDLE' -and $light.Groups[1].Value -eq 'true')}
}
function Invoke-LightAdb([string[]]$Arguments) {
    $script:counter++
    $out=Join-Path $run "adb-$script:counter.stdout.log";$err=Join-Path $run "adb-$script:counter.stderr.log"
    $process=Start-Process $adb -ArgumentList (@('-s',$serial)+$Arguments) -WindowStyle Hidden -PassThru -RedirectStandardOutput $out -RedirectStandardError $err
    $null=$process.Handle
    if(-not $process.WaitForExit(30000)){Stop-Process -Id $process.Id;throw 'Own adb timed out'}
    if($process.ExitCode -ne 0){throw "adb failed: $([IO.File]::ReadAllText($err))"}
    $value=[IO.File]::ReadAllText($out)
    # Обычные снимки сохраняются вызывающей функцией под понятными именами.
    Remove-Item -LiteralPath $out,$err
    return $value
}
function Read-LightSnapshot([string]$Prefix) {
    $idle=Invoke-LightAdb @('shell','dumpsys','deviceidle')
    $power=Invoke-LightAdb @('shell','dumpsys','power')
    $battery=Invoke-LightAdb @('shell','dumpsys','battery')
    foreach($item in @(@{name='idle';text=$idle},@{name='power';text=$power},@{name='battery';text=$battery})){
        Set-Content (Join-Path $run "$Prefix-$($item.name).log") $item.text -Encoding UTF8
    }
    return Get-LightIdleObservation $idle $power $battery
}
try {
    $lock=[IO.File]::Open((Join-Path $root '.test-tools/run.lock'),'OpenOrCreate','ReadWrite','None')
    $owner=Get-Content (Join-Path $root '.test-tools/emulator.json') -Raw|ConvertFrom-Json
    $process=Get-Process -Id $owner.processId -ErrorAction Stop
    if($owner.serial -ne $serial -or $process.StartTime.ToUniversalTime().ToString('o') -ne $owner.started){throw 'Emulator ownership mismatch'}
    if((Invoke-LightAdb @('emu','avd','name')).Split("`n")[0].Trim() -ne "Intime_Test_API$TestApi" -or (Invoke-LightAdb @('shell','getprop','ro.build.version.sdk')).Trim() -ne "$TestApi"){throw 'Dedicated API/AVD mismatch'}
    $before=Read-LightSnapshot 'before'
    if(-not $before.lightEnabled -or -not $before.screenOn -or $before.lightState -ne 'ACTIVE'){throw 'Light must be enabled and initially ACTIVE with screen on'}
    $battery=Get-Content (Join-Path $run 'before-battery.log') -Raw
    if($battery -match '(?:USB|Wireless|Dock) powered: true'){throw 'Unsupported initial power source'}
    $oldAc=([regex]::Match($battery,'AC powered: (true|false)')).Groups[1].Value
    $status=([regex]::Match($battery,'(?m)^\s*status: (\d+)')).Groups[1].Value
    $oldStatus=@{'1'='unknown';'2'='charging';'3'='discharging';'4'='not-charging';'5'='full'}[$status]
    if(-not $oldStatus){throw 'Unknown original battery status'}
    $summary.originalPower=@{ac=$oldAc;status=$oldStatus}
    $summary.before=$before
    Set-Content (Join-Path $run 'alarm-before.log') (Invoke-LightAdb @('shell','dumpsys','alarm')) -Encoding UTF8
    $powerChanged=$true;$summary.virtualPowerChanged=$true
    Invoke-LightAdb @('emu','power','ac','off')|Out-Null
    Invoke-LightAdb @('emu','power','status','discharging')|Out-Null
    Invoke-LightAdb @('shell','input','keyevent','223')|Out-Null
    $timer=[Diagnostics.Stopwatch]::StartNew();$lastTick=0.0;$consecutive=0;$index=0
    do {
        $index++
        $now=$timer.Elapsed.TotalSeconds
        if($index -gt 1 -and $now-$lastTick -gt 90){throw 'Observation interrupted by host pause'}
        $lastTick=$now
        $observation=Read-LightSnapshot "sample-$index"
        $observation.elapsedSeconds=[Math]::Round($timer.Elapsed.TotalSeconds,3)
        $summary.observations+=,$observation
        if(-not $observation.preparationReady -and $index -gt 1){throw 'Screen/power preparation lost'}
        Write-Output ("Light observation {0:N1} min: {1}, PowerManager={2}" -f ($timer.Elapsed.TotalMinutes),$observation.lightState,$observation.powerLightIdle)
        if($observation.entryObserved){$consecutive++}else{$consecutive=0}
        if($consecutive -ge 2){
            $summary.naturalEntryValidated=$true;$summary.status='NATURAL_LIGHT_IDLE_OBSERVED'
            $summary.entry=$observation
            Set-Content (Join-Path $run 'alarm-at-entry.log') (Invoke-LightAdb @('shell','dumpsys','alarm')) -Encoding UTF8
            break
        }
        Start-Sleep -Seconds 30
    } while($timer.Elapsed.TotalMinutes -lt $WaitMinutes)
    if(-not $summary.naturalEntryValidated){$summary.status='INCONCLUSIVE';$summary.message='No two consecutive light IDLE snapshots before deadline'}
    $health=Invoke-LightAdb @('logcat','-d')
    Set-Content (Join-Path $run 'logcat.log') $health -Encoding UTF8
    if($health -match 'ANR in|FATAL EXCEPTION'){throw 'ANR/fatal during observation'}
} catch {$summary.status='INFRASTRUCTURE_ERROR';$summary.message=$_.Exception.Message}
finally {
    if($powerChanged){
        try {
            Invoke-LightAdb @('emu','power','ac',$(if($oldAc -eq 'true'){'on'}else{'off'}))|Out-Null
            Invoke-LightAdb @('emu','power','status',$oldStatus)|Out-Null
            Invoke-LightAdb @('shell','input','keyevent','224')|Out-Null
            Start-Sleep -Seconds 2
            $after=Read-LightSnapshot 'restored'
            $restoredBattery=Get-Content (Join-Path $run 'restored-battery.log') -Raw
            if(-not $after.screenOn -or $after.lightState -ne 'ACTIVE' -or $restoredBattery -notmatch "AC powered: $oldAc" -or $restoredBattery -notmatch "(?m)^\s*status: $status\s*$"){throw 'Power restoration not verified'}
            $summary.cleanup=$true;$summary.restored=$after
        } catch {$summary.cleanupError=$_.Exception.Message;$summary.status='INFRASTRUCTURE_ERROR'}
    }
    if($lock){$lock.Dispose()}
    $summary|ConvertTo-Json -Depth 7|Set-Content (Join-Path $run 'summary.json') -Encoding UTF8
}
Write-Output "$($summary.status). Reports: $run"
if($summary.status -ne 'NATURAL_LIGHT_IDLE_OBSERVED'){exit 1}
