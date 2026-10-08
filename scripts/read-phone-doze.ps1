<#
Согласованная диагностика личного телефона: только системные сведения и версия
InTime. Не является тестом естественного Doze или доставки уведомлений.
Не устанавливает APK, не меняет питание, отладку, appops, режимы или данные.
Перед запуском нужно явное согласие владельца на диагностику личного устройства.
#>
[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$Serial)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$adb=Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
# Принимаем явно выбранный USB serial. Не подключаемся к сети и не выбираем
# произвольное устройство при нескольких подключениях. Эмуляторы исключены.
if($Serial -notmatch '^[A-Za-z0-9_.-]+$' -or $Serial -match '^emulator-'){throw 'Explicit physical USB device serial required'}
$run=Join-Path $root ('.test-tools/runs/'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'-phone-doze-readonly')
New-Item -ItemType Directory -Path $run|Out-Null
$counter=0
$summary=@{status='INFRASTRUCTURE_ERROR';readOnly=$true;reports=$run;naturalEntryValidated=$false;notificationDeliveryValidated=$false;candidateApkIdentityValidated=$false}

# В журнал идут только ответы явно заданных команд чтения. Предел одного
# процесса — 30 секунд; при тайм-ауте останавливается только собственный adb,
# а не сервер или другие процессы. Настройки телефона не меняются при cleanup.
function Invoke-PhoneRead([string[]]$Arguments){
    $script:counter++
    $out=Join-Path $run "adb-$script:counter.stdout.log"
    $err=Join-Path $run "adb-$script:counter.stderr.log"
    $process=Start-Process $adb -ArgumentList (@('-s',$Serial)+$Arguments) -WindowStyle Hidden -PassThru -RedirectStandardOutput $out -RedirectStandardError $err
    $null=$process.Handle
    try{
        if(-not $process.WaitForExit(30000)){Stop-Process -Id $process.Id;throw 'Own adb read timed out'}
        if($process.ExitCode -ne 0){throw "adb read failed: $([IO.File]::ReadAllText($err))"}
        return [IO.File]::ReadAllText($out)
    }finally{$process.Dispose()}
}

try{
    if((Invoke-PhoneRead @('get-state')).Trim() -ne 'device'){throw 'Selected phone is not online/authorized'}
    if((Invoke-PhoneRead @('shell','getprop','ro.kernel.qemu')).Trim() -eq '1'){throw 'Emulator refused'}
    $summary.model=(Invoke-PhoneRead @('shell','getprop','ro.product.model')).Trim()
    $summary.androidVersion=(Invoke-PhoneRead @('shell','getprop','ro.build.version.release')).Trim()
    $summary.deviceApi=(Invoke-PhoneRead @('shell','getprop','ro.build.version.sdk')).Trim()
    # dumpsys package содержит метаданные установки, не содержимое базы задач.
    # Для отчёта оставляем лишь versionCode/versionName. Совпадение версии не
    # доказывает идентичность подписанного кандидата; это отдельная сверка.
    $packageDump=Invoke-PhoneRead @('shell','dumpsys','package','com.vpe_soft.intime.intime')
    $code=[regex]::Match($packageDump,'(?m)^\s*versionCode=(\d+)(?:\s|$)')
    $name=[regex]::Match($packageDump,'(?m)^\s*versionName=([^\r\n]+)')
    $summary.intimeInstalled=$code.Success -and $name.Success
    if($summary.intimeInstalled){$summary.versionCode=$code.Groups[1].Value;$summary.versionName=$name.Groups[1].Value.Trim()}
    # Снимки показывают исходные условия. USB-зарядку не отключаем программно;
    # наличие IDLE или уведомления здесь не засчитывается как естественная доставка.
    foreach($service in @('deviceidle','power','alarm','battery','sensorservice')){
        $dump=Invoke-PhoneRead @('shell','dumpsys',$service)
        [IO.File]::WriteAllText((Join-Path $run "$service.log"),$dump,[Text.UTF8Encoding]::new($true))
        if($service -eq 'deviceidle'){
            foreach($field in @('mLightEnabled','mDeepEnabled','mForceIdle','mUseMotionSensor','mScreenOn','mCharging')){
                $value=[regex]::Match($dump,"\b${field}=(true|false)\b")
                $summary[$field]=if($value.Success){$value.Groups[1].Value}else{'UNKNOWN'}
            }
            $motion=[regex]::Match($dump,'\bmMotionSensor=([^\r\n]+)')
            $summary.motionSensor=if($motion.Success){$motion.Groups[1].Value.Trim()}else{'UNKNOWN'}
            $state=[regex]::Match($dump,'(?m)^\s*mState=(\w+)(?:\s|$)')
            $summary.controllerState=if($state.Success){$state.Groups[1].Value}else{'UNKNOWN'}
        }
        if($service -eq 'battery'){$summary.existingBatterySimulation=$dump -match 'UPDATES STOPPED'}
    }
    $summary.status='READ_ONLY_DIAGNOSTICS_COLLECTED'
}catch{$summary.message=$_.Exception.Message}
finally{$summary|ConvertTo-Json -Depth 6|Set-Content (Join-Path $run 'summary.json') -Encoding UTF8}
Write-Output "$($summary.status). Reports: $run"
if($summary.status -eq 'INFRASTRUCTURE_ERROR'){exit 2}
