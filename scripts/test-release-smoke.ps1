<#
Проверяет уже собранный production release APK через UI на проектном AVD API 24/31/33/35/36.
API 24 пока поддерживает только RebootOnly (CRUD/JSON/reboot), без notification phases.
Перед запуском: scripts/test.ps1 -Mode device -TestApi <24, 31, 33, 35 или 36> -Offline -KeepEmulator.
Одинаковый TestApi нужно передать обоим скриптам; по умолчанию здесь используется 36.
APK должен быть подписан подтверждённым upload key; скрипт не публикует его в Play.
Используются только новые синтетические задачи. Если production-пакет уже установлен,
скрипт отказывается продолжать; данные существующей установки не очищаются.
После проверки удаляется только APK, установленный этим запуском. Эмулятор остаётся работать.
PeriodicOnly (API 31/33/35/36) проходит подготовку/boot/первый alarm и ждёт тихий
повтор после am kill фонового процесса. Обычный полный режим сохраняет 14 фаз;
periodic-режим содержит 10 фаз (11 при PeriodicRepeats=2) и не меняет batching/период/часы Android.
HistoricalJsonImport добавляет к полному режиму 15-ю фазу: SAF-импорт meta Play 25,
восстановление собственной задачи и её alarm после перезапуска процесса.
InexactOnly (API 31/33/35/36) содержит 11 фаз: подготовка, доставка fallback и ACK/restart.
GestureOnly (API33/35/36) содержит9 фаз: CRUD/JSON и отмена/завершение бокового
Back-жеста. Требует -EmulatorSoftwareNavigation при bootstrap, восстанавливает
navigation overlay/mode и developer flag; reboot/notification не выполняет.
LightIdleOnly (API31/33) содержит13 фаз: CRUD/JSON/reboot, будущий exact alarm,
самостоятельный light entry, доставка при screen off, notification ACK/restart.
LightIdleOnly + InexactOnly содержит14 фаз: запрет exact, положительное окно
fallback, light entry и доставка с отдельным учётом sampled maintenance, ACK/restart.
InexactOnly + DozeOnly содержит 12 фаз: дополнительно подтверждается deep IDLE.
DozeSequence добавляет подготовку второй задачи и её доставку: 14 фаз.
DozeQuota (API 31/33) добавляет подтверждённую отсрочку по квоте: 15 фаз.
DozeQuotaSeven (API 35/36) проверяет восемь доставок при квоте 7/час: 21 фаза.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$ApkPath,
    # Выбор ограничен проектными AVD; произвольный serial принимать небезопасно.
    [ValidateSet(24, 31, 33, 35, 36)][int]$TestApi = 36,
    # Точечный режим проходит подготовку/JSON и повторяет reboot без минутных уведомлений.
    [switch]$RebootOnly,
    # Только CRUD/JSON и отмена/завершение реального бокового Back-жеста в release.
    # Требует экранную навигацию проектного AVD; reboot/notification не выполняются.
    [switch]$GestureOnly,
    # Штатный light idle и доставка после virtual unplug/screen off.
    # Только31/33, exact либо InexactOnly; без forced idle/step/смены таймеров.
    [switch]$LightIdleOnly,
    # Дополнительный SAF-импорт с исторической meta Play 25 и текущей собственной
    # задачей. Срок остаётся будущим; исходная sharedTest-фикстура не изменяется.
    [switch]$HistoricalJsonImport,
    # Только доставка в deep idle и ACK после подготовки/reboot; без фаз запрета.
    [switch]$DozeOnly,
    # На API 31/33/35/36 запрещаем exact своей установке и проверяем inexact delivery/ACK.
    [switch]$InexactOnly,
    # Две заранее созданные задачи доставляются без выхода из IDLE между ними.
    [switch]$DozeSequence,
    # Подготовка настоящего idle-until и проверка квоты 1/window на API 31/33.
    # Шаги maintenance разрешены только до ожидания первой доставки.
    [switch]$DozeQuota,
    # Восемь задач импортируются штатным SAF до IDLE. Восьмая ждёт освобождения
    # окна после семи реальных доставок, без ускорения часов/квот или ручного alarm.
    [switch]$DozeQuotaSeven,
    # После реального первого alarm ждём тихого повторного worker в release/R8.
    # Никакого ручного job run, ускорения часов/периода или изменения batching.
    [switch]$PeriodicOnly,
    # Общий срок ожидания всех повторов, а не отдельный срок на каждый повтор.
    [ValidateRange(16, 75)][int]$PeriodicWaitMinutes = 65,
    # Два повтора проверяют также следующий естественный период после первого worker.
    [ValidateRange(1, 2)][int]$PeriodicRepeats = 1,
    [ValidateRange(1, 10)][int]$RebootRepeats = 1,
    # Ноль позволяет проверить гипотезу о задержке записи stopped после force-stop.
    [ValidateRange(0, 60)][int]$RebootSettleSeconds = 15,
    # Код проверяемого APK задаётся явно для будущих кандидатов. Значение 25
    # сохраняет воспроизводимость старых артефактов; это не разрешение новой
    # production-сборки или публикации с уже занятым кодом.
    [ValidateRange(25, 2100000000)][int]$ExpectedVersionCode = 25,
    # Имя проверяется и в APK, и на экране настроек. Старое имя по умолчанию
    # относится к архивным кандидатам; для новой сборки передаём её точное имя.
    [ValidatePattern('^[0-9]+\.[0-9]+\.[0-9]+(?:-[A-Za-z0-9.]+)?$')][string]$ExpectedVersionName = '2.0.0-rc1'
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$work = Join-Path $root '.test-tools'
$run = Join-Path $work ('runs\' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-release-smoke')
New-Item -ItemType Directory -Path $run -Force | Out-Null
$sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$adb = Join-Path $sdk 'platform-tools\adb.exe'
# Каждому разрешённому API соответствует только закреплённый проектный порт.
$serial = switch ($TestApi) { 24 { 'emulator-5588' }; 31 { 'emulator-5586' }; 33 { 'emulator-5582' }; 35 { 'emulator-5580' }; 36 { 'emulator-5584' } }
$package = 'com.vpe_soft.intime.intime'
$installedHere = $false
$probePushed = $false
$dozeChanged = $false
$dozeDeepWasEnabled = $null
$backupDevicePath = $null
$oldJavaHome = $env:JAVA_HOME
$lock = $null
$commandNumber = 0
$summary = @{ status = 'RUNNING'; passedPhases = @(); reports = $run; serial = $serial; testApi = $TestApi; rebootOnly = [bool]$RebootOnly; rebootResults = @(); rebootSettleSeconds = $RebootSettleSeconds; expectedVersionCode = $ExpectedVersionCode; expectedVersionName = $ExpectedVersionName }
$summary.gestureOnly = [bool]$GestureOnly
$summary.lightIdleOnly=[bool]$LightIdleOnly
$lightPowerChanged=$false;$lightOriginalPower=$null
if($LightIdleOnly -and ($TestApi -notin @(31,33) -or $GestureOnly -or $RebootOnly -or $HistoricalJsonImport -or $DozeOnly -or $DozeSequence -or $DozeQuota -or $DozeQuotaSeven -or $PeriodicOnly -or $RebootRepeats -ne 1 -or $PeriodicRepeats -ne 1)){throw 'LightIdleOnly requires API31/33, optionally InexactOnly, without other modes/repeats'}
if($LightIdleOnly){
    # Загружаем только проверяемую функцию parser, без выполнения observer.
    $lightTokens=$null;$lightErrors=$null
    $lightAst=[System.Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'test-natural-light-idle.ps1'),[ref]$lightTokens,[ref]$lightErrors)
    if($lightErrors.Count){throw 'Light observer parser failed'}
    $lightFunction=$lightAst.Find({param($node) $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Get-LightIdleObservation'},$true)
    Invoke-Expression $lightFunction.Extent.Text
}
if ($GestureOnly -and ($TestApi -notin @(33,35,36) -or $RebootOnly -or $HistoricalJsonImport -or $DozeOnly -or $InexactOnly -or $DozeSequence -or $DozeQuota -or $DozeQuotaSeven -or $PeriodicOnly -or $RebootRepeats -ne 1 -or $PeriodicRepeats -ne 1)) { throw 'GestureOnly requires API33/35/36 without other modes/repeats' }
if (-not $RebootOnly -and $RebootRepeats -ne 1) { throw 'RebootRepeats requires RebootOnly' }
if ($RebootOnly -and $DozeOnly) { throw 'RebootOnly and DozeOnly are mutually exclusive' }
$summary.dozeOnly = [bool]$DozeOnly
$summary.inexactOnly = [bool]$InexactOnly
$summary.dozeSequence = [bool]$DozeSequence
$summary.dozeQuota = [bool]$DozeQuota
$summary.dozeQuotaSeven = [bool]$DozeQuotaSeven
if ($DozeQuotaSeven -and ($DozeQuota -or -not $DozeSequence -or -not $InexactOnly -or -not $DozeOnly -or $TestApi -notin @(35,36))) { throw 'DozeQuotaSeven requires API 35/36 and InexactOnly + DozeOnly + DozeSequence, without DozeQuota' }
if ($DozeQuota -and (-not $DozeSequence -or -not $InexactOnly -or -not $DozeOnly -or $TestApi -notin @(31,33))) { throw 'DozeQuota requires API 31/33 and InexactOnly + DozeOnly + DozeSequence' }
if ($DozeSequence -and (-not $InexactOnly -or -not $DozeOnly)) { throw 'DozeSequence requires InexactOnly and DozeOnly' }
if ($InexactOnly -and ($RebootOnly -or $PeriodicOnly -or $TestApi -notin @(31, 33, 35, 36))) { throw 'InexactOnly requires API 31/33/35/36 and can only be combined with DozeOnly or LightIdleOnly.' }
$summary.periodicOnly = [bool]$PeriodicOnly
$summary.historicalJsonImport = [bool]$HistoricalJsonImport
if ($HistoricalJsonImport -and ($PeriodicOnly -or $InexactOnly -or $DozeOnly)) { throw 'HistoricalJsonImport requires full smoke or RebootOnly' }
if (-not $PeriodicOnly -and $PeriodicRepeats -ne 1) { throw 'PeriodicRepeats requires PeriodicOnly' }
$summary.periodicRepeats = $PeriodicRepeats
if ($PeriodicOnly -and ($RebootOnly -or $DozeOnly -or $TestApi -notin @(31, 33, 35, 36))) { throw 'PeriodicOnly requires API 31/33/35/36 and cannot be combined with other modes.' }
# API 24 не имеет notification channels и новых Settings intents. Пока эти фазы
# не адаптированы, отказываем до установки APK вместо ложного полного smoke.
if ($TestApi -eq 24 -and -not $RebootOnly) { throw 'API 24 currently requires RebootOnly; notification phases are not supported yet.' }

# SDK-компиляторы тоже могут писать предупреждения в stderr с exit 0.
# Сохраняем оба потока и определяем результат по коду завершения.
function Invoke-ReleaseTool([string]$Executable, [string[]]$Arguments, [string]$LogName) {
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = & $Executable @Arguments 2>&1
        $code = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previousPreference }
    $text = ($output | ForEach-Object { "$_" }) -join "`n"
    Set-Content (Join-Path $run $LogName) $text -Encoding UTF8
    if ($code -ne 0) { throw "SDK tool failed ($code): $LogName" }
    return $text
}

# Каждый вызов адресован одному AVD, сохраняет вывод и проверяет код завершения.
function Invoke-ReleaseAdb([string[]]$Arguments) {
    $script:commandNumber++
    # PowerShell 5.1 превращает stderr в ErrorRecord даже при exit 0 (adb pull
    # пишет туда обычный прогресс). Временно собираем оба потока, затем оцениваем
    # именно exit code; ненулевой результат по-прежнему останавливает фазу.
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = & $adb -s $serial @Arguments 2>&1
        $code = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previousPreference }
    $text = ($output | ForEach-Object { "$_" }) -join "`n"
    Set-Content (Join-Path $run ("adb-{0:D3}.log" -f $script:commandNumber)) $text -Encoding UTF8
    if ($code -ne 0) { throw "adb failed ($code): $($Arguments -join ' ')" }
    return $text
}

# UIAutomator читает реальные элементы установленного APK, не обращаясь к его Java-классам.
function Read-ReleaseUi {
    # Android 16 вынес legacy android.test в отдельные framework JAR; явно
    # добавляем их в classpath shell-runner, не в APK приложения.
    $probe = Invoke-ReleaseAdb @('shell', 'uiautomator', 'runtest', '/system/framework/android.test.runner.jar', '/system/framework/android.test.base.jar', '/data/local/tmp/intime-release-probe.jar', '-c', 'intime.smoke.ReleaseHierarchyProbe#testDumpActiveWindow')
    if ($probe -notmatch 'OK \(1 test\)' -or $probe -notmatch 'INTIME_FRESH_HIERARCHY_READY' -or $probe -match 'FAILURES!!!|INSTRUMENTATION_FAILED|Test run aborted|shortMsg=') { throw 'Fresh UI hierarchy probe failed' }
    $path = Join-Path $run ("ui-{0:D3}.xml" -f $script:commandNumber)
    Invoke-ReleaseAdb @('pull', '/data/local/tmp/intime-release-ui.xml', $path) | Out-Null
    return [xml](Get-Content $path -Raw -Encoding UTF8)
}
# После Home команда раскрытия шторки может потеряться в переходе. Подтверждаем
# реальный контейнер уведомлений по свежему XML, иначе отсутствие описания ничего
# не доказывает. Повторяем раскрытие ограниченно, только на проектном AVD.
function Read-ReleaseShade {
    $limit = (Get-Date).AddSeconds(20)
    do {
        Invoke-ReleaseAdb @('shell', 'cmd', 'statusbar', 'expand-notifications') | Out-Null
        Start-Sleep -Milliseconds 500
        $ui = Read-ReleaseUi
        if ($ui.SelectSingleNode("//node[@resource-id='com.android.systemui:id/notification_stack_scroller']")) { return $ui }
    } while ((Get-Date) -lt $limit)
    throw 'Notification shade did not open'
}

function Wait-ReleaseNode([string]$XPath) {
    $deadline = (Get-Date).AddSeconds(30)
    do {
        $ui = Read-ReleaseUi
        $node = $ui.SelectSingleNode($XPath)
        if ($node) { return $node }
        Start-Sleep -Milliseconds 250
    } while ((Get-Date) -lt $deadline)
    throw "UI element not found: $XPath"
}
function Click-ReleaseNode([string]$XPath) {
    # Probe не ждёт idle из-за часов UI. Даём оконной анимации завершиться до
    # получения координат, чтобы tap не потерялся в переходе между Activity.
    Start-Sleep -Milliseconds 500
    $node = Wait-ReleaseNode $XPath
    if ($node.GetAttribute('enabled') -ne 'true') { throw "Disabled UI element: $XPath" }
    $bounds = [regex]::Match($node.GetAttribute('bounds'), '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$')
    if (-not $bounds.Success) { throw 'Invalid UI bounds' }
    $x = [int](([int]$bounds.Groups[1].Value + [int]$bounds.Groups[3].Value) / 2)
    $y = [int](([int]$bounds.Groups[2].Value + [int]$bounds.Groups[4].Value) / 2)
    Invoke-ReleaseAdb @('shell', 'input', 'tap', "$x", "$y") | Out-Null
}
function Click-ReleaseId([string]$Id) { Click-ReleaseNode "//node[@resource-id='${package}:id/$Id']" }
# Переход из деталей в Edit завершается только появлением поля формы.
# Если tap потерялся в оконной анимации, повторяем лишь пока видна кнопка деталей;
# не нажимаем по старым координатам и не принимаем ожидание за успешный переход.
function Open-ReleaseEdit {
    $limit = (Get-Date).AddSeconds(30)
    do {
        $ui = Read-ReleaseUi
        if ($ui.SelectSingleNode("//node[@resource-id='${package}:id/edit_task_description']")) { return }
        if ($ui.SelectSingleNode("//node[@resource-id='${package}:id/btnEditTask']")) { Click-ReleaseId 'btnEditTask' }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $limit)
    throw 'Edit form did not open'
}
# Во время анимации Activity tap может потеряться. Перед вводом подтверждаем
# фокус именно поля описания по свежему XML; ограниченные повторные taps безопасны.
function Focus-ReleaseDescription {
    $limit = (Get-Date).AddSeconds(20)
    do {
        Click-ReleaseId 'edit_task_description'
        $ui = Read-ReleaseUi
        if ($ui.SelectSingleNode("//node[@resource-id='${package}:id/edit_task_description' and @focused='true']")) { return }
        Start-Sleep -Milliseconds 250
    } while ((Get-Date) -lt $limit)
    throw 'Description input did not receive focus'
}
# На API 33 аппаратная клавиатура AVD может оставлять IME скрытой после ввода.
# Back в этом состоянии закроет форму. Сначала сверяем показ IME, затем ещё раз
# проверяем поле формы; системный Back для навигации в других фазах не заменяем.
function Close-ReleaseKeyboard {
    $ime = Invoke-ReleaseAdb @('shell', 'dumpsys', 'input_method')
    if ($ime -match '\bmInputShown=true\b|\bmIsInputViewShown=true\b') {
        Invoke-ReleaseAdb @('shell', 'input', 'keyevent', '4') | Out-Null
    }
    Wait-ReleaseNode "//node[@resource-id='${package}:id/edit_task_description']" | Out-Null
}
function Assert-ReleaseText([string]$Text) { Wait-ReleaseNode "//node[@text='$Text']" | Out-Null }
function Launch-Release {
    # После failed-фазы шторка может остаться раскрытой и перекрыть Activity.
    # Сбрасываем только этот UI-слой проектного AVD перед проверкой списка.
    Invoke-ReleaseAdb @('shell', 'cmd', 'statusbar', 'collapse') | Out-Null
    Invoke-ReleaseAdb @('shell', 'am', 'start', '-W', '-n', "$package/com.vpe_soft.intime.intime.activity.MainActivityV2") | Out-Null
    Wait-ReleaseNode "//node[@resource-id='${package}:id/fab_add_task']" | Out-Null
}
function Read-ReleaseAlarm {
    $dump = Invoke-ReleaseAdb @('shell', 'dumpsys', 'alarm')
    # Только действующий RTC_WAKEUP, не историческая запись отменённого будильника.
    # Когда applicationId совпадает с namespace, Android сокращает компонент
    # до «/.receiver.AlarmReceiver»; принимаем также полное имя того же receiver.
    $match = [regex]::Match($dump, '(?m)^\s*RTC_WAKEUP #\d+: Alarm\{\S+ type 0 origWhen (?<deadline>\d+) [^\r\n]* com\.vpe_soft\.intime\.intime\}\r?\n\s+tag=\*walarm\*:com\.vpe_soft\.intime\.intime/(?:com\.vpe_soft\.intime\.intime)?\.receiver\.AlarmReceiver\s*$')
    if ($match.Success) { return [long]$match.Groups['deadline'].Value }
    return [long]0
}
function Wait-ReleaseAlarm([long]$After = 0) {
    $limit = (Get-Date).AddSeconds(20)
    do {
        $deadline = Read-ReleaseAlarm
        if ($deadline -gt $After) { return $deadline }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $limit)
    throw 'Expected active release alarm was not scheduled'
}
function Complete-ReleasePhase([string]$Name) {
    $summary.passedPhases += $Name
    Write-Output "PASSED: $Name"
}

