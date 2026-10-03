<#
Проверяет уже собранный production release APK через UI на проектном AVD API 36.
Перед запуском: scripts/test.ps1 -Mode device -TestApi 36 -Offline -KeepEmulator.
APK должен быть подписан подтверждённым upload key; скрипт не публикует его в Play.
Используются только новые синтетические задачи. Если production-пакет уже установлен,
скрипт отказывается продолжать; данные существующей установки не очищаются.
После проверки удаляется только APK, установленный этим запуском. Эмулятор остаётся работать.
#>
[CmdletBinding()]
param([Parameter(Mandatory = $true)][string]$ApkPath)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$work = Join-Path $root '.test-tools'
$run = Join-Path $work ('runs\' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-release-smoke')
New-Item -ItemType Directory -Path $run -Force | Out-Null
$sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$adb = Join-Path $sdk 'platform-tools\adb.exe'
$serial = 'emulator-5584'
$package = 'com.vpe_soft.intime.intime'
$installedHere = $false
$probePushed = $false
$backupDevicePath = $null
$oldJavaHome = $env:JAVA_HOME
$lock = $null
$commandNumber = 0
$summary = @{ status = 'RUNNING'; passedPhases = @(); reports = $run; serial = $serial }

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
    $probe = Invoke-ReleaseAdb @('shell', 'uiautomator', 'runtest', '/system/framework/android.test.runner.jar', '/system/framework/android.test.base.jar', '/data/local/tmp/intime-release-probe.jar', '-c', 'intime.smoke.ReleaseHierarchyProbe')
    if ($probe -notmatch 'OK \(1 test\)' -or $probe -notmatch 'INTIME_FRESH_HIERARCHY_READY' -or $probe -match 'FAILURES!!!|INSTRUMENTATION_FAILED|Test run aborted|shortMsg=') { throw 'Fresh UI hierarchy probe failed' }
    $path = Join-Path $run ("ui-{0:D3}.xml" -f $script:commandNumber)
    Invoke-ReleaseAdb @('pull', '/data/local/tmp/intime-release-ui.xml', $path) | Out-Null
    return [xml](Get-Content $path -Raw -Encoding UTF8)
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
function Assert-ReleaseText([string]$Text) { Wait-ReleaseNode "//node[@text='$Text']" | Out-Null }
function Launch-Release {
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

try {
    $lock = [IO.File]::Open((Join-Path $work 'run.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
    $state = Get-Content (Join-Path $work 'emulator.json') -Raw | ConvertFrom-Json
    $process = Get-Process -Id $state.processId -ErrorAction Stop
    if ($state.serial -ne $serial -or $process.StartTime.ToUniversalTime().ToString('o') -ne $state.started) { throw 'Emulator ownership mismatch' }
    $name = Invoke-ReleaseAdb @('emu', 'avd', 'name')
    if ($name.Split("`n")[0].Trim() -ne 'Intime_Test_API36') { throw 'Refusing another AVD' }
    if ((Invoke-ReleaseAdb @('shell', 'getprop', 'ro.build.version.sdk')).Trim() -ne '36') { throw 'Expected API 36' }
    if ((Invoke-ReleaseAdb @('shell', 'dumpsys', 'window')) -match 'Application Not Responding:') { throw 'System ANR dialog is present' }
    $ApkPath = (Resolve-Path -LiteralPath $ApkPath).Path
    $tools = Join-Path $sdk 'build-tools\35.0.0'
    $env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
    $badging = (& (Join-Path $tools 'aapt.exe') dump badging $ApkPath) -join "`n"
    if ($LASTEXITCODE -ne 0 -or $badging -notmatch "package: name='$package' versionCode='25'" -or $badging -match 'application-debuggable') { throw 'Expected non-debuggable production candidate code 25' }
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
    Invoke-ReleaseAdb @('shell', 'pm', 'grant', $package, 'android.permission.POST_NOTIFICATIONS') | Out-Null
    Launch-Release

    # Проверка создания: чистая release-установка, разрешение уведомлений выдано.
    # Через форму вводим одну задачу с периодом час; список должен показать описание,
    # а AlarmManager — действующий будильник. Этим проверяется реальный Room/репозиторий
    # после R8, без in-memory базы и без вызова внутренних методов приложения.
    $description = 'R8-Smoke-' + (Get-Date -Format 'HHmmss')
    Click-ReleaseId 'fab_add_task'
    Click-ReleaseId 'edit_task_description'
    Invoke-ReleaseAdb @('shell', 'input', 'text', $description) | Out-Null
    Invoke-ReleaseAdb @('shell', 'input', 'keyevent', '4') | Out-Null # Закрываем клавиатуру.
    Click-ReleaseId 'spinner_interval'
    Click-ReleaseNode "//node[@text='Час']"
    Click-ReleaseId 'btn_save_task'
    Wait-ReleaseNode "//node[@resource-id='${package}:id/textDescription' and @text='$description']" | Out-Null
    $beforeAck = Wait-ReleaseAlarm
    Complete-ReleasePhase 'create-and-alarm'

    # Проверка редактирования: открываем созданную строку и форму Edit, дописываем
    # суффикс. После сохранения старое описание заменяется новым; повторный запуск
    # после force-stop должен прочитать это же значение из файловой Room.
    Click-ReleaseNode "//node[@resource-id='${package}:id/textDescription' and @text='$description']"
    Click-ReleaseId 'btnEditTask'
    Click-ReleaseId 'edit_task_description'
    Invoke-ReleaseAdb @('shell', 'input', 'keyevent', 'KEYCODE_MOVE_END') | Out-Null
    Invoke-ReleaseAdb @('shell', 'input', 'text', '-Edited') | Out-Null
    Invoke-ReleaseAdb @('shell', 'input', 'keyevent', '4') | Out-Null
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
    Click-ReleaseNode "//node[@resource-id='${package}:id/textDescription' and @text='$edited']"
    Click-ReleaseId 'btnAckTask'
    Assert-ReleaseText $edited
    $afterAck = Wait-ReleaseAlarm $beforeAck
    $summary.alarmBeforeAck = $beforeAck
    $summary.alarmAfterAck = $afterAck
    Complete-ReleasePhase 'ack-and-reschedule'

    # Проверка настроек и Back: экран с DataBinding должен показать версию кандидата;
    # системный Back возвращает список с задачей. Внешние настройки ОС не изменяем.
    Click-ReleaseId 'btn_open_settings'
    Wait-ReleaseNode "//node[contains(@text, '2.0.0-rc1')]" | Out-Null
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
    Click-ReleaseNode "//node[@class='android.widget.EditText']"
    Invoke-ReleaseAdb @('shell', 'input', 'keycombination', '113', '29') | Out-Null
    Invoke-ReleaseAdb @('shell', 'input', 'text', $backupName) | Out-Null
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
    Click-ReleaseNode "//node[@resource-id='${package}:id/textDescription' and @text='$edited']"
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

    # Проверка BootReceiver: импортированная часовая задача сохранена, приложение
    # запускалось и не находится в stopped. Уходим Home, записываем точный срок
    # alarm и boot_count, выполняем настоящий reboot только принадлежащего нам AVD.
    # После нового boot_count ждём тот же alarm ДО запуска Activity: восстановить
    # его должен release BootReceiver, а не побочный эффект открытия приложения.
    # Просрочку во время выключения и boot-уведомление эта фаза не моделирует.
    Invoke-ReleaseAdb @('shell', 'input', 'keyevent', '3') | Out-Null
    $bootBefore = [int](Invoke-ReleaseAdb @('shell', 'settings', 'get', 'global', 'boot_count')).Trim()
    $expectedBootAlarm = Wait-ReleaseAlarm
    Set-Content (Join-Path $run 'logcat-before-reboot.log') (Invoke-ReleaseAdb @('logcat', '-d')) -Encoding UTF8
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
    if ($bootAlarm -ne $expectedBootAlarm) { throw 'Release BootReceiver did not restore the same alarm' }
    if ((Invoke-ReleaseAdb @('shell', 'dumpsys', 'window')) -match 'Application Not Responding:') { throw 'ANR dialog after reboot' }
    $summary.bootCountBefore = $bootBefore
    $summary.bootCountAfter = $bootAfter
    $summary.bootRestoredAlarm = $bootAlarm
    Complete-ReleasePhase 'release-reboot-recovery'

    # Проверка реальной доставки: только нашей новой установке разрешаем exact
    # alarms через appops, меняем период задачи через UI на минуту, подтверждаем
    # её в деталях для нового отсчёта от текущего ACK и уходим Home.
    # Ждём наступления срока и появления её уникального описания в системной шторке.
    # Receiver не вызывается вручную, время ОС не меняется, приватная база не читается.
    # Этот сценарий проверяет разрешённые уведомления/exact alarm, не отказ или Doze.
    Invoke-ReleaseAdb @('shell', 'appops', 'set', $package, 'SCHEDULE_EXACT_ALARM', 'allow') | Out-Null
    Launch-Release
    Assert-ReleaseText $edited
    Click-ReleaseNode "//node[@resource-id='${package}:id/textDescription' and @text='$edited']"
    Click-ReleaseId 'btnEditTask'
    Click-ReleaseId 'spinner_interval'
    Click-ReleaseNode "//node[@text='Минута']"
    Click-ReleaseId 'btn_save_task'
    Click-ReleaseId 'btnAckTask'
    Assert-ReleaseText $edited
    $notificationDeadline = Wait-ReleaseAlarm
    $summary.notificationAlarm = $notificationDeadline
    Write-Output "Waiting for real AlarmManager notification (deadline $notificationDeadline)..."
    Invoke-ReleaseAdb @('shell', 'input', 'keyevent', '3') | Out-Null
    Invoke-ReleaseAdb @('shell', 'cmd', 'statusbar', 'expand-notifications') | Out-Null
    $notificationLimit = (Get-Date).AddSeconds(150)
    $notificationVisible = $false
    do {
        $notificationUi = Read-ReleaseUi
        $notificationVisible = $null -ne $notificationUi.SelectSingleNode("//node[@text='$edited']")
        if (-not $notificationVisible) { Start-Sleep -Seconds 2 }
    } while (-not $notificationVisible -and (Get-Date) -lt $notificationLimit)
    if (-not $notificationVisible) { throw 'Release task notification not delivered to shade' }
    Set-Content (Join-Path $run 'notifications-delivered.log') (Invoke-ReleaseAdb @('shell', 'dumpsys', 'notification', '--noredact')) -Encoding UTF8
    Complete-ReleasePhase 'release-notification-delivery'

    # Проверка ACK из настоящего уведомления: нажимаем системную action Acknowledge,
    # которая отправляет PendingIntent release AckReceiver. Новый alarm должен стать
    # позднее сработавшего, описание остаётся в списке после нового запуска процесса.
    # Перед reboot appops не изменяли; разрешение exact относится лишь к этой фазе
    # и исчезает вместе с удалением установленного нами APK в finally.
    Click-ReleaseNode "//node[@text='Acknowledge' or @text='ACKNOWLEDGE']"
    $notificationAckAlarm = Wait-ReleaseAlarm $notificationDeadline
    $afterNotificationAckUi = Read-ReleaseUi
    if ($afterNotificationAckUi.SelectSingleNode("//node[@text='$edited']")) { throw 'Acknowledged notification still visible in shade' }
    Set-Content (Join-Path $run 'notifications-after-ack.log') (Invoke-ReleaseAdb @('shell', 'dumpsys', 'notification', '--noredact')) -Encoding UTF8
    $summary.notificationAckAlarm = $notificationAckAlarm
    Invoke-ReleaseAdb @('shell', 'cmd', 'statusbar', 'collapse') | Out-Null
    Invoke-ReleaseAdb @('shell', 'am', 'force-stop', $package) | Out-Null
    Launch-Release
    Assert-ReleaseText $edited
    if ((Wait-ReleaseAlarm) -ne $notificationAckAlarm) { throw 'Notification ACK alarm lost after restart' }
    Complete-ReleasePhase 'release-notification-ack'
    $summary.status = 'PASSED'
} catch {
    $summary.status = if ($installedHere) { 'FAILED' } else { 'INFRASTRUCTURE_ERROR' }
    $summary.message = $_.Exception.Message
    Write-Output $summary.message
} finally {
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
    $summary | ConvertTo-Json | Set-Content (Join-Path $run 'summary.json') -Encoding UTF8
    Write-Output "$($summary.status). Reports: $run"
}
if ($summary.status -ne 'PASSED') { exit 1 }