function Read-ReleaseLightSnapshot([string]$Prefix) {
    $idle=Invoke-ReleaseTool $adb @('-s',$serial,'shell','dumpsys','deviceidle') "$Prefix-idle.log"
    $power=Invoke-ReleaseTool $adb @('-s',$serial,'shell','dumpsys','power') "$Prefix-power.log"
    $battery=Invoke-ReleaseTool $adb @('-s',$serial,'shell','dumpsys','battery') "$Prefix-battery.log"
    return Get-LightIdleObservation $idle $power $battery
}
# После любого исхода возвращаем только исходное emulator-console AC/status и
# экран. Не используем unforce/battery reset: forced idle/simulation не вводились.
function Restore-ReleaseLightPower {
    if(-not $script:lightPowerChanged){return}
    Invoke-ReleaseAdb @('emu','power','ac',$(if($script:lightOriginalPower.ac -eq 'true'){'on'}else{'off'}))|Out-Null
    Invoke-ReleaseAdb @('emu','power','status',$script:lightOriginalPower.status)|Out-Null
    Invoke-ReleaseAdb @('shell','input','keyevent','224')|Out-Null
    $limit=(Get-Date).AddSeconds(20)
    do {
        $restored=Read-ReleaseLightSnapshot 'light-restored'
        if($restored.screenOn -and $restored.lightState -eq 'ACTIVE'){break}
        Start-Sleep -Milliseconds 500
    } while((Get-Date) -lt $limit)
    $battery=Get-Content (Join-Path $run 'light-restored-battery.log') -Raw
    if(-not $restored.screenOn -or $restored.lightState -ne 'ACTIVE' -or $battery -notmatch "AC powered: $($script:lightOriginalPower.ac)" -or $battery -notmatch "(?m)^\s*status: $($script:lightOriginalPower.statusCode)\s*$"){throw 'Light power restoration failed'}
    $before=Get-Content (Join-Path $run 'light-before-idle.log') -Raw
    $after=Get-Content (Join-Path $run 'light-restored-idle.log') -Raw
    $beforeConstants=@([regex]::Matches($before,'(?m)^\s*light_[a-z_]+=[^\r\n]+')|ForEach-Object {$_.Value.Trim()}|Sort-Object)
    $afterConstants=@([regex]::Matches($after,'(?m)^\s*light_[a-z_]+=[^\r\n]+')|ForEach-Object {$_.Value.Trim()}|Sort-Object)
    if($beforeConstants.Count -eq 0 -or ($beforeConstants -join "`n") -ne ($afterConstants -join "`n")){throw 'Light constants changed'}
    $summary.lightPowerRestored=$true;$summary.lightConstantsUnchanged=$true
    $script:lightPowerChanged=$false
}

# Классифицируем только согласованные снимки после уже подтверждённого входа.
# Maintenance допускается явно только для fallback и не называется IDLE.
# Иные состояния/несогласованность не принимаются за успешное продолжение.
function Get-ReleaseLightDeliveryState($Snapshot,[bool]$AllowMaintenance) {
    if(-not $Snapshot.preparationReady -or $Snapshot.forceIdle){return 'INVALID'}
    if($Snapshot.lightState -eq 'IDLE' -and $Snapshot.powerLightIdle){return 'IDLE'}
    if($AllowMaintenance -and $Snapshot.lightState -eq 'IDLE_MAINTENANCE' -and -not $Snapshot.powerLightIdle){return 'MAINTENANCE'}
    return 'INVALID'
}

# Одна синтетическая задача имеет будущий alarm с периодом4/8мин.
# Перед её сроком требуем два штатных согласованных light IDLE-снимка; после
# этого опрос не открывает UI и не запускает receiver/worker. При обнаружении
# реального уведомления требуем IDLE (fallback также допускает согласованное
# maintenance), ACK action, when>=deadline,
# журнал AlarmReceiver и потребление собственного alarm. Это не deep/квота,
# не непрерывная трасса между опросами и не измерение физического звука.
function Assert-ReleaseLightDelivery([string]$Description,[long]$Deadline,[string]$Since) {
    $before=Read-ReleaseLightSnapshot 'light-before'
    if(-not $before.lightEnabled -or -not $before.screenOn -or $before.lightState -ne 'ACTIVE'){throw 'Initial light ACTIVE/screen on missing'}
    $battery=Get-Content (Join-Path $run 'light-before-battery.log') -Raw
    if($battery -match '(?:USB|Wireless|Dock) powered: true'){throw 'Unsupported original power source'}
    $ac=([regex]::Match($battery,'AC powered: (true|false)')).Groups[1].Value
    $code=([regex]::Match($battery,'(?m)^\s*status: (\d+)')).Groups[1].Value
    $status=@{'1'='unknown';'2'='charging';'3'='discharging';'4'='not-charging';'5'='full'}[$code]
    if(-not $status){throw 'Unknown original battery status'}
    $script:lightOriginalPower=@{ac=$ac;status=$status;statusCode=$code}
    $summary.lightOriginalPower=$script:lightOriginalPower
    $ops=Invoke-ReleaseAdb @('shell','appops','get',$package,'SCHEDULE_EXACT_ALARM')
    Set-Content (Join-Path $run 'light-exact-appops.log') $ops -Encoding UTF8
    $expectedOp=if($InexactOnly){'deny'}else{'allow'}
    if($ops -notmatch "SCHEDULE_EXACT_ALARM: $expectedOp"){throw 'Light alarm permission mode missing'}
    if((Read-ReleaseAlarm) -ne $Deadline){throw 'Own future light alarm missing'}
    $alarms=Invoke-ReleaseAdb @('shell','dumpsys','alarm')
    Set-Content (Join-Path $run 'light-alarm-before.log') $alarms -Encoding UTF8
    $own=[regex]::Match($alarms,'(?m)^\s*RTC_WAKEUP #\d+: Alarm\{\S+ type 0 origWhen (?<deadline>\d+) [^\r\n]* com\.vpe_soft\.intime\.intime\}\r?\n\s+tag=\*walarm\*:com\.vpe_soft\.intime\.intime/(?:com\.vpe_soft\.intime\.intime)?\.receiver\.AlarmReceiver\r?\n\s+type=RTC_WAKEUP[^\r\n]*?window=(?<window>\S+)')
    if(-not $own.Success -or [long]$own.Groups['deadline'].Value -ne $Deadline){throw 'Own light alarm missing'}
    $window=$own.Groups['window'].Value
    if(($InexactOnly -and $window -notmatch '[1-9]') -or (-not $InexactOnly -and $window -ne '0')){throw 'Own light alarm window mismatch'}
    $summary.lightAlarmWindow=$window
    $summary.lightInexactFallback=[bool]$InexactOnly
    $summary.lightMaintenanceObserved=$false
    # Сохраняем fallback log до ожидания: раннюю строку может вытеснить logcat.
    $beforeLog=Invoke-ReleaseTool $adb @('-s',$serial,'logcat','-d','-T',$Since) 'light-scheduling-logcat.log'
    if($InexactOnly -and $beforeLog -notmatch 'scheduleAlarm: exact alarms are not available, using inexact alarm'){throw 'Light fallback scheduling log missing'}
    $summary.lightForcedIdleUsed=$false;$summary.lightTimersChanged=$false
    $summary.lightObservations=@();$summary.lightEntryValidated=$false
    $script:lightPowerChanged=$true
    Invoke-ReleaseAdb @('emu','power','ac','off')|Out-Null
    Invoke-ReleaseAdb @('emu','power','status','discharging')|Out-Null
    Invoke-ReleaseAdb @('shell','input','keyevent','223')|Out-Null
    $timer=[Diagnostics.Stopwatch]::StartNew();$lastTick=0.0;$index=0;$consecutive=0;$delivered=$false
    do {
        $index++;$tick=$timer.Elapsed.TotalSeconds
        if($index -gt 1 -and $tick-$lastTick -gt 90){throw 'Light observation interrupted by host polling gap'}
        $lastTick=$tick
        $snapshot=Read-ReleaseLightSnapshot "light-sample-$index"
        $snapshot.elapsedSeconds=[Math]::Round($timer.Elapsed.TotalSeconds,3)
        $snapshot.guestNowMs=[long](Invoke-ReleaseTool $adb @('-s',$serial,'shell','date','+%s') 'light-guest-now.log').Trim()*1000
        $summary.lightObservations+=,$snapshot
        if($index -gt 1 -and -not $snapshot.preparationReady){throw 'Light screen/power preparation lost'}
        $notifications=Invoke-ReleaseTool $adb @('-s',$serial,'shell','dumpsys','notification','--noredact') 'light-notifications-poll.log'
        $record=[regex]::Match($notifications,'(?ms)^\s*NotificationRecord\([^\r\n]*pkg=com\.vpe_soft\.intime\.intime [^\r\n]*\r?\n(?:(?!^\s*NotificationRecord\().)*')
        $when=[regex]::Match($record.Value,'(?m)^\s*when=(\d+)(?:/|\r?$)')
        $delivered=$record.Success -and $record.Value.Contains("android.text=String ($Description)") -and $record.Value -match 'actions=1' -and $when.Success -and [long]$when.Groups[1].Value -ge $Deadline
        if(-not $summary.lightEntryValidated){
            if($snapshot.entryObserved -and $snapshot.guestNowMs -lt $Deadline -and -not $delivered){$consecutive++}else{$consecutive=0}
            if($consecutive -ge 2){
                $summary.lightEntryValidated=$true;$summary.lightEntry=$snapshot
                Complete-ReleasePhase 'release-natural-light-entry'
            } elseif($snapshot.guestNowMs -ge $Deadline -or $delivered){throw 'Natural light entry not confirmed before task deadline'}
        }
        if($summary.lightEntryValidated){
            $deliveryState=Get-ReleaseLightDeliveryState $snapshot ([bool]$InexactOnly)
            if($deliveryState -eq 'INVALID'){throw 'Confirmed light observation lost valid IDLE/maintenance state'}
            if($deliveryState -eq 'MAINTENANCE'){$summary.lightMaintenanceObserved=$true}
        }
        Write-Output ("Light delivery wait {0:N1} min: {1}, delivered={2}" -f $timer.Elapsed.TotalMinutes,$snapshot.lightState,$delivered)
        if(-not $delivered){Start-Sleep -Seconds 15}
    } while(-not $delivered -and $timer.Elapsed.TotalMinutes -lt 18)
    if(-not $delivered){throw 'Natural light alarm delivery timed out'}
    $final=Read-ReleaseLightSnapshot 'light-at-delivery'
    $finalState=Get-ReleaseLightDeliveryState $final ([bool]$InexactOnly)
    if($finalState -eq 'INVALID'){throw 'Light IDLE/maintenance not confirmed at delivery'}
    if($finalState -eq 'MAINTENANCE'){$summary.lightMaintenanceObserved=$true}
    $log=Invoke-ReleaseAdb @('logcat','-d','-T',$Since)
    Set-Content (Join-Path $run 'light-delivery-logcat.log') $log -Encoding UTF8
    if($log -notmatch 'AlarmReceiver.*handleAlarm: trying to post notification'){throw 'Actual light AlarmReceiver log missing'}
    if($InexactOnly -and ($beforeLog+"`n"+$log) -notmatch 'scheduleAlarm: exact alarms are not available, using inexact alarm'){throw 'Light fallback evidence missing'}
    if((Read-ReleaseAlarm) -ne 0){throw 'Own light alarm not consumed'}
    $summary.lightDeliveryValidated=$true;$summary.lightStateAtDelivery=$final
    $summary.lightDeliveryState=$finalState
    $summary.lightNotificationWhen=[long]$when.Groups[1].Value
    $summary.lightNotificationDelaySeconds=($summary.lightNotificationWhen-$Deadline)/1000.0
    Complete-ReleasePhase 'release-natural-light-alarm-delivery'
    # ACK через шторку является пользовательским взаимодействием: сначала
    # завершена проверка доставки при screen off, затем возвращаем питание/экран.
    Restore-ReleaseLightPower
}

# Функциональная проверка release без instrumentation в процессе приложения.
# Сверяем ActivityRecord/task и PID, видимое описание и действующий alarm;
# это не чтение Room и не измерение визуальных кадров predictive-анимации.
function Read-ReleaseResumed([string]$Prefix) {
    $dump=Invoke-ReleaseAdb @('shell','dumpsys','activity','activities')
    Set-Content (Join-Path $run "$Prefix-activities.log") $dump -Encoding UTF8
    # API33 закрывает ActivityRecord перед tNN, API35/36 — после него.
    # Скобка не является частью component; идентичность проверяем token/task.
    $records=[regex]::Matches($dump,'(?m)^\s*(?:mResumedActivity:\s*|topResumedActivity=\s*)ActivityRecord\{(?<token>\S+) u0 (?<component>com\.vpe_soft\.intime\.intime/[^{}\s]+)\}? t(?<task>\d+)[^\r\n]*')
    $unique=@($records|ForEach-Object {$_.Groups['token'].Value}|Select-Object -Unique)
    if($unique.Count -ne 1){throw 'Unique resumed release ActivityRecord missing'}
    return @{token=$unique[0];component=$records[0].Groups['component'].Value;task=$records[0].Groups['task'].Value}
}
function Wait-ReleaseNavigationMode([string]$Expected) {
    $limit=(Get-Date).AddSeconds(20)
    do {
        if((Invoke-ReleaseAdb @('shell','settings','get','secure','navigation_mode')).Trim() -eq $Expected){return}
        Start-Sleep -Milliseconds 250
    } while((Get-Date) -lt $limit)
    throw "Navigation mode not reached: $Expected"
}
function Test-ReleaseEdgeGestures([string]$Description,[long]$Alarm) {
    $oldMode=(Invoke-ReleaseAdb @('shell','settings','get','secure','navigation_mode')).Trim()
    $oldFlag=(Invoke-ReleaseAdb @('shell','settings','get','global','enable_back_animation')).Trim()
    if($oldMode -notmatch '^[012]$' -or $oldFlag -notmatch '^(null|[01])$'){throw 'Unknown navigation state'}
    $overlays=Invoke-ReleaseAdb @('shell','cmd','overlay','list','--user','0')
    Set-Content (Join-Path $run 'gesture-overlay-before.log') $overlays -Encoding UTF8
    $active=[regex]::Matches($overlays,'\[x\]\s+(com\.android\.internal\.systemui\.navbar\.[A-Za-z0-9_.]+)')
    if($active.Count -gt 1){throw 'Ambiguous navigation overlays'}
    $oldOverlay=if($active.Count){$active[0].Groups[1].Value}else{$null}
    $summary.gestureNavigationBefore=@{mode=$oldMode;overlay=$oldOverlay;backAnimation=$oldFlag}
    try {
        Invoke-ReleaseAdb @('shell','cmd','overlay','enable-exclusive','--user','0','--category','com.android.internal.systemui.navbar.gestural')|Out-Null
        Wait-ReleaseNavigationMode '2'
        $ready=$false;$limit=(Get-Date).AddSeconds(20)
        do {
            $systemUi=Invoke-ReleaseAdb @('shell','dumpsys','activity','service','com.android.systemui/.SystemUIService')
            foreach($section in [regex]::Matches($systemUi,'EdgeBackGestureHandler:([^\r\n]*(?:\r?\n[^\r\n]*\S[^\r\n]*)*)')){
                $body=$section.Groups[1].Value
                if($body.Contains('mIsEnabled=true') -and $body.Contains('mIsAttached=true') -and ($body.Contains('mIsGesturalModeEnabled=true') -or $body.Contains('mIsGestureHandlingEnabled=true'))){$ready=$true}
            }
            if(-not $ready){Start-Sleep -Milliseconds 250}
        } while(-not $ready -and (Get-Date) -lt $limit)
        Set-Content (Join-Path $run 'gesture-systemui-ready.log') $systemUi -Encoding UTF8
        if(-not $ready){throw 'Edge handler unavailable; bootstrap with EmulatorSoftwareNavigation'}
        if($TestApi -le 34){
            Invoke-ReleaseAdb @('shell','settings','put','global','enable_back_animation','1')|Out-Null
            if((Invoke-ReleaseAdb @('shell','settings','get','global','enable_back_animation')).Trim() -ne '1'){throw 'Back animation flag not enabled'}
        }
        $main=Read-ReleaseResumed 'gesture-main-before'
        if($main.component -notmatch '/(?:com\.vpe_soft\.intime\.intime)?\.activity\.MainActivityV2$'){throw 'Main not resumed'}
        $pidBefore=(Invoke-ReleaseAdb @('shell','pidof',$package)).Trim()
        Click-ReleaseId 'btn_open_settings'
        Wait-ReleaseNode "//node[@resource-id='${package}:id/export_to_json_btn']"|Out-Null
        $settings=Read-ReleaseResumed 'gesture-settings-before'
        if($settings.component -notmatch '/(?:com\.vpe_soft\.intime\.intime)?\.activity\.SettingsActivity$'){throw 'Settings not resumed'}
        foreach($mode in @('cancel','commit')){
            $result=Invoke-ReleaseAdb @('shell','uiautomator','runtest','/system/framework/android.test.runner.jar','/system/framework/android.test.base.jar','/data/local/tmp/intime-release-probe.jar','-c','intime.smoke.ReleaseHierarchyProbe#testEdgeBackGesture','-e','edgeMode',$mode)
            Set-Content (Join-Path $run "gesture-$mode-probe.log") $result -Encoding UTF8
            if($result -notmatch 'OK \(1 test\)' -or $result -notmatch "INTIME_EDGE_GESTURE_$($mode.ToUpperInvariant())" -or $result -match 'FAILURES!!!|INSTRUMENTATION_FAILED|Test run aborted|shortMsg='){throw "Edge injection failed: $mode"}
            if($mode -eq 'cancel'){
                Wait-ReleaseNode "//node[@resource-id='${package}:id/export_to_json_btn']"|Out-Null
                $after=Read-ReleaseResumed 'gesture-cancel-after'
                if($after.token -ne $settings.token -or $after.task -ne $settings.task){throw 'Canceled gesture changed Settings ActivityRecord'}
            } else {
                Assert-ReleaseText $Description
                Wait-ReleaseNode "//node[@resource-id='${package}:id/fab_add_task']"|Out-Null
                $after=Read-ReleaseResumed 'gesture-commit-after'
                if($after.token -ne $main.token -or $after.task -ne $main.task){throw 'Committed gesture did not return to same Main ActivityRecord'}
            }
            if((Read-ReleaseAlarm) -ne $Alarm -or (Invoke-ReleaseAdb @('shell','pidof',$package)).Trim() -ne $pidBefore){throw 'Gesture changed alarm/process'}
            Complete-ReleasePhase "release-edge-back-$mode"
        }
        $summary.gestureResults=@{main=$main;settings=$settings;afterCommit=$after;pid=$pidBefore;alarm=$Alarm;edgeHandlerReady=$true;cancelKeptSettings=$true;commitReturnedToMain=$true}
    } finally {
        try {
            if($oldOverlay){Invoke-ReleaseAdb @('shell','cmd','overlay','enable-exclusive','--user','0','--category',$oldOverlay)|Out-Null}
            else {Invoke-ReleaseAdb @('shell','cmd','overlay','disable','--user','0','com.android.internal.systemui.navbar.gestural')|Out-Null}
            Wait-ReleaseNavigationMode $oldMode
        } finally {
            if($TestApi -le 34){
                if($oldFlag -eq 'null'){Invoke-ReleaseAdb @('shell','settings','delete','global','enable_back_animation')|Out-Null}
                else {Invoke-ReleaseAdb @('shell','settings','put','global','enable_back_animation',$oldFlag)|Out-Null}
            }
            if((Invoke-ReleaseAdb @('shell','settings','get','global','enable_back_animation')).Trim() -ne $oldFlag){throw 'Back animation flag not restored'}
        }
        $restored=Invoke-ReleaseAdb @('shell','cmd','overlay','list','--user','0')
        Set-Content (Join-Path $run 'gesture-overlay-restored.log') $restored -Encoding UTF8
        $restoredActive=[regex]::Matches($restored,'\[x\]\s+(com\.android\.internal\.systemui\.navbar\.[A-Za-z0-9_.]+)')
        $restoredOverlay=if($restoredActive.Count -eq 1){$restoredActive[0].Groups[1].Value}else{$null}
        if($restoredActive.Count -gt 1 -or $restoredOverlay -ne $oldOverlay){throw 'Navigation overlay not restored'}
        $summary.gestureNavigationRestored=$true
    }
}

# Читаем подписанную длительность dumpsys без предположения о фиксированной
# длине окна. Неизвестное «--» не превращается в ноль и не подтверждает отсрочку.
function Convert-ReleaseDurationMs([string]$Text) {
    if ($Text -eq '0') { return [long]0 }
    $match=[regex]::Match($Text,'^(?<sign>[+-])(?:(?<d>\d+)d)?(?:(?<h>\d+)h)?(?:(?<m>\d+)m(?!s))?(?:(?<s>\d+)s)?(?:(?<ms>\d+)ms)?$')
    if (-not $match.Success -or $Text -notmatch '\d') { throw "Unknown alarm duration: $Text" }
    $value=[long]$match.Groups['d'].Value*86400000+[long]$match.Groups['h'].Value*3600000+[long]$match.Groups['m'].Value*60000+[long]$match.Groups['s'].Value*1000+[long]$match.Groups['ms'].Value
    if ($match.Groups['sign'].Value -eq '-') { $value=-$value }
    return $value
}

# Три независимых службы подтверждают подготовку до первой доставки. Tag должен
# принадлежать текущему idle-until, а не истории отменённых alarm. Квоту читаем,
# но не меняем. Raw dump сохраняется отдельно, не раздувая summary.
function Read-ReleaseIdlePolicy([string]$Prefix) {
    $idle=Invoke-ReleaseTool $adb @('-s',$serial,'shell','dumpsys','deviceidle') "$Prefix-idle.log"
    $power=Invoke-ReleaseTool $adb @('-s',$serial,'shell','dumpsys','power') "$Prefix-power.log"
    $alarm=Invoke-ReleaseTool $adb @('-s',$serial,'shell','dumpsys','alarm') "$Prefix-alarm.log"
    $quota=[regex]::Match($alarm,'(?m)^\s*allow_while_idle_compat_quota=(\d+)')
    $window=[regex]::Match($alarm,'(?m)^\s*allow_while_idle_compat_window=([^\r\n]+)')
    $until=[regex]::Match($alarm,'(?m)^\s*(?:Idling until|Pending idle until):\s*Alarm\{[^\r\n]*\r?\n\s*tag=\*walarm\*:DeviceIdleController\.deep\r?\n\s*type=ELAPSED_WAKEUP origWhen=(?<remaining>\S+)')
    if (-not $quota.Success -or -not $window.Success) { throw 'Quota settings missing from AlarmManager' }
    return @{
        controllerIdle=($idle -match '(?m)^\s*mState=IDLE(?:\s|$)')
        powerIdle=($power -match '(?m)^\s*mDeviceIdleMode=true\s*$')
        idleUntilPresent=($alarm -match '(?m)^\s*(?:Idling until|Pending idle until):\s*Alarm\{[^\r\n]*\r?\n\s*tag=\*walarm\*:DeviceIdleController\.deep(?:\s|$)')
        compatQuota=[int]$quota.Groups[1].Value
        compatWindowMs=(Convert-ReleaseDurationMs $window.Groups[1].Value.Trim())
        idleUntilRemainingMs=$(if($until.Success){Convert-ReleaseDurationMs $until.Groups['remaining'].Value}else{$null})
    }
}

# Подготовка выполняется только до ожидания первой доставки. При отсутствии
# idle-until используем проверенный цикл IDLE → maintenance → IDLE; для часового
# окна дополнительно ограничиваем число циклов и проверяем горизонт. Исходные
# снимки и ответы сохраняются. Между доставками эта функция не вызывается.
function Initialize-ReleaseQuotaIdle {
    Start-Sleep -Seconds 5
    $summary.quotaInitialPolicy=Read-ReleaseIdlePolicy 'quota-initial'
    $summary.quotaControlledMaintenanceCycle=$false
    if (-not $summary.quotaInitialPolicy.idleUntilPresent) {
        if (-not ($summary.quotaInitialPolicy.controllerIdle -and $summary.quotaInitialPolicy.powerIdle)) { throw 'Quota setup outside confirmed forced IDLE' }
        $summary.quotaControlledMaintenanceCycle=$true
        $summary.quotaMaintenanceStep=(Invoke-ReleaseAdb @('shell','cmd','deviceidle','step','deep')).Trim()
        $state=(Invoke-ReleaseAdb @('shell','cmd','deviceidle','get','deep')).Trim()
        if ($state -eq 'IDLE_MAINTENANCE') { $summary.quotaIdleStep=(Invoke-ReleaseAdb @('shell','cmd','deviceidle','step','deep')).Trim() }
        elseif ($state -ne 'IDLE') { throw "Unexpected quota preparation state: $state" }
        Start-Sleep -Seconds 5
    }
    $summary.quotaPreparedPolicy=Read-ReleaseIdlePolicy 'quota-prepared'
    if ($DozeQuotaSeven) {
        if($null -eq $summary.quotaPreparedPolicy.idleUntilRemainingMs){throw 'Unknown idle-until horizon before hourly preparation'}
        # Короткий idle-until закончится раньше часового окна. До первого ожидания
        # допускаем до трёх maintenance-циклов и проверяем достаточный горизонт.
        # Это управляемая подготовка, а не естественный Doze; таймеры/квоты не меняем.
        $summary.quotaHorizonCycles=@()
        for($cycle=1; $cycle -le 3 -and $summary.quotaPreparedPolicy.idleUntilRemainingMs -lt 4800000; $cycle++) {
            $p=$summary.quotaPreparedPolicy
            if(-not ($p.controllerIdle -and $p.powerIdle -and $p.idleUntilPresent)){throw 'Quota horizon preparation outside confirmed IDLE'}
            $maintenance=(Invoke-ReleaseAdb @('shell','cmd','deviceidle','step','deep')).Trim()
            $state=(Invoke-ReleaseAdb @('shell','cmd','deviceidle','get','deep')).Trim()
            $idleStep=$null
            if($state -eq 'IDLE_MAINTENANCE'){$idleStep=(Invoke-ReleaseAdb @('shell','cmd','deviceidle','step','deep')).Trim()}
            elseif($state -ne 'IDLE'){throw "Unexpected quota horizon state: $state"}
            $summary.quotaHorizonCycles+=@{maintenance=$maintenance;idle=$idleStep}
            Start-Sleep -Seconds 5
            $summary.quotaPreparedPolicy=Read-ReleaseIdlePolicy "quota-horizon-$cycle"
        }
        if($null -eq $summary.quotaPreparedPolicy.idleUntilRemainingMs -or $summary.quotaPreparedPolicy.idleUntilRemainingMs -lt 4800000){throw 'Idle-until horizon insufficient for hourly quota observation'}
    }
    $policy=$summary.quotaPreparedPolicy
    $expectedQuota=if($DozeQuotaSeven){7}else{1}
    if (-not ($policy.controllerIdle -and $policy.powerIdle -and $policy.idleUntilPresent) -or $policy.compatQuota -ne $expectedQuota -or $policy.compatWindowMs -le 0) { throw 'Quota setup requires active idle-until and expected compat quota' }
    if($DozeQuotaSeven -and $policy.compatWindowMs -ne 3600000){throw 'Hourly quota observation requires measured one-hour window'}
    if ($policy.compatQuota -ne $summary.quotaInitialPolicy.compatQuota -or $policy.compatWindowMs -ne $summary.quotaInitialPolicy.compatWindowMs) { throw 'Quota settings changed during preparation' }
}

# После одной или семи настоящих доставок receiver поставил следующую задачу.
# Требуем её действующий alarm, compat-флаг без unrestricted, историю своего
# пакета в истории и device_idle позже requester. Большой gap без этих признаков
# не считается квотой. Проверка ничего не запускает и не меняет настройки.
function Assert-ReleaseQuotaDeferral([long]$Deadline, [string]$Prefix='quota-after-first', [int]$ExpectedHistoryCount=1) {
    $policy=Read-ReleaseIdlePolicy $Prefix
    $summary.quotaBeforeDeferredDelivery=$policy
    if($ExpectedHistoryCount -eq 1){$summary.quotaAfterFirst=$policy}
    if (-not ($policy.controllerIdle -and $policy.powerIdle -and $policy.idleUntilPresent) -or $policy.compatQuota -ne $summary.quotaPreparedPolicy.compatQuota -or $policy.compatWindowMs -ne $summary.quotaPreparedPolicy.compatWindowMs) { throw 'Idle policy or quota changed after first delivery' }
    $dump=Get-Content (Join-Path $run "$Prefix-alarm.log") -Raw -Encoding UTF8
    $own=[regex]::Match($dump,'(?m)^\s*RTC_WAKEUP #\d+: Alarm\{\S+ type 0 origWhen (?<deadline>\d+) [^\r\n]* com\.vpe_soft\.intime\.intime\}\r?\n\s+tag=\*walarm\*:com\.vpe_soft\.intime\.intime/(?:com\.vpe_soft\.intime\.intime)?\.receiver\.AlarmReceiver\r?\n\s+type=RTC_WAKEUP[^\r\n]*flags=0x(?<flags>[a-fA-F0-9]+)\r?\n\s+policyWhenElapsed: requester=(?<requester>\S+)[^\r\n]*device_idle=(?<idle>\S+)')
    $history=[regex]::Match($dump,'(?ms)^\s*Allow while idle compat history:\s*\r?\n(?<entries>.*?)(?=^\s*Removal history:|\z)')
    if (-not $own.Success -or [long]$own.Groups['deadline'].Value -ne $Deadline -or -not $history.Success -or $history.Groups['entries'].Value -notmatch '(?m)^\s*com\.vpe_soft\.intime\.intime, u0:\s*[+-]') { throw 'Own second alarm or first quota history missing' }
    $flags=[Convert]::ToInt64($own.Groups['flags'].Value,16)
    if (($flags -band 0x20) -eq 0 -or ($flags -band 0x8) -ne 0) { throw 'Second alarm is not restricted compat allow-while-idle' }
    $requester=Convert-ReleaseDurationMs $own.Groups['requester'].Value
    $idle=Convert-ReleaseDurationMs $own.Groups['idle'].Value
    if ($idle -le $requester+1000) { throw 'Second alarm has no positive idle-policy deferral' }
    $summary.quotaDeferral=@{deadline=$Deadline; requesterRemainingMs=$requester; idleRemainingMs=$idle; deferredBeyondRequestedMs=($idle-$requester); compatFlags=$flags; firstHistoryConfirmed=$true}
    if($ExpectedHistoryCount -gt 1) {
        # Считаем только записи своего пакета в compat-секции. Все семь должны
        # быть внутри окна; device_idle сверяем с освобождением самой старой записи.
        $line=[regex]::Match($history.Groups['entries'].Value,'(?m)^\s*com\.vpe_soft\.intime\.intime, u0:\s*(?<ages>[^\r\n]+)')
        $ages=@($line.Groups['ages'].Value.Split(',')|ForEach-Object {if($_.Trim()){Convert-ReleaseDurationMs $_.Trim()}})
        if($ages.Count -ne $ExpectedHistoryCount -or @($ages|Where-Object {$_ -gt 0 -or $_ -le -$policy.compatWindowMs}).Count){throw 'Expected seven own dispatches inside quota window'}
        $oldest=($ages|Measure-Object -Minimum).Minimum
        $remaining=$policy.compatWindowMs+$oldest
        if([math]::Abs($idle-$remaining) -gt 5000){throw 'Idle policy does not match oldest quota entry expiry'}
        $summary.quotaDeferral.historyCount=$ages.Count
        $summary.quotaDeferral.oldestExpiryRemainingMs=$remaining
        $summary.quotaDeferral.oldestExpiryMatched=$true
    }
}

# Задача сохранена UI, exact appop запрещён. Проверяем собственный активный
# AlarmReceiver с тем же deadline и ненулевым окном, затем ждём настоящую доставку
# до 15 минут без Activity/probe/ручного receiver. Фиксированные файлы опроса
# ограничивают расход диска. Требуем ACK, запись receiver и потребление alarm;
# один тихий periodic worker не может подменить успех. При RequireDeepIdle
# каждый опрос и финальный снимок требуют IDLE; конкретная квота не утверждается.
function Assert-ReleaseInexactDelivery([string]$Description, [long]$Deadline, [string]$Since, [bool]$RequireDeepIdle = $false, [long]$ExpectedNextDeadline = 0, [string]$EvidencePrefix = 'inexact', [int]$WaitMinutes = 15) {
    # Часовое ожидание может вытеснить ранние строки из кольцевого logcat.
    # Сохраняем настоящий журнал до ожидания; при проверке объединяем с новым.
    $beforeLog=if($DozeQuotaSeven){Invoke-ReleaseTool $adb @('-s',$serial,'logcat','-d','-T',$Since) "$EvidencePrefix-logcat-before.log"}else{''}
    $ops = Invoke-ReleaseAdb @('shell', 'appops', 'get', $package, 'SCHEDULE_EXACT_ALARM')
    Set-Content (Join-Path $run "$EvidencePrefix-appops.log") $ops -Encoding UTF8
    if ($ops -notmatch 'SCHEDULE_EXACT_ALARM: deny') { throw 'Exact alarm denial not confirmed' }
    $alarms = Invoke-ReleaseAdb @('shell', 'dumpsys', 'alarm')
    Set-Content (Join-Path $run "$EvidencePrefix-alarm-before.log") $alarms -Encoding UTF8
    $alarm = [regex]::Match($alarms, '(?m)^\s*RTC_WAKEUP #\d+: Alarm\{\S+ type 0 origWhen (?<deadline>\d+) [^\r\n]* com\.vpe_soft\.intime\.intime\}\r?\n\s+tag=\*walarm\*:com\.vpe_soft\.intime\.intime/(?:com\.vpe_soft\.intime\.intime)?\.receiver\.AlarmReceiver\r?\n\s+type=RTC_WAKEUP[^\r\n]*?window=(?<window>\S+)')
    if (-not $alarm.Success -or [long]$alarm.Groups['deadline'].Value -ne $Deadline -or $alarm.Groups['window'].Value -notmatch '[1-9]') { throw 'Own inexact alarm with positive window missing' }
    $summary.inexactAlarmWindow = $alarm.Groups['window'].Value
    $summary.exactAlarmDenied = $true
    $limit = (Get-Date).AddMinutes($WaitMinutes)
    $delivered = $false
    $previous = [datetimeoffset]::UtcNow
    do {
        $now = [datetimeoffset]::UtcNow
        $gap = ($now - $previous).TotalSeconds
        if ($gap -lt 0 -or $gap -gt 180) { throw 'Inexact observation interrupted by host polling gap' }
        $previous = $now
        if ($RequireDeepIdle) {
            $state = (Invoke-ReleaseTool $adb @('-s', $serial, 'shell', 'cmd', 'deviceidle', 'get', 'deep') "$EvidencePrefix-idle-poll.log").Trim()
            if ($state -ne 'IDLE') { throw "Deep idle not maintained: $state" }
            if ($DozeSequence) {
                # Команда deviceidle и фактический режим PowerManager — разные
                # свидетельства. Сохраняем второй, не выдавая IDLE сам по себе
                # за доказательство применения конкретной квоты AlarmManager.
                $power=Invoke-ReleaseTool $adb @('-s', $serial, 'shell', 'dumpsys', 'power') "$EvidencePrefix-power-poll.log"
                $mode=[regex]::Match($power, 'mDeviceIdleMode=(true|false)')
                $summary.sequencePowerDeviceIdleAtDelivery=if ($mode.Success) { $mode.Groups[1].Value -eq 'true' } else { $null }
                $summary.quotaEnforcementValidated=$false
                if ($DozeQuota -or $DozeQuotaSeven) {
                    # На каждом опросе проверяем реальную idle-политику; её утрату
                    # не исправляем шагами во время наблюдения двух доставок.
                    $queue=Invoke-ReleaseTool $adb @('-s',$serial,'shell','dumpsys','alarm') "$EvidencePrefix-alarm-poll.log"
                    $pending=$queue -match '(?m)^\s*(?:Idling until|Pending idle until):\s*Alarm\{[^\r\n]*\r?\n\s*tag=\*walarm\*:DeviceIdleController\.deep(?:\s|$)'
                    if ($summary.sequencePowerDeviceIdleAtDelivery -ne $true -or -not $pending) { throw 'Quota observation lost active AlarmManager or PowerManager idle' }
                    $summary.sequenceAlarmManagerIdleAtDelivery=$true
                }
            }
        }
        $dump = Invoke-ReleaseTool $adb @('-s', $serial, 'shell', 'dumpsys', 'notification', '--noredact') "$EvidencePrefix-notifications-poll.log"
        $record = [regex]::Match($dump, '(?ms)^\s*NotificationRecord\([^\r\n]*pkg=com\.vpe_soft\.intime\.intime [^\r\n]*\r?\n(?:(?!^\s*NotificationRecord\().)*')
        $when = [regex]::Match($record.Value, '(?m)^\s*when=(\d+)(?:/|\r?$)')
        $delivered = $record.Success -and $record.Value.Contains("android.text=String ($Description)") -and $record.Value -match 'actions=1' -and $when.Success -and [long]$when.Groups[1].Value -ge $Deadline
        if (-not $delivered) { Write-Output 'Waiting for real inexact alarm notification...'; Start-Sleep -Seconds 15 }
    } while (-not $delivered -and (Get-Date) -lt $limit)
    # Последняя пауза тоже учитывается: сон хоста может завершить while сразу
    # после пробуждения. Такой результат не подтверждает непрерывное наблюдение.
    $finalGap=([datetimeoffset]::UtcNow-$previous).TotalSeconds
    if ($finalGap -lt 0 -or $finalGap -gt 180) { throw 'Inexact observation interrupted by host polling gap' }
    $log = Invoke-ReleaseAdb @('logcat', '-d', '-T', $Since)
    if($DozeQuotaSeven){$log=$beforeLog+"`n"+$log}
    Set-Content (Join-Path $run "$EvidencePrefix-logcat.log") $log -Encoding UTF8
    if (-not $delivered) { throw 'Inexact alarm notification timed out; delivery not validated' }
    if ($log -notmatch 'scheduleAlarm: exact alarms are not available, using inexact alarm' -or $log -notmatch 'AlarmReceiver.*handleAlarm: trying to post notification') { throw 'Fallback scheduling or actual receiver log missing' }
    if ($ExpectedNextDeadline -gt 0) {
        if ((Wait-ReleaseAlarm $Deadline) -ne $ExpectedNextDeadline) { throw 'Second prearranged alarm not scheduled after first delivery' }
    } elseif ((Read-ReleaseAlarm) -ne 0) { throw 'Inexact alarm was not consumed' }
    if ($RequireDeepIdle) {
        $state = (Invoke-ReleaseAdb @('shell', 'cmd', 'deviceidle', 'get', 'deep')).Trim()
        if ($state -ne 'IDLE') { throw "Deep idle not maintained at delivery: $state" }
        $summary.dozeStateAtDelivery = $state
        Set-Content (Join-Path $run 'doze-state.log') (Invoke-ReleaseAdb @('shell', 'dumpsys', 'deviceidle')) -Encoding UTF8
    }
    $summary.inexactNotificationWhen = [long]$when.Groups[1].Value
    $summary.inexactDelaySeconds = ($summary.inexactNotificationWhen - $Deadline) / 1000.0
    Complete-ReleasePhase $(if($DozeQuotaSeven){"release-inexact-quota-delivery-$EvidencePrefix"}elseif ($EvidencePrefix -eq 'inexact') { 'release-inexact-fallback-delivery' } else { 'release-inexact-repeat-delivery' })
}

# Восстанавливаем каждую изменённую настройку даже при ошибке предыдущей команды.
# Вызывается и из finally: отказ проверки доставки не должен оставить forced idle
# или имитацию отключённой зарядки на проектном AVD.
function Restore-ReleaseDoze {
    $errors = @()
    $restoreCommands = @(@('shell', 'cmd', 'deviceidle', 'unforce'), @('shell', 'dumpsys', 'battery', 'reset'), @('shell', 'input', 'keyevent', '224'))
    # AOSP AVD может иметь deep idle выключенным. Возвращаем исходное enabled,
    # не меняя light idle и постоянные whitelist/configuration ОС.
    if ($null -ne $script:dozeDeepWasEnabled -and -not $script:dozeDeepWasEnabled) {
        $restoreCommands += ,@('shell', 'cmd', 'deviceidle', 'disable', 'deep')
    }
    foreach ($arguments in $restoreCommands) {
        try { Invoke-ReleaseAdb $arguments | Out-Null } catch { $errors += $_.Exception.Message }
    }
    if ($errors.Count) { throw ($errors -join '; ') }
    $script:dozeChanged = $false
}

# Tap по строке иногда теряется после перезапуска/перехода. Повторяем его только
# пока свежий XML показывает именно нужную строку списка; появление btnAckTask
# подтверждает экран деталей. ACK не повторяется и выполняется отдельной фазой.
function Open-ReleaseDetails([string]$Description) {
    $limit = (Get-Date).AddSeconds(30)
    $row = "//node[@resource-id='${package}:id/textDescription' and @text='$Description']"
    do {
        $ui = Read-ReleaseUi
        if ($ui.SelectSingleNode("//node[@resource-id='${package}:id/btnAckTask']")) { return }
        if ($ui.SelectSingleNode($row)) { Click-ReleaseNode $row }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $limit)
    throw 'Task details did not open'
}

# После ACK последняя задача становится будущей и уходит ниже семи просроченных.
# Проверяем её реальное присутствие через ограниченную прокрутку RecyclerView,
# а не требуем видимости на первой странице. Скролл допустим только после
# завершения доставки; он не меняет задачи и не заменяет проверку срока alarm.
function Assert-ReleaseListTask([string]$Description) {
    # После contentIntent или restart SystemUI ещё может перекрывать Activity.
    # Ждём реальный список с обычным 30-секундным пределом до первой прокрутки.
    Wait-ReleaseNode "//node[@resource-id='${package}:id/recyclerView']" | Out-Null
    for($page=0;$page -lt 8;$page++) {
        $ui=Read-ReleaseUi
        if($ui.SelectSingleNode("//node[@resource-id='${package}:id/textDescription' and @text='$Description']")){return}
        $list=$ui.SelectSingleNode("//node[@resource-id='${package}:id/recyclerView']")
        if(-not $list){throw 'Task list missing during post-ACK verification'}
        $bounds=[regex]::Match($list.GetAttribute('bounds'),'^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$')
        if(-not $bounds.Success){throw 'Task list bounds unknown'}
        $x=[int]([int]$bounds.Groups[1].Value+([int]$bounds.Groups[3].Value-[int]$bounds.Groups[1].Value)/2)
        $top=[int]$bounds.Groups[2].Value;$height=[int]$bounds.Groups[4].Value-$top
        if($height -lt 100){throw 'Task list too small for safe scroll'}
        if($page -lt 7){Invoke-ReleaseAdb @('shell','input','swipe',"$x","$([int]($top+$height*0.8))","$x","$([int]($top+$height*0.2))",'400')|Out-Null}
    }
    throw "Task not found after bounded list scroll: $Description"
}

# До установки проверяем метаданные aapt: исходный production-пакет, ожидаемый
# код/имя версии и отсутствие debuggable. Подпись и mapping проверяются отдельно.
# Совпадение кода не доказывает, что код свободен в Play Console; здесь защищаем
# тестовый запуск от проверки другого APK вместо выбранного кандидата.
function Assert-ReleaseApkIdentity([string]$Badging, [int]$Code, [string]$VersionName = '2.0.0-rc1') {
    $identity = "(?m)^package: name='$([regex]::Escape($package))' versionCode='$Code' versionName='$([regex]::Escape($VersionName))'"
    if($Badging -notmatch $identity -or $Badging -match 'application-debuggable') {
        throw "Expected non-debuggable production candidate $Code/$VersionName"
    }
}

# Восемь синтетических задач проходят настоящий JSON parser/Room через SAF.
# Первая назначена через две минуты, следующие с шагом три минуты.
# Приватная база не читается; экспорт CRUD сохраняется отдельно. Заменяем
# только собственный временный файл в Download и удаляем его в finally.
function Prepare-ReleaseHourlyQuotaTasks([string]$DescriptionPrefix) {
    $now=[long](Invoke-ReleaseAdb @('shell','date','+%s')).Trim()*1000
    $rows=@();$tasks=@()
    for($index=0;$index -lt 8;$index++) {
        $deadline=$now+120000+$index*180000
        $description="$DescriptionPrefix-Quota$($index+1)"
        $rows+=,@([long]($index+1),$description,1,1,[long]$deadline,[long]($deadline-180000),[long]($deadline-3600000),1)
        $tasks+=@{description=$description;deadline=[long]$deadline}
    }
    $fixture=@{meta=@{version=1;exportedAt=$now};tables=@{tasks=@{columns=@('id','description','interval','amount','next_alarm','next_caution','last_ack','quant');rows=$rows}}}
    $path=Join-Path $run 'hourly-quota-fixture.json'
    $fixture|ConvertTo-Json -Depth 8|Set-Content -LiteralPath $path -Encoding UTF8
    Invoke-ReleaseAdb @('push',$path,$backupDevicePath)|Out-Null
    Launch-Release
    Click-ReleaseId 'btn_open_settings'
    Click-ReleaseId 'import_from_json_btn'
    Click-ReleaseNode "//node[@text='$backupName']"
    Wait-ReleaseNode "//node[@resource-id='${package}:id/import_from_json_btn']"|Out-Null
    Invoke-ReleaseAdb @('shell','input','keyevent','4')|Out-Null
    Assert-ReleaseText $tasks[0].description
    if((Wait-ReleaseAlarm) -ne $tasks[0].deadline){throw 'First hourly quota task not scheduled after SAF import'}
    Invoke-ReleaseAdb @('shell','am','force-stop',$package)|Out-Null
    Launch-Release
    Assert-ReleaseText $tasks[0].description
    if((Wait-ReleaseAlarm) -ne $tasks[0].deadline){throw 'Hourly quota fixture alarm lost after restart'}
    return $tasks
}

# Перед ACK worker вправе заменить прямое уведомление новой тихой сводкой.
# Повторно проверяем актуальные текст/when/PID/SUCCESS и contentIntent;
# доказательство восьмой системной отправки при этом не переопределяется.
function Assert-ReleaseQuotaNotification($Task,[long]$dispatch) {
    $clock=(Invoke-ReleaseAdb @('shell',"date '+%Y %z'")).Trim()
    if($clock -notmatch '^(\d{4}) ([+-]\d{2})(\d{2})$'){throw 'Device logcat timezone unknown'}
    $year=$Matches[1];$zone=$Matches[2]+':'+$Matches[3]
    $recent=Invoke-ReleaseTool $adb @('-s',$serial,'logcat','-d') 'quota8-notification-logcat.log'
    $processes=Invoke-ReleaseTool $adb @('-s',$serial,'shell','ps','-A') 'quota8-notification-processes.log'
    $releaseProcessIds=@([regex]::Matches($processes,'(?m)^\S+\s+(?<process>\d+)\s+[^\r\n]*\scom\.vpe_soft\.intime\.intime\s*$')|ForEach-Object {$_.Groups['process'].Value})
    $releaseProcessIds+=@([regex]::Matches($recent,'Start proc (?<process>\d+):com\.vpe_soft\.intime\.intime/')|ForEach-Object {$_.Groups['process'].Value})
    $notification=Invoke-ReleaseTool $adb @('-s',$serial,'shell','dumpsys','notification','--noredact') 'quota8-dispatch-notification.log'
    $record=[regex]::Match($notification,'(?ms)^\s*NotificationRecord\([^\r\n]*pkg=com\.vpe_soft\.intime\.intime(?=\s)[^\r\n]*\r?\n.*?(?=^\s*NotificationRecord\(|^\s*Current Notification List:|^\s*Notification listeners:|\z)')
    $text=[regex]::Match($record.Value,'android.text=String \((?<text>[^\r\n]+)\)')
    $when=[regex]::Match($record.Value,'when=(\d+)')
    if(-not $when.Success -or [long]$when.Groups[1].Value -lt $Task.deadline){throw 'Eighth due task notification missing'}
    $direct=$text.Groups['text'].Value -eq $Task.description
    if($direct -and $record.Value -notmatch 'actions=1'){throw 'Direct eighth notification ACK missing'}
    if($direct -and [long]$when.Groups[1].Value -lt $dispatch-2000){throw 'Direct eighth notification predates its dispatch'}
    $workerText=$summary.quotaTasks[0].description+' and 7 more tasks'
    $workerConfirmed=$false
    foreach($event in [regex]::Matches($recent,'(?m)^(?<stamp>\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})\s+(?<process>\d+)\s+\d+[^\r\n]*Worker result SUCCESS[^\r\n]*TaskNotificationWorker')){
        if($releaseProcessIds -contains $event.Groups['process'].Value){$time=[datetimeoffset]::ParseExact("$year-$($event.Groups['stamp'].Value) $zone",'yyyy-MM-dd HH:mm:ss.fff zzz',[cultureinfo]::InvariantCulture);if($time.ToUnixTimeMilliseconds() -ge [long]$when.Groups[1].Value-2000){$workerConfirmed=$true}}
    }
    if(-not $direct -and ($text.Groups['text'].Value -ne $workerText -or -not $workerConfirmed)){throw 'Eighth notification is neither direct nor verified natural worker summary'}
    if(-not $direct -and $record.Value -notmatch 'contentIntent=PendingIntent\{[^\r\n]*com\.vpe_soft\.intime\.intime startActivity'){throw 'Worker summary list PendingIntent missing'}
    $summary.quotaEighthUsedWorker=-not $direct
    $summary.quotaEighthNotificationText=$text.Groups['text'].Value
    $summary.quotaEighthNotificationWhen=[long]$when.Groups[1].Value
    $summary.quotaEighthWorkerBeforeDispatch=(-not $direct -and [long]$when.Groups[1].Value -lt $dispatch)
}

# Проверяем restricted alarm до ожидания, восемь доставок в счётчике пакета,
# восемь callback собственного PID, последнюю отправку после истечения окна
# и потребление alarm. История может сохранить семь или восемь записей.
# Уведомление проверяем отдельно: receiver может не публиковать дубль worker.
# Resume принимает before dump только той же установки; caller проверяет
# владение AVD. Это внутренний режим продолжения, а не новая OS-проверка.
function Assert-ReleaseQuotaEighthDispatch($Task,[string]$Since,[long]$FirstWhen,[bool]$Resume=$false) {
    $beforePath=Join-Path $run 'quota8-alarm-before.log'
    if(-not $Resume){Invoke-ReleaseTool $adb @('-s',$serial,'shell','dumpsys','alarm') 'quota8-alarm-before.log'|Out-Null}
    $before=Get-Content $beforePath -Raw
    $own=[regex]::Match($before,'(?m)^\s*RTC_WAKEUP #\d+: Alarm\{\S+ type 0 origWhen (?<deadline>\d+) [^\r\n]* com\.vpe_soft\.intime\.intime\}\r?\n\s+tag=\*walarm\*:com\.vpe_soft\.intime\.intime/(?:com\.vpe_soft\.intime\.intime)?\.receiver\.AlarmReceiver\r?\n\s+type=RTC_WAKEUP[^\r\n]*flags=0x(?<flags>[a-fA-F0-9]+)')
    if(-not $own.Success -or [long]$own.Groups['deadline'].Value -ne $Task.deadline -or ([Convert]::ToInt64($own.Groups['flags'].Value,16) -band 0x28) -ne 0x20){throw 'Restricted eighth alarm before dispatch missing'}
    if(-not $Resume){Invoke-ReleaseTool $adb @('-s',$serial,'logcat','-d','-T',$Since) 'quota8-logcat-before.log'|Out-Null}
    $early=Get-Content (Join-Path $run 'quota8-logcat-before.log') -Raw
    $clock=(Invoke-ReleaseAdb @('shell',"date '+%Y %z'")).Trim()
    if($clock -notmatch '^(\d{4}) ([+-]\d{2})(\d{2})$'){throw 'Device logcat timezone unknown'}
    $year=$Matches[1];$zone=$Matches[2]+':'+$Matches[3]
    $limit=(Get-Date).AddMinutes(75);$previous=[datetimeoffset]::UtcNow
    do {
        $now=[datetimeoffset]::UtcNow
        if(($now-$previous).TotalSeconds -lt 0 -or ($now-$previous).TotalSeconds -gt 180){throw 'Inexact observation interrupted by host polling gap'}
        $previous=$now
        $policy=Read-ReleaseIdlePolicy 'quota8-dispatch-poll'
        if(-not ($policy.controllerIdle -and $policy.powerIdle -and $policy.idleUntilPresent) -or $policy.compatQuota -ne 7 -or $policy.compatWindowMs -ne 3600000){throw 'Hourly dispatch observation lost confirmed idle policy'}
        $dump=Get-Content (Join-Path $run 'quota8-dispatch-poll-alarm.log') -Raw
        $h=[regex]::Match($dump,'(?ms)^\s*Allow while idle compat history:\s*\r?\n(?<entries>.*?)(?=^\s*Removal history:|\z)')
        $line=[regex]::Match($h.Groups['entries'].Value,'(?m)^\s*com\.vpe_soft\.intime\.intime, u0:\s*(?<ages>[^\r\n]+)')
        $ages=@($line.Groups['ages'].Value.Split(',')|ForEach-Object {if($_.Trim()){Convert-ReleaseDurationMs $_.Trim()}})
        $recent=Invoke-ReleaseTool $adb @('-s',$serial,'logcat','-d','-T',$Since) 'quota8-dispatch-logcat-poll.log'
        # Процесс вправе умереть до alarm и запуститься снова при dispatch.
        # PID берём из ps и записей запуска именно своего пакета; пустой ps
        # до доставки не является отказом. Не используем readonly переменную PID.
        $processes=Invoke-ReleaseTool $adb @('-s',$serial,'shell','ps','-A') 'quota8-processes-poll.log'
        $releaseProcessIds=@([regex]::Matches($processes,'(?m)^\S+\s+(?<process>\d+)\s+[^\r\n]*\scom\.vpe_soft\.intime\.intime\s*$')|ForEach-Object {$_.Groups['process'].Value})
        $releaseProcessIds+=@([regex]::Matches(($early+"`n"+$recent),'Start proc (?<process>\d+):com\.vpe_soft\.intime\.intime/')|ForEach-Object {$_.Groups['process'].Value})
        $events=[regex]::Matches($recent,'(?m)^(?<stamp>\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})\s+(?<process>\d+)\s+\d+\s+D\s+AlarmReceiver\s*:\s*onReceive\s*$')
        $dispatch=[long]0
        foreach($event in $events){if($releaseProcessIds -notcontains $event.Groups['process'].Value){continue};$time=[datetimeoffset]::ParseExact("$year-$($event.Groups['stamp'].Value) $zone",'yyyy-MM-dd HH:mm:ss.fff zzz',[cultureinfo]::InvariantCulture);if($time.ToUnixTimeMilliseconds() -ge $FirstWhen+3600000-2000){$dispatch=$time.ToUnixTimeMilliseconds()}}
        $consumed=$dump -notmatch '(?m)^\s*RTC_WAKEUP #\d+: Alarm\{[^\r\n]*com\.vpe_soft\.intime\.intime\}'
        # История квоты хранит окно времени: при восьмой доставке Android вправе
        # удалить вышедший из окна элемент и оставить семь. Подтверждаем восемь
        # доставок отдельным счётчиком пакета и восемью callback его PID в logcat.
        $stats=[regex]::Match($dump,'(?m)^\s*[^\r\n]*\b8 wakeups, 8 alarms: u0a\d+:com\.vpe_soft\.intime\.intime\s*$')
        $ownCallbacks=@($events|Where-Object {$releaseProcessIds -contains $_.Groups['process'].Value})
        $historyValid=$ages.Count -ge 7 -and $ages.Count -le 8
        if($historyValid -and $stats.Success -and $ownCallbacks.Count -eq 8 -and $dispatch -gt 0 -and $consumed){break}
        Write-Output 'Waiting for eighth own AlarmManager dispatch...'
        Start-Sleep -Seconds 15
    }while((Get-Date) -lt $limit)
    if($dispatch -eq 0 -or -not $historyValid -or -not $stats.Success -or $ownCallbacks.Count -ne 8 -or -not $consumed){throw 'Inexact alarm notification timed out; eighth dispatch not validated'}
    Assert-ReleaseQuotaNotification $Task $dispatch
    $summary.quotaEighthDispatchWhen=$dispatch
    $summary.quotaEighthHistoryCount=$ages.Count
    $summary.quotaEighthPackageAlarmCount=8
    $summary.quotaEighthOwnCallbackCount=$ownCallbacks.Count
    $summary.inexactNotificationWhen=$dispatch
    $summary.inexactDelaySeconds=($dispatch-$Task.deadline)/1000.0
    $summary.sequencePowerDeviceIdleAtDelivery=$true
    $summary.dozeStateAtDelivery='IDLE'
    Set-Content (Join-Path $run 'quota8-logcat.log') ($early+"`n"+$recent) -Encoding UTF8
    Complete-ReleasePhase 'release-inexact-quota-delivery-quota8'
}

# Задачи заранее сохранены и запланированы приложением, открыт Home.
# На своём AVD имитируем отключение зарядки, гасим экран и включаем deep idle.
# Во время ожидания нет UI/ручного receiver, каждый опрос подтверждает idle.
# Обычный режим проверяет одну отправку, Sequence — две, QuotaSeven — восемь.
# Квоту доказывают отдельные проверки history/policy/callback, большой интервал
# сам по себе не считается доказательством. Естественный Doze/OEM не покрываются.
function Assert-ReleaseDozeDelivery([string]$Description, [long]$Deadline, [string]$SchedulingSince) {
    $battery = Invoke-ReleaseAdb @('shell', 'dumpsys', 'battery')
    Set-Content (Join-Path $run 'doze-battery-before.log') $battery -Encoding UTF8
    if ($battery -match 'UPDATES STOPPED') { throw 'Refusing to overwrite existing battery simulation' }
    $idle = Invoke-ReleaseAdb @('shell', 'dumpsys', 'deviceidle')
    Set-Content (Join-Path $run 'doze-state-before.log') $idle -Encoding UTF8
    if ($idle -notmatch 'mForceIdle=false') { throw 'Refusing an already forced idle state' }
    $enabled = (Invoke-ReleaseAdb @('shell', 'cmd', 'deviceidle', 'enabled', 'deep')).Trim()
    if ($enabled -notin @('0', '1')) { throw "Unknown deep idle enabled state: $enabled" }
    $script:dozeDeepWasEnabled = $enabled -eq '1'
    $summary.dozeDeepOriginallyEnabled = $script:dozeDeepWasEnabled
    $whitelist = Invoke-ReleaseAdb @('shell', 'cmd', 'deviceidle', 'whitelist')
    Set-Content (Join-Path $run 'doze-whitelist.log') $whitelist -Encoding UTF8
    if ($whitelist -match [regex]::Escape($package)) { throw 'Release package is exempt from device idle' }
    $since = (Invoke-ReleaseAdb @('shell', "date '+%m-%d %H:%M:%S.000'")).Trim()
    $script:dozeChanged = $true
    if (-not $script:dozeDeepWasEnabled) { Invoke-ReleaseAdb @('shell', 'cmd', 'deviceidle', 'enable', 'deep') | Out-Null }
    Invoke-ReleaseAdb @('shell', 'dumpsys', 'battery', 'unplug') | Out-Null
    Invoke-ReleaseAdb @('shell', 'input', 'keyevent', '223') | Out-Null
    Invoke-ReleaseAdb @('shell', 'cmd', 'deviceidle', 'force-idle', 'deep') | Out-Null
    if ($DozeQuota -or $DozeQuotaSeven) { Initialize-ReleaseQuotaIdle }
    # Fallback должен доставиться при сохранённом deny и положительном окне.
    # Общая проверка требует ACK/receiver и IDLE до пробуждения; cleanup общий.
    if ($InexactOnly) {
        if ($DozeQuotaSeven) {
            $summary.quotaDeliveries=@()
            for($index=0;$index -lt 8;$index++) {
                $task=$summary.quotaTasks[$index]
                $next=if($index -lt 7){$summary.quotaTasks[$index+1].deadline}else{[long]0}
                if($index -eq 7){Assert-ReleaseQuotaDeferral $task.deadline 'quota-after-seven' 7}
                $wait=if($index -eq 7){75}else{15}
                Write-Output "Observing quota delivery $($index+1)/8..."
                if($index -eq 7){Assert-ReleaseQuotaEighthDispatch $task $SchedulingSince $summary.quotaDeliveries[0].when}
                else{Assert-ReleaseInexactDelivery $task.description $task.deadline $SchedulingSince $true $next "quota$($index+1)" $wait}
                $summary.quotaDeliveries+=@{description=$task.description;deadline=$task.deadline;when=$summary.inexactNotificationWhen;delaySeconds=$summary.inexactDelaySeconds;powerDeviceIdle=$summary.sequencePowerDeviceIdleAtDelivery}
            }
            $summary.sequenceFirst=$summary.quotaDeliveries[0]
            $summary.sequenceSecond=$summary.quotaDeliveries[7]
            $summary.sequenceGapSeconds=($summary.sequenceSecond.when-$summary.sequenceFirst.when)/1000.0
            $summary.quotaDeliveryToleranceMs=2000
            if($summary.sequenceGapSeconds*1000 -lt $summary.quotaPreparedPolicy.compatWindowMs-2000){throw 'Eighth delivery arrived before confirmed hourly quota expiry'}
            $lines=@(1..8|ForEach-Object {Get-Content (Join-Path $run "quota$_-logcat.log")}|Sort-Object -Unique)
            $summary.sequenceReceiverCount=@($lines|Where-Object {$_ -match 'AlarmReceiver.*onReceive'}).Count
            if($summary.sequenceReceiverCount -lt 8){throw 'Eight distinct receiver deliveries not confirmed'}
            $summary.quotaEnforcementValidated=$true
            Complete-ReleasePhase 'release-idle-quota-seven-deferral'
        } elseif ($DozeSequence) {
            # Обе задачи запланированы до IDLE. Первая доставка должна сама
            # поставить alarm второй задачи, а вторая — обновить record новым
            # описанием/when. Между ними не будим ОС и не вызываем ACK/UI.
            Assert-ReleaseInexactDelivery $Description $Deadline $SchedulingSince $true $summary.sequenceSecondDeadline 'inexact' 20
            $firstWhen = $summary.inexactNotificationWhen
            $summary.sequenceFirst = @{ deadline=$Deadline; when=$firstWhen; window=$summary.inexactAlarmWindow; delaySeconds=$summary.inexactDelaySeconds; powerDeviceIdle=$summary.sequencePowerDeviceIdleAtDelivery }
            if ($DozeQuota) { Assert-ReleaseQuotaDeferral $summary.sequenceSecondDeadline }
            Assert-ReleaseInexactDelivery $summary.sequenceSecondDescription $summary.sequenceSecondDeadline $SchedulingSince $true 0 'inexact-repeat' 20
            $summary.sequenceSecond = @{ deadline=$summary.sequenceSecondDeadline; when=$summary.inexactNotificationWhen; window=$summary.inexactAlarmWindow; delaySeconds=$summary.inexactDelaySeconds; powerDeviceIdle=$summary.sequencePowerDeviceIdleAtDelivery }
            $summary.sequenceGapSeconds = ($summary.inexactNotificationWhen - $firstWhen) / 1000.0
            $sequenceLog=Get-Content (Join-Path $run 'inexact-repeat-logcat.log') -Raw -Encoding UTF8
            $summary.sequenceReceiverCount = [regex]::Matches($sequenceLog, 'AlarmReceiver.*handleAlarm: trying to post notification').Count
            if ($summary.sequenceReceiverCount -lt 2 -or $summary.sequenceGapSeconds -le 0) { throw 'Two distinct receiver deliveries not confirmed' }
            if ($DozeQuota) {
                # Timestamp уведомления чуть позже системной записи dispatch;
                # допускаем 2 с этой разницы, но требуем отдельное доказательство
                # положительной idle-отсрочки выше, а не только большой gap.
                $summary.quotaDeliveryToleranceMs=2000
                if ($summary.sequenceGapSeconds*1000 -lt $summary.quotaPreparedPolicy.compatWindowMs-$summary.quotaDeliveryToleranceMs) { throw 'Second delivery arrived before the confirmed quota window' }
                $summary.quotaEnforcementValidated=$true
                Complete-ReleasePhase 'release-idle-quota-deferral'
            }
        } else {
            Assert-ReleaseInexactDelivery $Description $Deadline $SchedulingSince $true
        }
        Complete-ReleasePhase 'release-doze-delivery'
        Restore-ReleaseDoze
        return
    }
    $limit = (Get-Date).AddSeconds(150)
    $delivered = $false
    do {
        $state = (Invoke-ReleaseAdb @('shell', 'cmd', 'deviceidle', 'get', 'deep')).Trim()
        if ($state -ne 'IDLE') { throw "Deep idle not maintained: $state" }
        $notifications = Invoke-ReleaseAdb @('shell', 'dumpsys', 'notification', '--noredact')
        # Ищем только в активном record своего пакета, не в history/чужих уведомлениях.
        $record = [regex]::Match($notifications, '(?ms)^\s*NotificationRecord\([^\r\n]*pkg=com\.vpe_soft\.intime\.intime [^\r\n]*\r?\n(?:(?!^\s*NotificationRecord\().)*')
        $delivered = $record.Success -and $record.Value.Contains("android.text=String ($Description)")
        if (-not $delivered) { Start-Sleep -Seconds 2 }
    } while (-not $delivered -and (Get-Date) -lt $limit)
    Set-Content (Join-Path $run 'doze-notifications.log') $notifications -Encoding UTF8
    Set-Content (Join-Path $run 'doze-state.log') (Invoke-ReleaseAdb @('shell', 'dumpsys', 'deviceidle')) -Encoding UTF8
    $deliveryLog = Invoke-ReleaseAdb @('logcat', '-d', '-T', $since)
    Set-Content (Join-Path $run 'doze-logcat.log') $deliveryLog -Encoding UTF8
    if (-not $delivered -or $deliveryLog -notmatch 'AlarmReceiver.*handleAlarm: trying to post notification') { throw 'Release notification not confirmed during deep idle' }
    if ((Read-ReleaseAlarm) -ne 0) { throw 'Doze alarm was not consumed' }
    $summary.dozeStateAtDelivery = $state
    Complete-ReleasePhase 'release-doze-delivery'
    Restore-ReleaseDoze
}

# Начинаем новый минутный отсчёт только через существующую кнопку ACK в UI.
# После возврата Home приложение не подавляет доставку своей видимостью.
function Prepare-ReleaseMinuteAlarm([string]$Description) {
    Launch-Release
    Assert-ReleaseText $Description
    Open-ReleaseDetails $Description
    Click-ReleaseId 'btnAckTask'
    Assert-ReleaseText $Description
    $alarm = Wait-ReleaseAlarm
    Invoke-ReleaseAdb @('shell', 'input', 'keyevent', '3') | Out-Null
    return $alarm
}

# Сохраняем питание, Doze, сборку и uptime Android. Полную очередь JobScheduler
# читаем только перед ожиданием и после фиксации его результата: установленный
# API 36 при dumpsys пересчитывает Ready и может менять TimeController.
# Между началом и концом ожидания эту диагностику вызывать нельзя.
# Три фиксированных набора (before/latest/after) ограничивают расход диска;
# latest перезаписывается раз в пять минут, after снимается и при тайм-ауте.
# Эти dumpsys показывают состояние в момент снимка, но не доказывают причину
# решения планировщика между снимками и могут содержать другие системные пакеты.
function Save-ReleasePeriodicDiagnostics([ValidateSet('before', 'latest', 'after')][string]$Stage) {
    $services = @('power', 'deviceidle', 'battery')
    if ($Stage -ne 'latest') { $services = @('jobscheduler') + $services }
    foreach ($service in $services) {
        Invoke-ReleaseTool $adb @('-s', $serial, 'shell', 'dumpsys', $service) "periodic-diagnostic-$Stage-$service.log" | Out-Null
    }
    Invoke-ReleaseTool $adb @('-s', $serial, 'shell', 'cat', '/proc/uptime') "periodic-diagnostic-$Stage-uptime.log" | Out-Null
    Invoke-ReleaseTool $adb @('-s', $serial, 'shell', 'getprop', 'ro.build.fingerprint') 'periodic-build-fingerprint.log' | Out-Null
    Set-Content (Join-Path $run "periodic-diagnostic-$Stage-time.txt") ([datetimeoffset]::Now.ToString('o')) -Encoding UTF8
}

# Опрос выполняется каждые 30 секунд. Разрыв свыше трёх минут или перевод
# часов назад означает, что непрерывность наблюдения потеряна. Причину (сон,
# приостановку VM или изменение часов) эта проверка не устанавливает.
# Проверяем и последнюю паузу после выхода из цикла: из-за скачка часов
# условие while может завершить цикл раньше следующего опроса.
function Assert-ReleasePeriodicPollContinuity([datetimeoffset]$Previous, [datetimeoffset]$Current) {
    $gap = ($Current - $Previous).TotalSeconds
    if ($gap -lt 0 -or $gap -gt 180) {
        $summary.periodicObservationInterrupted = $true
        $summary.periodicHostPollGapSeconds = $gap
        $summary.naturalDispatchValidated = $false
        throw 'Periodic observation interrupted by host polling gap; continuous device wait not validated'
    }
}

# Чистая release-установка уже прошла CRUD/JSON/reboot и доставила настоящий
# минутный alarm с ACK. После Home убиваем только фоновый процесс (не force-stop),
# подтверждаем stopped=false и оставшуюся SystemJobService. Ждём новое активное
# уведомление с тем же уникальным описанием и when >= первой отправки + 15 минут.
# Проверяем отсутствие ACK, приоритет LOW, null sound/vibration и defaults=0
# в Notification, SILENT на API 35/36 и подавление оповещения группы. mSound в dumpsys
# может сохранять звук канала даже при SILENT, поэтому его null не требуем.
# а также SUCCESS настоящего WorkManager worker в logcat после смерти процесса.
# Штатный режим не вызывает job вручную. Если host отдельно выполнил контролируемый
# dispatch, он обязан сохранить маркер и вывод команды; итог будет PASSED_CONTROLLED,
# а не подтверждением естественной доставки. Не читаем private Room/preferences,
# поэтому сопоставление work UUID с private Room и wasNotified не утверждаем.
# PeriodicRepeats=2 проверяет следующий период без вмешательства между повторами.
# Doze/OEM и точное время запуска этой проверкой не покрываются.
# Проверяем именно объект Notification, а не настройки канала. API 31/33 печатают
# defaults/flags в hex, API 35/36 — ноль и символические флаги. На API 31/33 нет
# текстового SILENT: требуем ONLY_ALERT_ONCE (0x8), AUTO_CANCEL (0x10),
# groupAlertBehavior=1 и null sound/vibrate с нулевыми defaults. Звук динамика
# не измеряется; значение mSound канала не подменяет эти проверки.
function Assert-ReleaseQuietNotification([string]$Record) {
    if ($Record -match 'actions=[1-9]\d*|actions=\{' -or $Record -notmatch '(?m)^\s*pri=-1\s*$' -or $Record -notmatch 'vibrate=null sound=null defaults=(?:0x0|0)(?=\s)' -or $Record -notmatch '(?m)^\s*groupAlertBehavior=1\s*$') { throw 'Repeat is not a quiet notification without ACK' }
    # Проверяем отдельное поле flags: слово silent в groupKey/тексте той же
    # header-строки не является нативным флагом SILENT. AndroidX на31/33/35
    # подавляет звук канала через собственную тихую группу и groupAlertBehavior.
    # На36 ожидаем настоящий именованный флаг; для35 допускаем оба формата.
    $nativeSilent = $Record -cmatch '(?m)^\s*flags=(?:[A-Z_]+\|)*SILENT(?:\|[A-Z_]+)*\r?$'
    $compatSilentGroup = $Record -match '(?m)^\s*groupKey=0\|com\.vpe_soft\.intime\.intime\|g:silent\r?$'
    if ($TestApi -le 33) {
        $flags = [regex]::Match($Record, '(?m)^\s*flags=0x([0-9a-fA-F]+)\r?$')
        if (-not $flags.Success -or ([Convert]::ToInt64($flags.Groups[1].Value, 16) -band 0x18) -ne 0x18 -or -not $compatSilentGroup) { throw "Quiet notification flags/group missing on API $TestApi" }
    } elseif ($TestApi -eq 35) {
        if (-not $nativeSilent -and -not $compatSilentGroup) { throw 'Quiet notification native flag or AndroidX silent group missing on API35' }
    } elseif (-not $nativeSilent) { throw 'Quiet notification native SILENT flag missing' }
}

function Assert-ReleasePeriodicReminder([string]$Description) {
    $dump = Get-Content (Join-Path $run 'notifications-delivered.log') -Raw -Encoding UTF8
    $pattern = '(?ms)^\s*NotificationRecord\([^\r\n]*pkg=com\.vpe_soft\.intime\.intime [^\r\n]*\r?\n(?:(?!^\s*NotificationRecord\().)*'
    $initial = [regex]::Match($dump, $pattern)
    # Старые платформы печатают только when, новые добавляют /postingTime.
    $first = [regex]::Match($initial.Value, '(?m)^\s*when=(\d+)(?:/|\r?$)')
    if (-not $initial.Success -or -not $first.Success -or -not $initial.Value.Contains("android.text=String ($Description)") -or $initial.Value -notmatch 'actions=1') { throw 'Initial alarm record with ACK missing' }
    $firstPost = [long]$first.Groups[1].Value
    $summary.periodicFirstAlarmWhen = $firstPost
    Invoke-ReleaseAdb @('shell', 'cmd', 'statusbar', 'collapse') | Out-Null
    Invoke-ReleaseAdb @('shell', 'input', 'keyevent', '3') | Out-Null
    $since = (Invoke-ReleaseAdb @('shell', "date '+%m-%d %H:%M:%S.000'")).Trim()
    $summary.periodicBatchingChanged = $false
    Set-Content (Join-Path $run 'periodic-jobs-before.log') (Invoke-ReleaseAdb @('shell', 'dumpsys', 'jobscheduler')) -Encoding UTF8
    if ((Get-Content (Join-Path $run 'periodic-jobs-before.log') -Raw) -notmatch 'com\.vpe_soft\.intime\.intime/androidx\.work\.impl\.background\.systemjob\.SystemJobService') { throw 'Release WorkManager platform job missing' }
    Invoke-ReleaseAdb @('shell', 'am', 'kill', $package) | Out-Null
    Start-Sleep -Seconds 2
    if ((Invoke-ReleaseAdb @('shell', "pidof $package || true")).Trim()) { throw 'Release process survived am kill' }
    $state = Invoke-ReleaseAdb @('shell', 'dumpsys', 'package', $package)
    Set-Content (Join-Path $run 'periodic-package-after-kill.log') $state -Encoding UTF8
    if ($state -notmatch 'User 0:.*stopped=false') { throw 'Release package stopped after kill' }
    $summary.periodicProcessAbsentAfterKill = $true
    $summary.periodicLiveJobDumps = $false
    Save-ReleasePeriodicDiagnostics 'before'
    $limit = (Get-Date).AddMinutes($PeriodicWaitMinutes)
    $timer = [Diagnostics.Stopwatch]::StartNew()
    $nextDiagnosticSeconds = 300
    $lastPollAt = [datetimeoffset]::UtcNow
    $observationError = $null
    $delivered = $false
    $posts = @()
    $previousPost = $firstPost
    Write-Output "Waiting for natural release worker (up to $PeriodicWaitMinutes minutes, unchanged batching)..."
    do {
        $pollAt = [datetimeoffset]::UtcNow
        try { Assert-ReleasePeriodicPollContinuity $lastPollAt $pollAt }
        catch { $observationError = $_.Exception.Message; break }
        $lastPollAt = $pollAt
        # Перезаписываем снимок опроса, чтобы длительное ожидание не раздувало диск.
        # Не вызываем UI/probe/Activity/instrumentation/ручной JobScheduler run.
        $notifications = Invoke-ReleaseTool $adb @('-s', $serial, 'shell', 'dumpsys', 'notification', '--noredact') 'periodic-notifications-poll.log'
        $record = [regex]::Match($notifications, $pattern)
        $when = [regex]::Match($record.Value, '(?m)^\s*when=(\d+)(?:/|\r?$)')
        # Один и тот же активный record не считается новым повтором. Каждый новый
        # when должен отстоять минимум на штатные 15 минут от предыдущей отправки.
        if ($record.Success -and $record.Value.Contains("android.text=String ($Description)") -and $when.Success -and [long]$when.Groups[1].Value -ge $previousPost + 900000) {
            Assert-ReleaseQuietNotification $record.Value
            $post = [long]$when.Groups[1].Value
            $posts += @{ when = $post; gapSeconds = ($post - $previousPost) / 1000.0 }
            $previousPost = $post
            # Сохраняем оба доказательства; остальные опросы перезаписываем.
            Set-Content (Join-Path $run ("periodic-notifications-repeat-{0}.log" -f $posts.Count)) $notifications -Encoding UTF8
            $summary.periodicObservedRepeats = $posts
            Write-Output ("Natural quiet repeat {0}/{1}, gap {2:N1} seconds" -f $posts.Count, $PeriodicRepeats, $posts[-1].gapSeconds)
            if ($posts.Count -eq $PeriodicRepeats) { $delivered = $true; break }
        }
        if ($timer.Elapsed.TotalSeconds -ge $nextDiagnosticSeconds) {
            Save-ReleasePeriodicDiagnostics 'latest'
            $nextDiagnosticSeconds = $timer.Elapsed.TotalSeconds + 300
        }
        Write-Output ("Worker wait: {0:N1} minutes" -f $timer.Elapsed.TotalMinutes)
        Start-Sleep -Seconds 30
    } while ((Get-Date) -lt $limit)
    if (-not $observationError) {
        try { Assert-ReleasePeriodicPollContinuity $lastPollAt ([datetimeoffset]::UtcNow) }
        catch { $observationError = $_.Exception.Message }
    }
    Save-ReleasePeriodicDiagnostics 'after'
    Set-Content (Join-Path $run 'periodic-jobs-after.log') (Invoke-ReleaseAdb @('shell', 'dumpsys', 'jobscheduler')) -Encoding UTF8
    $log = Invoke-ReleaseAdb @('logcat', '-d', '-T', $since)
    Set-Content (Join-Path $run 'periodic-logcat.log') $log -Encoding UTF8
    if ($observationError) { throw $observationError }
    if (-not $delivered) { throw 'Natural release worker notification timed out; inspect platform scheduling before blaming application' }
    Assert-ReleaseQuietNotification $record.Value
    # Logcat читаем только после всего окна. Для двух повторов нужны две записи
    # SUCCESS настоящего worker; одно уведомление/один SUCCESS нельзя засчитать дважды.
    $successes = [regex]::Matches($log, '(?m)^.*Worker result SUCCESS for Work[^\r\n]*TaskNotificationWorker[^\r\n]*$')
    if ($successes.Count -lt $PeriodicRepeats) { throw 'Actual WorkManager SUCCESS count is below requested repeats after process termination' }
    $summary.periodicWorkerSuccessCount = $successes.Count
    if ((Read-ReleaseAlarm) -ne 0) { throw 'Unexpected future alarm after overdue repeat' }
    $summary.periodicQuietNotificationWhen = [long]$when.Groups[1].Value
    $summary.periodicGapSeconds = ($summary.periodicQuietNotificationWhen - $firstPost) / 1000.0
    Complete-ReleasePhase 'release-periodic-quiet-after-process-exit'
    if ($PeriodicRepeats -eq 2) { Complete-ReleasePhase 'release-periodic-next-natural-period' }
    $summary.naturalDispatchValidated = $true
}

# Метаданные отдельного host-dispatch не запускают команды. Они только уточняют
# область фактической проверки: PASS после явного JobScheduler run нельзя выдавать
# за естественную доставку. Маркер без подтверждённого Running job отвергается.
# Применяется после сбора диагностики/очистки, не затрагивает APK или настройки ОС.
function Set-ReleaseDispatchAssessment([hashtable]$Result, [string]$RunDirectory) {
    $marker = Join-Path $RunDirectory 'periodic-dispatch-control.json'
    if (-not (Test-Path -LiteralPath $marker)) { return }
    $control = Get-Content -LiteralPath $marker -Raw -Encoding UTF8 | ConvertFrom-Json
    $output = Get-Content (Join-Path $RunDirectory 'periodic-controlled-run.log') -Raw -Encoding UTF8
    if ($control.ownJobCount -ne 1 -or "$($control.jobId)" -notmatch '^\d+$' -or $output -notmatch '(?m)^Running job\s*$') { throw 'Controlled dispatch evidence is incomplete' }
    $Result.periodicPlatformJobDispatched = $true
    $Result.naturalDispatchValidated = $false
    $Result.periodicDispatchCommand = 'cmd jobscheduler run -s -n androidx.work.systemjobscheduler com.vpe_soft.intime.intime ' + $control.jobId
    $Result.naturalObservationSeconds = (([datetimeoffset]$control.observedAt).ToUnixTimeMilliseconds() - [long]$Result.periodicFirstAlarmWhen) / 1000.0
    if ($Result.status -eq 'PASSED') { $Result.status = 'PASSED_CONTROLLED_PLATFORM_DISPATCH' }
}

# Повторно опрашиваем шторку до пяти секунд после срока alarm; между снимками
# UI есть паузы, поэтому кратковременное появление между ними не исключается.
# Дополнительно требуем исчезновения сработавшего alarm и запись receiver в logcat,
# чтобы отсутствие уведомления не стало ложным успехом при несработавшем будильнике.
# wasNotified в приватной Room этим black-box наблюдением не проверяется.
function Assert-ReleaseBlockedNotification([long]$Alarm, [string]$Description, [string]$Name) {
    $since = (Invoke-ReleaseAdb @('shell', "date '+%m-%d %H:%M:%S.000'")).Trim()
    Invoke-ReleaseAdb @('shell', 'cmd', 'statusbar', 'expand-notifications') | Out-Null
    $limit = (Get-Date).AddSeconds(150)
    $finished = $false
    do {
        $ui = Read-ReleaseShade
        if ($ui.SelectSingleNode("//node[@text='$Description']")) { throw "Blocked notification visible: $Name" }
        $now = [long](Invoke-ReleaseAdb @('shell', 'date', '+%s')).Trim() * 1000
        if ($now -ge $Alarm + 5000 -and (Read-ReleaseAlarm) -eq 0) { $finished = $true; break }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $limit)
    if (-not $finished) { throw "Blocked alarm did not finish: $Name" }
    $phaseLog = Invoke-ReleaseAdb @('logcat', '-d', '-T', $since)
    Set-Content (Join-Path $run "$Name-logcat.log") $phaseLog -Encoding UTF8
    if ($phaseLog -notmatch 'AlarmReceiver.*handleAlarm: trying to post notification') { throw "Receiver delivery not confirmed: $Name" }
    Set-Content (Join-Path $run "$Name-notifications.log") (Invoke-ReleaseAdb @('shell', 'dumpsys', 'notification', '--noredact')) -Encoding UTF8
    Invoke-ReleaseAdb @('shell', 'cmd', 'statusbar', 'collapse') | Out-Null
    Launch-Release
    Assert-ReleaseText $Description
}

# Восстановление доставки проверяем новым UI ACK существующей задачи: её описание
# остаётся в списке, но срок начинается заново. ID отдельно не сверяется. Это не доказательство сохранения
# wasNotified у уже просроченной задачи после снятия блокировки.
function Assert-ReleaseRecoveredNotification([string]$Description, [string]$Name) {
    $alarm = Prepare-ReleaseMinuteAlarm $Description
    Invoke-ReleaseAdb @('shell', 'cmd', 'statusbar', 'expand-notifications') | Out-Null
    $limit = (Get-Date).AddSeconds(150)
    $visible = $false
    do {
        $ui = Read-ReleaseShade
        $visible = $null -ne $ui.SelectSingleNode("//node[@text='$Description']")
        if (-not $visible) { Start-Sleep -Seconds 2 }
    } while (-not $visible -and (Get-Date) -lt $limit)
    if (-not $visible) { throw "Notification did not recover: $Name" }
    Set-Content (Join-Path $run "$Name-notifications.log") (Invoke-ReleaseAdb @('shell', 'dumpsys', 'notification', '--noredact')) -Encoding UTF8
    Click-ReleaseNode "//node[@text='Acknowledge' or @text='ACKNOWLEDGE']"
    $next = Wait-ReleaseAlarm $alarm
    $summary[$Name] = @{ deliveredAlarm = $alarm; acknowledgedAlarm = $next }
    Invoke-ReleaseAdb @('shell', 'cmd', 'statusbar', 'collapse') | Out-Null
    Launch-Release
    Assert-ReleaseText $Description
}

# API 31: меняем общий доступ через системный экран собственного приложения.
# В отличие от appops POST_NOTIFICATION, Settings меняет notification preferences.
# Требуем состояние главного переключателя и importance именно собственного пакета.
# Тест не проверяет permission dialog API 33+ и не трогает чужие приложения.
function Set-ReleaseAppNotificationsEnabled([bool]$Enabled) {
    Invoke-ReleaseAdb @('shell', 'am', 'start', '-W', '-a', 'android.settings.APP_NOTIFICATION_SETTINGS', '--es', 'android.provider.extra.APP_PACKAGE', $package) | Out-Null
    $xpath = "//node[(@resource-id='android:id/switch_widget' or @resource-id='com.android.settings:id/switch_widget') and @checkable='true']"
    $switch = Wait-ReleaseNode $xpath
    $expected = if ($Enabled) { 'true' } else { 'false' }
    if ($switch.GetAttribute('checked') -ne $expected) { Click-ReleaseNode $xpath }
    Wait-ReleaseNode ($xpath.TrimEnd(']') + " and @checked='$expected']") | Out-Null
    $dump = Invoke-ReleaseAdb @('shell', 'dumpsys', 'notification', '--noredact')
    $app = [regex]::Match($dump, '(?m)^\s*AppSettings: com\.vpe_soft\.intime\.intime \(\d+\)(?<details>[^\r\n]*)$')
    if (-not $app.Success) { throw 'Own app notification preferences not found' }
    # Android 12 опускает importance при возвращении к значению по умолчанию.
    # Наличие строки своего пакета обязательно; NONE/0 означает общий запрет.
    $blocked = $app.Groups['details'].Value -match '\bimportance=(NONE|0)(?:\s|$)'
    if ($blocked -eq $Enabled) { throw 'Own app notification importance does not match switch' }
    Set-Content (Join-Path $run ("app-notifications-$expected-settings.log")) $dump -Encoding UTF8
    Invoke-ReleaseAdb @('shell', 'input', 'keyevent', '3') | Out-Null
}

# Меняем только канал собственного production-пакета через системный экран.
# Должен измениться именно переключатель Show notifications; системные каналы
# и общий доступ приложения к уведомлениям этим действием не меняются.
function Set-ReleaseChannelEnabled([bool]$Enabled) {
    Invoke-ReleaseAdb @('shell', 'am', 'start', '-W', '-a', 'android.settings.CHANNEL_NOTIFICATION_SETTINGS', '--es', 'android.provider.extra.APP_PACKAGE', $package, '--es', 'android.provider.extra.CHANNEL_ID', 'DefaultChannelId') | Out-Null
    $xpath = "//node[@resource-id='android:id/switch_widget' and @checkable='true']"
    $switch = Wait-ReleaseNode $xpath
    $expected = if ($Enabled) { 'true' } else { 'false' }
    if ($switch.GetAttribute('checked') -ne $expected) { Click-ReleaseNode $xpath }
    Wait-ReleaseNode ($xpath.TrimEnd(']') + " and @checked='$expected']") | Out-Null
    $dump = Invoke-ReleaseAdb @('shell', 'dumpsys', 'notification', '--noredact')
    $app = [regex]::Match($dump, '(?ms)^\s*AppSettings: com\.vpe_soft\.intime\.intime \(\d+\)[^\r\n]*\r?\n(?<body>.*?)(?=^\s*AppSettings:|^Restored without uid:|\z)')
    $channel = [regex]::Match($app.Groups['body'].Value, "NotificationChannel\{mId='DefaultChannelId'[^\r\n]*?mImportance=(\d+),")
    if (-not $app.Success -or -not $channel.Success) { throw 'Own channel state not found in dumpsys' }
    $importance = [int]$channel.Groups[1].Value
    if (($Enabled -and $importance -eq 0) -or (-not $Enabled -and $importance -ne 0)) { throw 'Own channel importance does not match switch' }
    Set-Content (Join-Path $run ("channel-$expected-settings.log")) $dump -Encoding UTF8
    Invoke-ReleaseAdb @('shell', 'input', 'keyevent', '3') | Out-Null
}

try {
    $lock = [IO.File]::Open((Join-Path $work 'run.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
    $state = Get-Content (Join-Path $work 'emulator.json') -Raw | ConvertFrom-Json
    $process = Get-Process -Id $state.processId -ErrorAction Stop
    if ($state.serial -ne $serial -or $process.StartTime.ToUniversalTime().ToString('o') -ne $state.started) { throw 'Emulator ownership mismatch' }
    $name = Invoke-ReleaseAdb @('emu', 'avd', 'name')
    if ($name.Split("`n")[0].Trim() -ne "Intime_Test_API$TestApi") { throw 'Refusing another AVD' }
    if ((Invoke-ReleaseAdb @('shell', 'getprop', 'ro.build.version.sdk')).Trim() -ne "$TestApi") { throw "Expected API $TestApi" }
    if ((Invoke-ReleaseAdb @('shell', 'dumpsys', 'window')) -match 'Application Not Responding:') { throw 'System ANR dialog is present' }
    $ApkPath = (Resolve-Path -LiteralPath $ApkPath).Path
    $tools = Join-Path $sdk 'build-tools\35.0.0'
    $env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
    $badging = (& (Join-Path $tools 'aapt.exe') dump badging $ApkPath) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw 'APK badging failed' }
    Assert-ReleaseApkIdentity $badging $ExpectedVersionCode $ExpectedVersionName
    Set-Content (Join-Path $run 'apk-badging.log') $badging -Encoding UTF8
    $signing = (& (Join-Path $tools 'apksigner.bat') verify --print-certs $ApkPath 2>&1) -join "`n"
    if ($LASTEXITCODE -ne 0 -or $signing -notmatch '97b60110a8e894791b4770826a8d214dc5e3a551cd24bd41d646c29f64a1dd26') { throw 'APK upload signature mismatch' }
    Set-Content (Join-Path $run 'apk-signing.log') $signing -Encoding UTF8
    $summary.apkSha256 = (Get-FileHash $ApkPath -Algorithm SHA256).Hash
    $summary.apk = $ApkPath
    # Mapping должен сопровождать APK из того же сохранённого build-прогона.
    # Проверяем, что R8 действительно переименовал release-репозиторий; одной
    # отметки non-debuggable недостаточно для утверждения о проверке R8.
    $mappingPath = Join-Path (Split-Path $ApkPath -Parent) 'mapping.txt'
    $mapping = Get-Content $mappingPath -Raw
    $renamed = [regex]::Match($mapping, '(?m)^com\.vpe_soft\.intime\.intime\.database\.repositories\.TaskRepository -> ([^:]+):')
    if (-not $renamed.Success -or $renamed.Groups[1].Value -eq 'com.vpe_soft.intime.intime.database.repositories.TaskRepository') { throw 'Expected R8 repository renaming in accompanying mapping.txt' }
    $summary.mappingSha256 = (Get-FileHash $mappingPath -Algorithm SHA256).Hash
    # Компилируем отдельный shell-probe SDK-инструментами. Legacy UIAutomator
    # используется только для свежего XML без idle timeout: зависимости и хуки
    # в release APK не добавляются. Сам CRUD проверяется ниже по реальному UI.
    $classes = Join-Path $run 'probe-classes'
    $dex = Join-Path $run 'probe-dex'
    New-Item -ItemType Directory -Path $classes, $dex -Force | Out-Null
    $platform = Join-Path $sdk 'platforms\android-36'
    $junit = Get-ChildItem (Join-Path ([Environment]::GetFolderPath('UserProfile')) '.gradle\caches\modules-2\files-2.1\junit\junit\4.13.2') -Recurse -Filter 'junit-4.13.2.jar' | Select-Object -First 1
    if (-not $junit) { throw 'JUnit compile dependency missing; run test.ps1 bootstrap first' }
    Invoke-ReleaseTool (Join-Path $env:JAVA_HOME 'bin\javac.exe') @('-encoding', 'UTF-8', '--release', '8', '-classpath', "$(Join-Path $platform 'android.jar');$(Join-Path $platform 'uiautomator.jar');$($junit.FullName)", '-d', $classes, (Join-Path $PSScriptRoot 'android\ReleaseHierarchyProbe.java')) 'probe-compile.log' | Out-Null
    Invoke-ReleaseTool (Join-Path $tools 'd8.bat') @('--min-api', '24', '--output', $dex, (Join-Path $classes 'intime\smoke\ReleaseHierarchyProbe.class')) 'probe-dex.log' | Out-Null
    $probeJar = Join-Path $run 'intime-release-probe.jar'
    Invoke-ReleaseTool (Join-Path $env:JAVA_HOME 'bin\jar.exe') @('cf', $probeJar, '-C', $dex, 'classes.dex') 'probe-package.log' | Out-Null
    Invoke-ReleaseAdb @('push', $probeJar, '/data/local/tmp/intime-release-probe.jar') | Out-Null
    $probePushed = $true
    # pm path возвращает exit 1 для отсутствующего пакета. Для проверки отсутствия
    # используем успешный list packages и точное совпадение, исключая соседний .dev.
    if ((Invoke-ReleaseAdb @('shell', 'pm', 'list', 'packages', $package)) -match '(?m)^package:com\.vpe_soft\.intime\.intime\r?$') { throw 'Production package already exists; refusing to replace or clear it' }
    Invoke-ReleaseAdb @('logcat', '-c') | Out-Null # Начало журнала только этого smoke на тестовом AVD.
    Invoke-ReleaseAdb @('install', $ApkPath) | Out-Null
    $installedHere = $true
    # На API 31 runtime POST_NOTIFICATIONS отсутствует; начальная установка
    # получает обычный доступ к уведомлениям без команды pm grant.
    if ($TestApi -ge 33) { Invoke-ReleaseAdb @('shell', 'pm', 'grant', $package, 'android.permission.POST_NOTIFICATIONS') | Out-Null }
    Launch-Release

    # Проверка создания: чистая release-установка, разрешение уведомлений выдано.
    # Через форму вводим одну задачу с периодом час; список должен показать описание,
    # а AlarmManager — действующий будильник. Этим проверяется реальный Room/репозиторий
    # после R8, без in-memory базы и без вызова внутренних методов приложения.
    $description = 'R8-Smoke-' + (Get-Date -Format 'HHmmss')
    Click-ReleaseId 'fab_add_task'
    Focus-ReleaseDescription
    Invoke-ReleaseAdb @('shell', 'input', 'text', $description) | Out-Null
    Wait-ReleaseNode "//node[@resource-id='${package}:id/edit_task_description' and @text='$description']" | Out-Null
    Close-ReleaseKeyboard
    Click-ReleaseId 'spinner_interval'
    Click-ReleaseNode "//node[@text='Час']"
    Click-ReleaseId 'btn_save_task'
    Wait-ReleaseNode "//node[@resource-id='${package}:id/textDescription' and @text='$description']" | Out-Null
    $beforeAck = Wait-ReleaseAlarm
    Complete-ReleasePhase 'create-and-alarm'

    # Проверка редактирования: открываем созданную строку и форму Edit, дописываем
    # суффикс. После сохранения старое описание заменяется новым; повторный запуск
    # после force-stop должен прочитать это же значение из файловой Room.
    Open-ReleaseDetails $description
    Open-ReleaseEdit
    Focus-ReleaseDescription
    Invoke-ReleaseAdb @('shell', 'input', 'keyevent', 'KEYCODE_MOVE_END') | Out-Null
    Invoke-ReleaseAdb @('shell', 'input', 'text', '-Edited') | Out-Null
    Wait-ReleaseNode "//node[@resource-id='${package}:id/edit_task_description' and @text='$description-Edited']" | Out-Null
    Close-ReleaseKeyboard
    Click-ReleaseId 'btn_save_task'
    $edited = "$description-Edited"
    Assert-ReleaseText $edited
    Click-ReleaseId 'btn_back_to_main'
    Invoke-ReleaseAdb @('shell', 'am', 'force-stop', $package) | Out-Null
    Launch-Release
    Assert-ReleaseText $edited
    Complete-ReleasePhase 'edit-and-process-restart'

    # Проверка ACK: подтверждаем сохранённую задачу кнопкой деталей. Она остаётся
    # в списке, а срок действующего будильника должен стать позднее исходного.
    # Это проверяет прохождение ACK через release-репозиторий и пересчёт расписания;
    # доставку уведомления и действие ACK из шторки эта фаза не проверяет.
    # Снимок срока берём после Edit/restart, иначе перенос при редактировании
    # мог бы дать ложный успех даже при неработающем ACK.
    $beforeAck = Wait-ReleaseAlarm
    Open-ReleaseDetails $edited
    Click-ReleaseId 'btnAckTask'
    Assert-ReleaseText $edited
    $afterAck = Wait-ReleaseAlarm $beforeAck
    $summary.alarmBeforeAck = $beforeAck
    $summary.alarmAfterAck = $afterAck
    Complete-ReleasePhase 'ack-and-reschedule'

    # Проверка настроек и Back: экран с DataBinding должен показать версию кандидата;
    # системный Back возвращает список с задачей. Внешние настройки ОС не изменяем.
    Click-ReleaseId 'btn_open_settings'
    Wait-ReleaseNode "//node[contains(@text, '$ExpectedVersionName')]" | Out-Null
    Invoke-ReleaseAdb @('shell', 'input', 'keyevent', '4') | Out-Null
    Assert-ReleaseText $edited
    Complete-ReleasePhase 'settings-and-system-back'

    # Проверка экспорта: после ACK существует одна отредактированная задача.
    # Сохраняем JSON через настоящий системный SAF picker в Downloads, читаем
    # только созданный нами файл и проверяем версию, порядок восьми колонок,
    # описание и совпадение next_alarm с действующим AlarmManager. Это проверяет
    # release-сериализацию и запись ContentResolver; чужие файлы не читаются.
    $backupName = 'intime-r8-' + [guid]::NewGuid().ToString('N') + '.json'
    $backupDevicePath = '/sdcard/Download/' + $backupName
    Click-ReleaseId 'btn_open_settings'
    Click-ReleaseId 'export_to_json_btn'
    # После открытия SAF поле имени существует, но первый tap может потеряться
    # во время раскладки системного picker. Повторяем tap только по его android:id/title
    # до подтверждённого фокуса, не вводя символы в другой элемент. Лимит 30 секунд;
    # далее проверка точного имени и реального JSON остаётся обязательной.
    $nameFocusLimit = (Get-Date).AddSeconds(30)
    do {
        $nameField = Wait-ReleaseNode "//node[@class='android.widget.EditText' and @resource-id='android:id/title']"
        if ($nameField.GetAttribute('focused') -eq 'true') { break }
        Click-ReleaseNode "//node[@class='android.widget.EditText' and @resource-id='android:id/title']"
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $nameFocusLimit)
    if ($nameField.GetAttribute('focused') -ne 'true') { throw 'SAF backup filename did not gain focus' }
    # API 31 интерпретирует shell keycombination Ctrl+A как ввод буквы a.
    # Вместо него читаем текущее имя, переводим курсор в конец и удаляем символы
    # обычными DEL. Перед Save требуем точное новое имя по свежему XML.
    Invoke-ReleaseAdb @('shell', 'input', 'keyevent', 'KEYCODE_MOVE_END') | Out-Null
    $nameLength = $nameField.GetAttribute('text').Length
    if ($nameLength -gt 0) { Invoke-ReleaseAdb (@('shell', 'input', 'keyevent') + (@('KEYCODE_DEL') * $nameLength)) | Out-Null }
    Invoke-ReleaseAdb @('shell', 'input', 'text', $backupName) | Out-Null
    Wait-ReleaseNode "//node[@class='android.widget.EditText' and @text='$backupName']" | Out-Null
    Click-ReleaseNode "//node[@resource-id='android:id/button1']"
    Wait-ReleaseNode "//node[@resource-id='${package}:id/export_to_json_btn']" | Out-Null
    $backupLocalPath = Join-Path $run $backupName
    Invoke-ReleaseAdb @('pull', $backupDevicePath, $backupLocalPath) | Out-Null
    $backup = Get-Content $backupLocalPath -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($backup.meta.version -ne 1 -or ($backup.tables.tasks.columns -join ',') -ne 'id,description,interval,amount,next_alarm,next_caution,last_ack,quant') { throw 'Invalid exported JSON contract' }
    $rows = @($backup.tables.tasks.rows)
    if ($rows.Count -ne 1 -or $rows[0].Count -ne 8 -or $rows[0][1] -ne $edited -or [long]$rows[0][4] -ne $afterAck) { throw 'Exported task does not match UI/alarm' }
    $summary.exportSha256 = (Get-FileHash $backupLocalPath -Algorithm SHA256).Hash
    Invoke-ReleaseAdb @('shell', 'input', 'keyevent', '4') | Out-Null
    Complete-ReleasePhase 'json-export'

    # Проверка удаления: нажимаем Delete и подтверждаем диалог. Должно появиться
    # пустое состояние, которое сохраняется после нового запуска процесса;
    # будильник единственной удалённой задачи должен быть отменён.
    Open-ReleaseDetails $edited
    Click-ReleaseId 'btnDeleteTask'
    Click-ReleaseNode "//node[@resource-id='android:id/button1']"
    Wait-ReleaseNode "//node[@resource-id='${package}:id/emptyStateAddTaskBtn']" | Out-Null
    Invoke-ReleaseAdb @('shell', 'am', 'force-stop', $package) | Out-Null
    Launch-Release
    Wait-ReleaseNode "//node[@resource-id='${package}:id/emptyStateAddTaskBtn']" | Out-Null
    if ((Read-ReleaseAlarm) -ne 0) { throw 'Deleted task still has an active alarm' }
    Complete-ReleasePhase 'delete-and-empty-restart'

    # Проверка импорта: список пуст после удаления и перезапуска процесса.
    # Выбираем собственный экспорт через SAF, затем проверяем восстановленное
    # описание и точно прежний срок alarm, включая повторный запуск приложения.
    # Таким образом данные проходят настоящий release parser/Room/ContentResolver;
    # ошибочные файлы и откат транзакции эта фаза пока не моделирует.
    Click-ReleaseId 'btn_open_settings'
    Click-ReleaseId 'import_from_json_btn'
    Click-ReleaseNode "//node[@text='$backupName']"
    Wait-ReleaseNode "//node[@resource-id='${package}:id/import_from_json_btn']" | Out-Null
    Invoke-ReleaseAdb @('shell', 'input', 'keyevent', '4') | Out-Null
    Assert-ReleaseText $edited
    $restoredAlarm = Wait-ReleaseAlarm
    if ($restoredAlarm -ne $afterAck) { throw 'Imported alarm deadline changed' }
    Invoke-ReleaseAdb @('shell', 'am', 'force-stop', $package) | Out-Null
    Launch-Release
    Assert-ReleaseText $edited
    if ((Wait-ReleaseAlarm) -ne $afterAck) { throw 'Imported alarm not restored after process restart' }
    $summary.importedAlarm = $restoredAlarm
    Complete-ReleasePhase 'json-import-and-restart'

    if ($HistoricalJsonImport) {
        # Исходные условия: одна наша синтетическая задача восстановлена штатным
        # импортом. Берём точную форму meta из реального синтетического экспорта
        # Play 25; строки заменяем собственным текущим экспортом, чтобы срок alarm
        # не зависел от даты создания исторической фикстуры. Проверяем форму файла
        # и неизменность всех восьми значений на стороне host перед SAF-импортом.
        $historicalFixture = Get-Content (Join-Path $root 'app/src/sharedTest/resources/play25-backup.json') -Raw -Encoding UTF8 | ConvertFrom-Json
        if ($historicalFixture.meta.backup_version -ne 1 -or $historicalFixture.meta.db_version -ne 5 -or $historicalFixture.meta.app -ne $package -or ($historicalFixture.tables.tasks.columns -join ',') -ne ($backup.tables.tasks.columns -join ',')) { throw 'Unexpected Play25 fixture contract' }
        $historical = @{ meta = $historicalFixture.meta; tables = $backup.tables }
        $historicalPath = Join-Path $run 'historical-import.json'
        # JSON записываем без BOM, как штатный экспорт; BOM нужен только PS1
        # с русскими комментариями для Windows PowerShell 5.1.
        [IO.File]::WriteAllText($historicalPath, ($historical | ConvertTo-Json -Depth 10), (New-Object System.Text.UTF8Encoding($false)))
        $roundTrip = Get-Content $historicalPath -Raw -Encoding UTF8 | ConvertFrom-Json
        if ($roundTrip.meta.PSObject.Properties.Name -contains 'version') { throw 'Historical import contains current version marker' }
        if (($roundTrip.tables.tasks.rows | ConvertTo-Json -Depth 10 -Compress) -ne ($backup.tables.tasks.rows | ConvertTo-Json -Depth 10 -Compress)) { throw 'Historical fixture task fields changed' }
        $summary.historicalImportSha256 = (Get-FileHash $historicalPath -Algorithm SHA256).Hash
        $summary.historicalSourceFixtureSha256 = (Get-FileHash (Join-Path $root 'app/src/sharedTest/resources/play25-backup.json') -Algorithm SHA256).Hash
        # Удаляем только собственную задачу через UI и требуем пустой список с
        # отменённым alarm. Без этого отказ parser мог бы скрыться за старой строкой.
        Open-ReleaseDetails $edited
        Click-ReleaseId 'btnDeleteTask'
        Click-ReleaseNode "//node[@resource-id='android:id/button1']"
        Wait-ReleaseNode "//node[@resource-id='${package}:id/emptyStateAddTaskBtn']" | Out-Null
        if ((Read-ReleaseAlarm) -ne 0) { throw 'Historical import setup still has active alarm' }
        Invoke-ReleaseAdb @('push', $historicalPath, $backupDevicePath) | Out-Null
        Click-ReleaseId 'btn_open_settings'
        Click-ReleaseId 'import_from_json_btn'
        Click-ReleaseNode "//node[@text='$backupName']"
        Wait-ReleaseNode "//node[@resource-id='${package}:id/import_from_json_btn']" | Out-Null
        Invoke-ReleaseAdb @('shell', 'input', 'keyevent', '4') | Out-Null
        Assert-ReleaseText $edited
        if ((Wait-ReleaseAlarm) -ne $afterAck) { throw 'Historical import alarm changed' }
        Invoke-ReleaseAdb @('shell', 'am', 'force-stop', $package) | Out-Null
        Launch-Release
        Assert-ReleaseText $edited
        if ((Wait-ReleaseAlarm) -ne $afterAck) { throw 'Historical import alarm missing after restart' }
        # Реальный release/R8 parser, SAF, Room и restart проверены по описанию и
        # alarm. Эта UI-фаза не читает private DB и не подтверждает все поля Room;
        # полный контроль восьми полей выполняют отдельные JVM/native Room тесты.
        Complete-ReleasePhase 'historical-json-import-and-restart'
    }

    # Проверка BootReceiver: импортированная часовая задача сохранена, приложение
    # запускалось и не находится в stopped. Уходим Home, записываем точный срок
    # alarm и boot_count, выполняем настоящий reboot только принадлежащего нам AVD.
    # После нового boot_count ждём тот же alarm ДО запуска Activity: восстановить
    # его должен release BootReceiver, а не побочный эффект открытия приложения.
    # Просрочку во время выключения и boot-уведомление эта фаза не моделирует.
    if($GestureOnly){Test-ReleaseEdgeGestures $edited $afterAck}
    if(-not $GestureOnly){
    for ($bootIteration = 1; $bootIteration -le $RebootRepeats; $bootIteration++) {
    # Каждый повтор воспроизводит force-stop/запуск, а не только reboot уже стабильного
    # пакета. Проверяем сохранённое описание и срок до Home; после reboot Activity
    # не открывается до проверки alarm. Не читаем private DB и не вызываем receiver вручную.
    if ($bootIteration -gt 1) {
        Invoke-ReleaseAdb @('shell', 'am', 'force-stop', $package) | Out-Null
        Launch-Release
        Assert-ReleaseText $edited
        if ((Wait-ReleaseAlarm) -ne $afterAck) { throw 'Alarm changed before repeated reboot' }
    }
    $bootPrefix = "reboot-$bootIteration"
    Invoke-ReleaseAdb @('shell', 'input', 'keyevent', '3') | Out-Null
    $bootBefore = [int](Invoke-ReleaseAdb @('shell', 'settings', 'get', 'global', 'boot_count')).Trim()
    $expectedBootAlarm = Wait-ReleaseAlarm
    # API 31/35/36: после force-stop/запуска оставляем ОС время зафиксировать
    # состояние пакета перед reboot. На35 stopped=true после reboot повторился
    # даже после успешного ожидания обеих очередей PackageManager. Пауза даёт
    # время отложенным операциям, но не доказывает причину сбоя или запись всех
    # файлов. Не снимаем stopped принудительно и не вызываем receiver вручную.
    # Отдельно отмечаем реально применённую паузу: наличие параметра в summary
    # само по себе не означает, что данный API выполнял ожидание.
    $summary.rebootSettleAppliedSeconds = 0
    if ($TestApi -in @(31, 35, 36) -and $RebootSettleSeconds -gt 0) {
        Start-Sleep -Seconds $RebootSettleSeconds
        $summary.rebootSettleAppliedSeconds = $RebootSettleSeconds
    }
    # API 35/36: после force-stop/повторного запуска наблюдалось
    # stopped=false до reboot и stopped=true после него, без вызова BootReceiver.
    # Дожидаемся штатных очередей PackageManager перед reboot, чтобы отделить
    # подготовку host от проверки восстановления. Сначала проверяем наличие
    # команд в справке именно этого AVD. Они не снимают stopped принудительно
    # и не вызывают receiver; неподдерживаемый формат не скрываем.
    # Ожидание очередей не доказывает причину прежнего сбоя или запись всех файлов.
    if ($TestApi -ge 35) {
        $packageHelp = Invoke-ReleaseAdb @('shell', 'cmd', 'package', 'help')
        Set-Content (Join-Path $run "$bootPrefix-package-help.log") $packageHelp -Encoding UTF8
        if ($packageHelp -notmatch '(?m)^\s*wait-for-handler\b' -or $packageHelp -notmatch '(?m)^\s*wait-for-background-handler\b') { throw 'PackageManager queue wait commands unavailable on this AVD' }
        Set-Content (Join-Path $run "$bootPrefix-package-handler-wait.log") (Invoke-ReleaseAdb @('shell', 'cmd', 'package', 'wait-for-handler', '--timeout', '30000')) -Encoding UTF8
        Set-Content (Join-Path $run "$bootPrefix-package-background-handler-wait.log") (Invoke-ReleaseAdb @('shell', 'cmd', 'package', 'wait-for-background-handler', '--timeout', '30000')) -Encoding UTF8
        $summary.packageManagerHandlersWaited = $true
    }
    $beforeBootPackage = Invoke-ReleaseAdb @('shell', 'dumpsys', 'package', $package)
    Set-Content (Join-Path $run 'package-before-reboot.log') $beforeBootPackage -Encoding UTF8
    Set-Content (Join-Path $run "$bootPrefix-package-before.log") $beforeBootPackage -Encoding UTF8
    if ($beforeBootPackage -notmatch 'User 0:.*stopped=false') { throw 'Release package stopped before reboot' }
    Set-Content (Join-Path $run 'logcat-before-reboot.log') (Invoke-ReleaseAdb @('logcat', '-d')) -Encoding UTF8
    Copy-Item -LiteralPath (Join-Path $run 'logcat-before-reboot.log') -Destination (Join-Path $run "$bootPrefix-logcat-before.log")
    if ((Get-Content (Join-Path $run "$bootPrefix-logcat-before.log") -Raw) -match 'ANR in|FATAL EXCEPTION') { throw 'ANR or fatal exception before repeated reboot' }
    Invoke-ReleaseAdb @('reboot') | Out-Null
    Write-Output 'Waiting for owned AVD reboot...'
    $bootLimit = (Get-Date).AddSeconds(300)
    $bootReady = $false
    do {
        try {
            $bootFlag = (Invoke-ReleaseAdb @('shell', 'getprop', 'sys.boot_completed')).Trim()
            $bootAfter = [int](Invoke-ReleaseAdb @('shell', 'settings', 'get', 'global', 'boot_count')).Trim()
            $bootReady = $bootFlag -eq '1' -and $bootAfter -gt $bootBefore
        } catch { $bootReady = $false }
        if (-not $bootReady) { Start-Sleep -Seconds 2 }
    } while (-not $bootReady -and (Get-Date) -lt $bootLimit)
    if (-not $bootReady) { throw 'Owned AVD reboot timed out' }
    $bootAlarmLimit = (Get-Date).AddSeconds(180)
    do {
        $bootAlarm = Read-ReleaseAlarm
        if ($bootAlarm -eq $expectedBootAlarm) { break }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $bootAlarmLimit)
    Set-Content (Join-Path $run 'package-after-reboot.log') (Invoke-ReleaseAdb @('shell', 'dumpsys', 'package', $package)) -Encoding UTF8
    Copy-Item -LiteralPath (Join-Path $run 'package-after-reboot.log') -Destination (Join-Path $run "$bootPrefix-package-after.log")
    # Сохраняем broadcast history и logcat также при успехе: сравнение только
    # неуспешных запусков не показывает, доставлялся ли BOOT_COMPLETED в норме.
    Set-Content (Join-Path $run "$bootPrefix-broadcasts-after.log") (Invoke-ReleaseAdb @('shell', 'dumpsys', 'activity', 'broadcasts')) -Encoding UTF8
    $bootLog = Invoke-ReleaseAdb @('logcat', '-d')
    Set-Content (Join-Path $run "$bootPrefix-logcat-after.log") $bootLog -Encoding UTF8
    $summary.rebootResults += @{ iteration = $bootIteration; bootBefore = $bootBefore; bootAfter = $bootAfter; expectedAlarm = $expectedBootAlarm; actualAlarm = $bootAlarm; receiverLogged = [bool]($bootLog -match 'BootReceiver.*onReceive'); restored = ($bootAlarm -eq $expectedBootAlarm) }
    if ($bootLog -match 'ANR in|FATAL EXCEPTION') { throw 'ANR or fatal exception during repeated reboot' }
    if ($bootAlarm -ne $expectedBootAlarm) {
        Set-Content (Join-Path $run 'broadcasts-after-reboot.log') (Invoke-ReleaseAdb @('shell', 'dumpsys', 'activity', 'broadcasts')) -Encoding UTF8
        throw 'Release BootReceiver did not restore the same alarm'
    }
    if ((Invoke-ReleaseAdb @('shell', 'dumpsys', 'window')) -match 'Application Not Responding:') { throw 'ANR dialog after reboot' }
    $summary.bootCountBefore = $bootBefore
    $summary.bootCountAfter = $bootAfter
    $summary.bootRestoredAlarm = $bootAlarm
    $bootPhase = if ($RebootOnly) { "release-reboot-recovery-$bootIteration" } else { 'release-reboot-recovery' }
    Complete-ReleasePhase $bootPhase
    }

    }
    if (-not $RebootOnly -and -not $GestureOnly) {
    # Проверка реальной доставки: задаём appops только нашей новой установке,
    # меняем период задачи через UI на минуту, подтверждаем
    # её в деталях для нового отсчёта от текущего ACK и уходим Home.
    # Ждём наступления срока и появления её уникального описания в системной шторке.
    # Receiver не вызывается вручную, время ОС не меняется, приватная база не читается.
    # InexactOnly сохраняет запрет exact; остальные режимы проверяют разрешённый exact.
    $inexactSince = (Invoke-ReleaseAdb @('shell', "date '+%m-%d %H:%M:%S.000'")).Trim()
    Invoke-ReleaseAdb @('shell', 'appops', 'set', $package, 'SCHEDULE_EXACT_ALARM', $(if ($InexactOnly) { 'deny' } else { 'allow' })) | Out-Null
    if ($DozeQuotaSeven) {
        $summary.quotaTasks=@(Prepare-ReleaseHourlyQuotaTasks $edited)
        if($summary.quotaTasks.Count -ne 8){throw 'Hourly quota fixture must contain eight tasks'}
        $edited=$summary.quotaTasks[0].description
        $summary.sequenceSecondDescription=$summary.quotaTasks[7].description
        $summary.sequenceSecondDeadline=$summary.quotaTasks[7].deadline
        $notificationDeadline=$summary.quotaTasks[0].deadline
        Complete-ReleasePhase 'release-doze-quota-seven-preparation'
    } elseif ($DozeSequence) {
        # Пока исходная задача имеет часовой период, создаём вторую на пять минут:
        # её срок сейчас ближайший, поэтому читаем его непосредственно из AlarmManager.
        # Вводим amount только в NumberPicker этой формы; перед Save требуем 5.
        Launch-Release
        $secondDescription = "$edited-Second"
        Click-ReleaseId 'fab_add_task'
        Focus-ReleaseDescription
        Invoke-ReleaseAdb @('shell', 'input', 'text', $secondDescription) | Out-Null
        Close-ReleaseKeyboard
        Click-ReleaseId 'spinner_interval'
        Click-ReleaseNode "//node[@text='Минута']"
        # Accessibility NumberPicker не экспортирует ID контейнера на API 31.
        # В этой форме два picker: amount первым, quant вторым; проверяем структуру.
        if ((Read-ReleaseUi).SelectNodes("//node[@class='android.widget.NumberPicker']").Count -ne 2) { throw 'Unexpected number picker structure' }
        $amountXpath="(//node[@class='android.widget.NumberPicker'])[1]//node[@resource-id='android:id/numberpicker_input']"
        Click-ReleaseNode $amountXpath
        Wait-ReleaseNode ($amountXpath + "[@focused='true']") | Out-Null
        Invoke-ReleaseAdb @('shell', 'input', 'keyevent', 'KEYCODE_MOVE_END', 'KEYCODE_DEL') | Out-Null
        Invoke-ReleaseAdb @('shell', 'input', 'text', '5') | Out-Null
        Invoke-ReleaseAdb @('shell', 'input', 'keyevent', 'KEYCODE_ENTER') | Out-Null
        Close-ReleaseKeyboard
        Wait-ReleaseNode ($amountXpath + "[@text='5']") | Out-Null
        Click-ReleaseId 'btn_save_task'
        Assert-ReleaseText $secondDescription
        $summary.sequenceSecondDeadline = Wait-ReleaseAlarm
        $secondNow = [long](Invoke-ReleaseAdb @('shell', 'date', '+%s')).Trim() * 1000
        if ($summary.sequenceSecondDeadline - $secondNow -lt 240000 -or $summary.sequenceSecondDeadline - $secondNow -gt 360000) { throw 'Five-minute second task deadline not confirmed' }
        $summary.sequenceSecondDescription = $secondDescription
        Complete-ReleasePhase 'release-doze-sequence-preparation'
    }
    if(-not $DozeQuotaSeven) {
    Launch-Release
    Assert-ReleaseText $edited
    Open-ReleaseDetails $edited
    Open-ReleaseEdit
    Click-ReleaseId 'spinner_interval'
    Click-ReleaseNode "//node[@text='Минута']"
    if($LightIdleOnly){
        # Подбираем срок синтетической задачи относительно ранее наблюдавшегося
        # light entry; системные таймеры не ускоряем. Amount задаём именно в Edit.
        $minutes=if($TestApi -eq 31){4}else{8}
        if((Read-ReleaseUi).SelectNodes("//node[@class='android.widget.NumberPicker']").Count -ne 2){throw 'Unexpected light fixture pickers'}
        $amountXpath="(//node[@class='android.widget.NumberPicker'])[1]//node[@resource-id='android:id/numberpicker_input']"
        Click-ReleaseNode $amountXpath
        Wait-ReleaseNode ($amountXpath+"[@focused='true']")|Out-Null
        Invoke-ReleaseAdb @('shell','input','keyevent','KEYCODE_MOVE_END','KEYCODE_DEL')|Out-Null
        Invoke-ReleaseAdb @('shell','input','text',"$minutes")|Out-Null
        Invoke-ReleaseAdb @('shell','input','keyevent','KEYCODE_ENTER')|Out-Null
        Close-ReleaseKeyboard
        Wait-ReleaseNode ($amountXpath+"[@text='$minutes']")|Out-Null
        $summary.lightFixtureMinutes=$minutes
    }
    Click-ReleaseId 'btn_save_task'
    Click-ReleaseId 'btnAckTask'
    Assert-ReleaseText $edited
    $notificationDeadline = Wait-ReleaseAlarm
    if($LightIdleOnly){
        $fixtureNow=[long](Invoke-ReleaseAdb @('shell','date','+%s')).Trim()*1000
        if([math]::Abs(($notificationDeadline-$fixtureNow)-$summary.lightFixtureMinutes*60000) -gt 15000){throw 'Light fixture interval not confirmed'}
        Complete-ReleasePhase 'release-natural-light-fixture'
    }
    if ($DozeSequence -and $summary.sequenceSecondDeadline -le $notificationDeadline + 60000) { throw 'Second task deadline is too close to first alarm' }
    }
    $summary.notificationAlarm = $notificationDeadline
    Write-Output "Waiting for real AlarmManager notification (deadline $notificationDeadline)..."
    Invoke-ReleaseAdb @('shell', 'input', 'keyevent', '3') | Out-Null
    if ($InexactOnly -and -not $DozeOnly -and -not $LightIdleOnly) { Assert-ReleaseInexactDelivery $edited $notificationDeadline $inexactSince }
    if ($DozeOnly) { Assert-ReleaseDozeDelivery $edited $notificationDeadline $inexactSince }
    if ($LightIdleOnly) { Assert-ReleaseLightDelivery $edited $notificationDeadline $inexactSince }
    if ($DozeSequence) {
        # Шторка теперь показывает последнюю задачу последовательности:
        # вторую либо восьмую. Её ACK проверяем штатным путём.
        $edited = $summary.sequenceSecondDescription
        $notificationDeadline = $summary.sequenceSecondDeadline
    }
    $notificationDisplayText=if($DozeQuotaSeven){$summary.quotaEighthNotificationText}else{$edited}
    $summary.notificationAckTaskDescription=$edited
    Invoke-ReleaseAdb @('shell', 'cmd', 'statusbar', 'expand-notifications') | Out-Null
    $notificationLimit = (Get-Date).AddSeconds(150)
    $notificationVisible = $false
    do {
        if($DozeQuotaSeven){Assert-ReleaseQuotaNotification $summary.quotaTasks[7] $summary.quotaEighthDispatchWhen; $notificationDisplayText=$summary.quotaEighthNotificationText}
        $notificationUi = Read-ReleaseShade
        $notificationVisible = $null -ne $notificationUi.SelectSingleNode("//node[@text='$notificationDisplayText']")
        if (-not $notificationVisible) { Start-Sleep -Seconds 2 }
    } while (-not $notificationVisible -and (Get-Date) -lt $notificationLimit)
    if (-not $notificationVisible) { throw 'Release task notification not delivered to shade' }
    Set-Content (Join-Path $run 'notifications-delivered.log') (Invoke-ReleaseAdb @('shell', 'dumpsys', 'notification', '--noredact')) -Encoding UTF8
    Complete-ReleasePhase 'release-notification-delivery'

    if ($PeriodicOnly) {
        Assert-ReleasePeriodicReminder $edited
    } else {

    # Проверка ACK из настоящего уведомления: нажимаем системную action Acknowledge,
    # которая отправляет PendingIntent release AckReceiver. Новый alarm должен стать
    # позднее сработавшего, описание остаётся в списке после нового запуска процесса.
    # Перед reboot appops не изменяли; allow/deny exact относится лишь к этой фазе
    # и исчезает вместе с удалением установленного нами APK в finally.
    if($DozeQuotaSeven -and $summary.quotaEighthUsedWorker){
        # Тихая сводка worker по действующему контракту не содержит ACK action.
        # Открываем список её contentIntent и подтверждаем именно восьмую задачу
        # в деталях; будущий alarm и его сохранение после restart проверяются ниже.
        # Обычная блокировка выделенного AVD после часа с погашенным экраном
        # может удерживать contentIntent. Не меняем lock settings и не сбрасываем
        # PIN: dismiss-keyguard работает только там, где ОС разрешает снятие.
        Invoke-ReleaseTool $adb @('-s',$serial,'shell','wm','dismiss-keyguard') 'quota-ui-dismiss-keyguard.log' | Out-Null
        Read-ReleaseShade | Out-Null
        Click-ReleaseNode "//node[@text='$notificationDisplayText']"
        Assert-ReleaseListTask $edited
        Open-ReleaseDetails $edited
        Click-ReleaseId 'btnAckTask'
        $summary.notificationAckMode='task-details-after-worker-summary'
    }else{
        Click-ReleaseNode "//node[@text='Acknowledge' or @text='ACKNOWLEDGE']"
        $summary.notificationAckMode='notification-action'
    }
    $notificationAckAlarm = Wait-ReleaseAlarm $notificationDeadline
    $afterNotificationAckUi = Read-ReleaseShade
    if ($afterNotificationAckUi.SelectSingleNode("//node[@text='$notificationDisplayText']")) { throw 'Acknowledged notification still visible in shade' }
    Set-Content (Join-Path $run 'notifications-after-ack.log') (Invoke-ReleaseAdb @('shell', 'dumpsys', 'notification', '--noredact')) -Encoding UTF8
    $summary.notificationAckAlarm = $notificationAckAlarm
    if($LightIdleOnly){
        # Проверяем, что ACK сохранил именно4/8мин, а не просто любой будущий
        # alarm. Допуск учитывает UI/adb после нажатия, не обещает точность доставки.
        $ackNow=[long](Invoke-ReleaseAdb @('shell','date','+%s')).Trim()*1000
        $remaining=$notificationAckAlarm-$ackNow
        if([math]::Abs($remaining-$summary.lightFixtureMinutes*60000) -gt 45000){throw 'Light ACK did not preserve fixture interval'}
        $summary.lightAckRemainingSeconds=$remaining/1000.0
    }
    Invoke-ReleaseAdb @('shell', 'cmd', 'statusbar', 'collapse') | Out-Null
    Invoke-ReleaseAdb @('shell', 'am', 'force-stop', $package) | Out-Null
    Launch-Release
    if($DozeQuotaSeven){Assert-ReleaseListTask $edited}else{Assert-ReleaseText $edited}
    if ((Wait-ReleaseAlarm) -ne $notificationAckAlarm) { throw 'Notification ACK alarm lost after restart' }
    Complete-ReleasePhase 'release-notification-ack'
    # После ACK/restart запрет должен сохраниться, новый alarm — иметь положительное
    # окно и тот же срок, который прочитан после ACK. Повторную доставку не ждём.
    if ($InexactOnly) {
        $opsAfterAck = Invoke-ReleaseAdb @('shell', 'appops', 'get', $package, 'SCHEDULE_EXACT_ALARM')
        Set-Content (Join-Path $run 'inexact-appops-after-ack.log') $opsAfterAck -Encoding UTF8
        if ($opsAfterAck -notmatch 'SCHEDULE_EXACT_ALARM: deny') { throw 'Exact denial lost after notification ACK' }
        $ackAlarms = Invoke-ReleaseAdb @('shell', 'dumpsys', 'alarm')
        Set-Content (Join-Path $run 'inexact-alarm-after-ack.log') $ackAlarms -Encoding UTF8
        $ackAlarm = [regex]::Match($ackAlarms, '(?m)^\s*RTC_WAKEUP #\d+: Alarm\{\S+ type 0 origWhen (?<deadline>\d+) [^\r\n]* com\.vpe_soft\.intime\.intime\}\r?\n\s+tag=\*walarm\*:com\.vpe_soft\.intime\.intime/(?:com\.vpe_soft\.intime\.intime)?\.receiver\.AlarmReceiver\r?\n\s+type=RTC_WAKEUP[^\r\n]*?window=(?<window>\S+)')
        if (-not $ackAlarm.Success -or [long]$ackAlarm.Groups['deadline'].Value -ne $notificationAckAlarm -or $ackAlarm.Groups['window'].Value -notmatch '[1-9]') { throw 'Notification ACK did not keep an inexact alarm' }
        $summary.inexactAckAlarmWindow = $ackAlarm.Groups['window'].Value
        if($LightIdleOnly){Complete-ReleasePhase 'release-light-inexact-ack-window'}
    }

    if (-not $DozeOnly -and -not $InexactOnly -and -not $LightIdleOnly) {
    # Проверка отказа POST_NOTIFICATIONS: отзыв разрешения может убить процесс,
    # поэтому выполняем его до нового запуска. Флаги user-set/user-fixed исключают
    # всплывающий permission dialog. UI ACK ставит новый минутный alarm, Home;
    # receiver должен сработать, но описание не должно появиться в шторке даже
    # через пять секунд после срока. Задача остаётся в списке; базу не читаем.
    if ($TestApi -ge 33) {
        Invoke-ReleaseAdb @('shell', 'pm', 'revoke', $package, 'android.permission.POST_NOTIFICATIONS') | Out-Null
        Invoke-ReleaseAdb @('shell', 'pm', 'set-permission-flags', $package, 'android.permission.POST_NOTIFICATIONS', 'user-set', 'user-fixed') | Out-Null
        $deniedPackage = Invoke-ReleaseAdb @('shell', 'dumpsys', 'package', $package)
        if ($deniedPackage -notmatch 'android.permission.POST_NOTIFICATIONS: granted=false') { throw 'Runtime permission denial not confirmed' }
        Set-Content (Join-Path $run 'permission-denied-package.log') $deniedPackage -Encoding UTF8
        $summary.notificationBlockMode = 'runtime-permission'
    } else {
        # API 31: runtime permission отсутствует. Выключаем общий доступ через
        # системный экран только собственной установки; проверяем переключатель
        # и importance=NONE, затем receiver и отсутствие уведомления.
        Set-ReleaseAppNotificationsEnabled $false
        $summary.notificationBlockMode = 'settings-switch'
    }
    $deniedAlarm = Prepare-ReleaseMinuteAlarm $edited
    Assert-ReleaseBlockedNotification $deniedAlarm $edited 'permission-denied'
    $summary.permissionDeniedAlarm = $deniedAlarm
    Complete-ReleasePhase $(if ($TestApi -ge 33) { 'release-permission-denied' } else { 'release-app-notifications-denied' })

    # Проверка снятия отказа: очищаем только выставленные нами флаги, возвращаем
    # POST_NOTIFICATIONS и через UI ACK создаём новый срок той же задачи. Настоящее
    # уведомление и его action ACK должны снова работать. Снятие отказа без нового
    # ACK у старой просрочки в этой фазе не проверяется.
    if ($TestApi -ge 33) {
        Invoke-ReleaseAdb @('shell', 'pm', 'clear-permission-flags', $package, 'android.permission.POST_NOTIFICATIONS', 'user-set', 'user-fixed') | Out-Null
        Invoke-ReleaseAdb @('shell', 'pm', 'grant', $package, 'android.permission.POST_NOTIFICATIONS') | Out-Null
    } else {
        # Снятие общего запрета API 31: включаем уведомления в Settings;
        # новый UI ACK существующей задачи должен восстановить доставку/action ACK.
        # Автоматическая доставка старой просрочки без ACK этим не проверяется.
        Set-ReleaseAppNotificationsEnabled $true
    }
    Assert-ReleaseRecoveredNotification $edited 'permission-restored'
    Complete-ReleasePhase $(if ($TestApi -ge 33) { 'release-permission-restored' } else { 'release-app-notifications-restored' })

    # Проверка отключённого канала: общий доступ к уведомлениям разрешён, канал
    # DefaultChannelId выключается через экран Android. Новый минутный alarm
    # должен дойти до receiver, но не создать видимое уведомление. Это отдельная
    # проверка канала; общий системный запрет всех уведомлений здесь не включается.
    Set-ReleaseChannelEnabled $false
    $channelAlarm = Prepare-ReleaseMinuteAlarm $edited
    Assert-ReleaseBlockedNotification $channelAlarm $edited 'channel-disabled'
    $summary.channelDisabledAlarm = $channelAlarm
    Complete-ReleasePhase 'release-channel-disabled'

    # Проверка включения канала: через тот же системный экран возвращаем разрешение
    # канала и начинаем новый срок UI ACK. Ожидаем реальное уведомление и успешный
    # ACK из шторки. Тест не читает wasNotified и не моделирует восстановление
    # старой просрочки автоматически, без пользовательского действия.
    Set-ReleaseChannelEnabled $true
    Assert-ReleaseRecoveredNotification $edited 'channel-restored'
    Complete-ReleasePhase 'release-channel-restored'
    }
    }
    }
    $summary.status = 'PASSED'
} catch {
    $summary.status = if ($installedHere) { 'FAILED' } else { 'INFRASTRUCTURE_ERROR' }
    $summary.message = $_.Exception.Message
    if($LightIdleOnly -and $summary.message -match 'Natural light entry not confirmed|Confirmed light observation lost|Light IDLE/maintenance not confirmed|Natural light alarm delivery timed out'){$summary.status='INCONCLUSIVE'}
    if($LightIdleOnly -and $summary.message -like 'Light observation interrupted*'){$summary.status='INFRASTRUCTURE_INTERRUPTED'}
    # Отсутствие естественного запуска к лимиту — неопределённый результат
    # планирования, а не доказанное падение приложения/R8. Остальные assertions
    # и ошибки диагностики/очистки по-прежнему считаются FAILED.
    if ($summary.message -like 'Natural release worker notification timed out*') { $summary.status = 'INCONCLUSIVE' }
    if ($summary.message -like 'Periodic observation interrupted*') { $summary.status = 'INFRASTRUCTURE_INTERRUPTED' }
    if ($summary.message -like 'Inexact observation interrupted*') { $summary.status = 'INFRASTRUCTURE_INTERRUPTED' }
    if ($summary.message -like 'Inexact alarm notification timed out*') { $summary.status = 'INCONCLUSIVE' }
    Write-Output $summary.message
} finally {
    if($lightPowerChanged){
        try {Restore-ReleaseLightPower} catch {$summary.lightCleanupError=$_.Exception.Message;$summary.status='FAILED'}
    }
    if ($dozeChanged) {
        try {
            Set-Content (Join-Path $run 'doze-failure-state.log') (Invoke-ReleaseAdb @('shell', 'dumpsys', 'deviceidle')) -Encoding UTF8
        } catch { $summary.dozeDiagnosticError = $_.Exception.Message }
        try { Restore-ReleaseDoze } catch { $summary.dozeCleanupError = $_.Exception.Message; $summary.status = 'FAILED' }
    }
    if ($installedHere) {
        try {
            $window = Invoke-ReleaseAdb @('shell', 'dumpsys', 'window')
            $logcat = Invoke-ReleaseAdb @('logcat', '-d')
            Set-Content (Join-Path $run 'window.log') $window -Encoding UTF8
            Set-Content (Join-Path $run 'logcat.log') $logcat -Encoding UTF8
            $beforeRebootLog = Join-Path $run 'logcat-before-reboot.log'
            $priorHealth = if (Test-Path $beforeRebootLog) { Get-Content $beforeRebootLog -Raw -Encoding UTF8 } else { '' }
            if ($window -match 'Application Not Responding:' -or $logcat -match 'ANR in|FATAL EXCEPTION' -or $priorHealth -match 'ANR in|FATAL EXCEPTION') {
                $summary.status = 'FAILED'; $summary.healthError = 'ANR or fatal exception in smoke logs'
            }
        } catch { $summary.healthError = $_.Exception.Message; $summary.status = 'FAILED' }
        # Ошибка сбора диагностики не должна мешать удалению нашей установки.
        try { Invoke-ReleaseAdb @('uninstall', $package) | Out-Null }
        catch { $summary.cleanupError = $_.Exception.Message; $summary.status = 'FAILED' }
    }
    if ($probePushed) {
        try { Invoke-ReleaseAdb @('shell', 'rm', '-f', '/data/local/tmp/intime-release-probe.jar', '/data/local/tmp/intime-release-ui.xml', '/data/local/tmp/local/tmp/intime-release-probe-output.xml') | Out-Null }
        catch { $summary.probeCleanupError = $_.Exception.Message; $summary.status = 'FAILED' }
    }
    if ($installedHere -and $backupDevicePath) {
        # Только уникальный файл этого запуска; сохранённая локальная копия остаётся в отчёте.
        try { Invoke-ReleaseAdb @('shell', 'rm', '-f', $backupDevicePath) | Out-Null }
        catch { $summary.backupCleanupError = $_.Exception.Message; $summary.status = 'FAILED' }
    }
    $env:JAVA_HOME = $oldJavaHome
    if ($lock) { $lock.Dispose() }
    if ($PeriodicOnly) {
        try { Set-ReleaseDispatchAssessment $summary $run }
        catch { $summary.dispatchAssessmentError = $_.Exception.Message; $summary.status = 'FAILED' }
    }
    $summary | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $run 'summary.json') -Encoding UTF8
    Write-Output "$($summary.status). Reports: $run"
}
if ($summary.status -notin @('PASSED', 'PASSED_CONTROLLED_PLATFORM_DISPATCH')) { exit 1 }
