<#
Обновляет исходный Play APK 25/1.1.12 до явно указанного кандидата только на Intime_Test_API35.
Перед запуском: test.ps1 -Mode device -TestApi 35 -Filter ...ScreenPlatformTest
-Offline -KeepEmulator -EmulatorGpu host -DisableEmulatorVulkan -EmulatorRamMb 4096.
Не переподписывает APK. Синтетические задачи создаёт старый UI. Читает только
копии базы собственной новой установки на root-capable AOSP AVD, учитывая WAL.
Не читает пользовательские main*.db и не адресует телефон. Нужен локальный Python
со стандартным sqlite3. После прогона удаляет свою установку и останавливает AVD.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$OldApkPath,
    [Parameter(Mandatory=$true)][string]$NewApkPath,
    [Parameter(Mandatory=$true)][string]$PythonPath,
    # Явные ожидания исключают проверку другой загрузки Console по ошибке.
    # Код 26 допустим для исторического прогона; новый кандидат — 27/rc3.
    [Parameter(Mandatory=$true)][ValidateRange(26,2100000000)][int]$ExpectedNewVersionCode,
    [Parameter(Mandatory=$true)]
    [ValidatePattern('^[0-9]+\.[0-9]+\.[0-9]+(?:-[A-Za-z0-9.]+)?$')][string]$ExpectedNewVersionName
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$work = Join-Path $root '.test-tools'
$run = Join-Path $work ('runs\' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-play-upgrade-' + [guid]::NewGuid().ToString('N').Substring(0,8))
$null = New-Item -ItemType Directory -Path $run
$sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$tools = Join-Path $sdk 'build-tools\35.0.0'
$adb = Join-Path $sdk 'platform-tools\adb.exe'
$serial = 'emulator-5580'
$package = 'com.vpe_soft.intime.intime'
$commandNumber = 0
$summary = [ordered]@{status='RUNNING'; passedPhases=@(); reports=$run; testApi=35; serial=$serial; phoneUsed=$false; signatureScope='original Google Play APKs'; cleanup=$false; expectedNewVersionCode=$ExpectedNewVersionCode; expectedNewVersionName=$ExpectedNewVersionName}
$lock=$null; $owned=$false; $installedHere=$false; $probePushed=$false
$remoteSnapshots=@(); $backupDevicePaths=@(); $backupDevicePath=$null; $state=$null
$oldJavaHome=$env:JAVA_HOME

# Импортируем только выбранные определения UI-помощников из AST, не выполняя
# основную часть release runner с установкой/удалением. Подпись и владение AVD
# этот сценарий проверяет отдельно. Изменения помощников проверяются device-прогоном.
$tokens=$null; $errors=$null
$ast=[Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'test-release-smoke.ps1'),[ref]$tokens,[ref]$errors)
if($errors.Count){throw ($errors|Out-String)}
foreach($helper in @('Invoke-ReleaseTool','Invoke-ReleaseAdb','Read-ReleaseUi','Wait-ReleaseNode','Click-ReleaseNode','Click-ReleaseId','Assert-ReleaseText','Launch-Release','Read-ReleaseAlarm','Wait-ReleaseAlarm','Complete-ReleasePhase','Focus-ReleaseDescription','Close-ReleaseKeyboard','Open-ReleaseDetails','Open-ReleaseEdit')){
    $definition=$ast.Find({param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq $helper},$true)
    if(-not $definition){throw "UI helper missing: $helper"}
    Invoke-Expression $definition.Extent.Text
}

# UID и firstInstallTime подтверждают обновление той же установки. Статус версии
# читается у точного production-пакета; сравнение не является чтением его базы.
function Read-InstalledIdentity([int]$Code) {
    $dump=Invoke-ReleaseAdb @('shell','dumpsys','package',$package)
    $version=[regex]::Match($dump,'\bversionCode=(\d+)')
    # Android 15 выводит appId, прежние dumpsys — userId. Для проверенного ниже
    # пользователя 0 appId совпадает с UID; чужие userId из других секций не берём.
    $uid=[regex]::Match($dump,'(?m)^\s*(?:userId|appId)=(\d+)\s*$')
    $first=[regex]::Match($dump,'(?m)^\s*firstInstallTime=([^\r\n]+)')
    if(-not $version.Success -or [int]$version.Groups[1].Value -ne $Code -or -not $uid.Success -or -not $first.Success){throw 'Installed package identity unavailable/wrong'}
    return @{uid=$uid.Groups[1].Value; firstInstallTime=$first.Groups[1].Value.Trim(); versionCode=$Code}
}

# Проверяем обе подписи и точные версии до установки. Исходный Play 25 остаётся
# фиксированным, ожидания кандидата передаются явно и используются также после
# install -r. Host-проверки подменяют только транспорт вызова verify-play-apk.
function Invoke-PlayUpgradePreflight([string]$OldPath,[string]$NewPath,[int]$NewCode,[string]$NewName) {
    foreach($entry in @(@{path=$OldPath;code=25;name='1.1.12'},@{path=$NewPath;code=$NewCode;name=$NewName})){
        Invoke-ReleaseTool 'powershell.exe' @('-NoProfile','-ExecutionPolicy','Bypass','-File',(Join-Path $PSScriptRoot 'verify-play-apk.ps1'),'-ApkPath',$entry.path,'-ExpectedVersionCode',"$($entry.code)",'-ExpectedVersionName',$entry.name) "preflight-$($entry.code).log"|Out-Null
    }
}

# До копирования принудительно останавливаем только установленный нами пакет.
# AOSP su используется исключительно после проверки project AVD и ownership.
# Копируем main и существующие WAL/SHM под уникальными именами, ничего не пишем
# в приватную базу. Python читает только эти локальные копии в mode=ro.
function Read-OwnSnapshot([string]$Name) {
    if(-not $owned -or -not $installedHere){throw 'Snapshot requires owned fixture installation'}
    Invoke-ReleaseAdb @('shell','am','force-stop',$package)|Out-Null
    $remote='/data/local/tmp/intime-upgrade-'+[guid]::NewGuid().ToString('N')+'.sqlite'
    $local=Join-Path $run "$Name.sqlite"
    foreach($suffix in @('','-wal','-shm')){
        $source="/data/user/0/$package/databases/main$suffix"
        $exists=Invoke-ReleaseAdb @('shell','su','0','sh','-c',"'if test -f $source; then echo PRESENT; else echo ABSENT; fi'")
        if($exists.Trim() -eq 'ABSENT') {if($suffix -eq ''){throw 'Fixture database missing'}; continue}
        if($exists.Trim() -ne 'PRESENT'){throw 'Cannot inspect own snapshot source'}
        $target="$remote$suffix"
        $script:remoteSnapshots+=$target
        Invoke-ReleaseAdb @('shell','su','0','cp',$source,$target)|Out-Null
        Invoke-ReleaseAdb @('shell','su','0','chmod','644',$target)|Out-Null
        Invoke-ReleaseAdb @('pull',$target,"$local$suffix")|Out-Null
    }
    $json=Invoke-ReleaseTool $PythonPath @((Join-Path $PSScriptRoot 'read-upgrade-snapshot.py'),$local) "$Name.json"
    return $json|ConvertFrom-Json
}

# Исходные две задачи должны сохранить каждый из восьми старых столбцов и ID.
# Проверяем Room 6 и wasNotified=0 для будущих задач. Неверные схемы, старые
# версии 1–4 и реальные пользовательские данные эта фаза не моделирует.
function Assert-PreservedSnapshot($Before,$After) {
    $columns='id,description,interval,amount,next_alarm,next_caution,last_ack,quant'
    if(($Before.columns -join ',') -ne $columns -or $Before.schemaVersion -ne 5 -or $Before.roomIdentity){throw 'Expected historical SQLite schema 5 without Room'}
    if(($After.columns -join ',') -ne "$columns,wasNotified" -or $After.schemaVersion -ne 6 -or -not $After.roomIdentity){throw 'Expected migrated Room schema 6'}
    if($Before.rows.Count -ne 2 -or $After.rows.Count -ne 2){throw 'Expected exactly two preserved tasks'}
    for($row=0;$row -lt 2;$row++){
        for($column=0;$column -lt 8;$column++){
            if([string]$Before.rows[$row][$column] -cne [string]$After.rows[$row][$column]){throw "Task field changed: row $row column $column"}
        }
        if([int]$After.rows[$row][8] -ne 0){throw 'Future migrated task unexpectedly notified'}
    }
}

# Через SAF экспортируем только свои задачи. После нажатия Save копируем созданный
# файл и сверяем все восемь значений каждой строки с мигрированным снимком, не
# считая импорт/экспорт доказательством самого install -r.
function Export-OwnTasks($Expected, [switch]$OldVersion) {
    # У старого APK другие id и метаданные. Сравниваем данные строк отдельно
    # от оболочки JSON; не переименовываем поля старого файла для этой проверки.
    $exportId='export_to_json_btn'
    if($OldVersion){
        Invoke-ReleaseAdb @('shell','am','start','-W','-n',"$package/com.vpe_soft.intime.intime.activity.MainActivity")|Out-Null
        Wait-ReleaseNode "//node[@resource-id='${package}:id/add_task']"|Out-Null
        Click-ReleaseId 'open_settings'; $exportId='export_btn'
    }else{Launch-Release; Click-ReleaseId 'btn_open_settings'}
    Click-ReleaseId $exportId
    $backupName='intime-play-upgrade-'+[guid]::NewGuid().ToString('N')+'.json'
    $script:backupDevicePath='/sdcard/Download/'+$backupName
    $script:backupDevicePaths+=$script:backupDevicePath
    $fieldXPath="//node[@class='android.widget.EditText' and @resource-id='android:id/title']"
    $limit=(Get-Date).AddSeconds(30)
    do{Click-ReleaseNode $fieldXPath; $field=Wait-ReleaseNode $fieldXPath; if($field.GetAttribute('focused') -eq 'true'){break}}while((Get-Date) -lt $limit)
    if($field.GetAttribute('focused') -ne 'true'){throw 'SAF filename focus failed'}
    Invoke-ReleaseAdb @('shell','input','keyevent','KEYCODE_MOVE_END')|Out-Null
    if($field.GetAttribute('text').Length){Invoke-ReleaseAdb (@('shell','input','keyevent')+(@('KEYCODE_DEL')*$field.GetAttribute('text').Length))|Out-Null}
    Invoke-ReleaseAdb @('shell','input','text',$backupName)|Out-Null
    Wait-ReleaseNode "//node[@class='android.widget.EditText' and @text='$backupName']"|Out-Null
    Click-ReleaseNode "//node[@resource-id='android:id/button1']"
    Wait-ReleaseNode "//node[@resource-id='${package}:id/$exportId']"|Out-Null
    $local=Join-Path $run $backupName
    Invoke-ReleaseAdb @('pull',$backupDevicePath,$local)|Out-Null
    $backup=Get-Content $local -Raw -Encoding UTF8|ConvertFrom-Json
    if($OldVersion){
        if($backup.meta.backup_version -ne 1 -or $backup.meta.db_version -ne 5 -or $backup.meta.app -ne $package -or $backup.meta.created_at -le 0){throw 'Unexpected historical export metadata'}
    }elseif($backup.meta.version -ne 1){throw 'Unexpected current export metadata'}
    if(($backup.tables.tasks.columns -join ',') -ne 'id,description,interval,amount,next_alarm,next_caution,last_ack,quant' -or $backup.tables.tasks.rows.Count -ne 2){throw 'Unexpected export contract/count'}
    $rows=@($backup.tables.tasks.rows|Sort-Object {[long]$_[0]})
    for($row=0;$row -lt 2;$row++){for($column=0;$column -lt 8;$column++){if([string]$rows[$row][$column] -cne [string]$Expected.rows[$row][$column]){throw 'Export differs from migrated tasks'}}}
    if($OldVersion){
        $summary.oldExport=$local
        $summary.oldExportMetadata=$backup.meta
        $summary.oldExportSha256=(Get-FileHash $local -Algorithm SHA256).Hash
    }else{$summary.exportSha256=(Get-FileHash $local -Algorithm SHA256).Hash}
    Invoke-ReleaseAdb @('shell','input','keyevent','4')|Out-Null
}

try {
    $lock=[IO.File]::Open((Join-Path $work 'run.lock'),'OpenOrCreate','ReadWrite','None')
    $state=Get-Content (Join-Path $work 'emulator.json') -Raw|ConvertFrom-Json
    $process=Get-Process -Id $state.processId
    if($state.serial -ne $serial -or $process.StartTime.ToUniversalTime().ToString('o') -ne $state.started){throw 'Emulator ownership mismatch'}
    $name=Invoke-ReleaseAdb @('emu','avd','name')
    if($name.Split("`n")[0].Trim() -ne 'Intime_Test_API35' -or (Invoke-ReleaseAdb @('shell','getprop','ro.build.version.sdk')).Trim() -ne '35'){throw 'Refusing another emulator/API'}
    $owned=$true
    if((Invoke-ReleaseAdb @('shell','am','get-current-user')).Trim() -ne '0'){throw 'Expected dedicated AVD user 0'}
    if((Invoke-ReleaseAdb @('shell','pm','list','packages',$package)) -match '(?m)^package:com\.vpe_soft\.intime\.intime\r?$'){throw 'Unknown production installation present; refusing replacement'}
    if((Invoke-ReleaseAdb @('shell','dumpsys','window')) -match 'Application Not Responding:'){throw 'ANR before upgrade'}
    if((Invoke-ReleaseAdb @('shell','su','0','id')) -notmatch 'uid=0'){throw 'AOSP root required for own synthetic snapshots'}
    $OldApkPath=(Resolve-Path -LiteralPath $OldApkPath).Path
    $NewApkPath=(Resolve-Path -LiteralPath $NewApkPath).Path
    # Независимый preflight перед установкой обоих оригинальных файлов.
    Invoke-PlayUpgradePreflight $OldApkPath $NewApkPath $ExpectedNewVersionCode $ExpectedNewVersionName
    $summary.oldApkSha256=(Get-FileHash $OldApkPath -Algorithm SHA256).Hash
    $summary.newApkSha256=(Get-FileHash $NewApkPath -Algorithm SHA256).Hash
    $env:JAVA_HOME='C:\Program Files\Java\jdk-17'
    $classes=Join-Path $run 'probe-classes'; $dex=Join-Path $run 'probe-dex'
    $null=New-Item -ItemType Directory -Path $classes,$dex
    $platform=Join-Path $sdk 'platforms\android-36'
    $junit=Get-ChildItem (Join-Path ([Environment]::GetFolderPath('UserProfile')) '.gradle\caches\modules-2\files-2.1\junit\junit\4.13.2') -Recurse -Filter 'junit-4.13.2.jar'|Select-Object -First 1
    if(-not $junit){throw 'Cached JUnit missing'}
    Invoke-ReleaseTool (Join-Path $env:JAVA_HOME 'bin\javac.exe') @('-encoding','UTF-8','--release','8','-classpath',"$(Join-Path $platform 'android.jar');$(Join-Path $platform 'uiautomator.jar');$($junit.FullName)",'-d',$classes,(Join-Path $PSScriptRoot 'android\ReleaseHierarchyProbe.java')) 'probe-compile.log'|Out-Null
    Invoke-ReleaseTool (Join-Path $tools 'd8.bat') @('--min-api','24','--output',$dex,(Join-Path $classes 'intime\smoke\ReleaseHierarchyProbe.class')) 'probe-dex.log'|Out-Null
    $jar=Join-Path $run 'intime-release-probe.jar'
    Invoke-ReleaseTool (Join-Path $env:JAVA_HOME 'bin\jar.exe') @('cf',$jar,'-C',$dex,'classes.dex') 'probe-package.log'|Out-Null
    Invoke-ReleaseAdb @('push',$jar,'/data/local/tmp/intime-release-probe.jar')|Out-Null
    $probePushed=$true
    Invoke-ReleaseAdb @('logcat','-c')|Out-Null
    $output=Invoke-ReleaseAdb @('install',$OldApkPath)
    if($output -notmatch '(?m)^Success\r?$'){throw 'Old APK install did not report Success'}
    $installedHere=$true
    Invoke-ReleaseAdb @('shell','pm','grant',$package,'android.permission.POST_NOTIFICATIONS')|Out-Null
    $beforeIdentity=Read-InstalledIdentity 25
    Invoke-ReleaseAdb @('shell','am','start','-W','-n',"$package/com.vpe_soft.intime.intime.activity.MainActivity")|Out-Null
    Wait-ReleaseNode "//node[@resource-id='${package}:id/add_task']"|Out-Null
    $first='PlayUpgrade-First-'+(Get-Date -Format 'HHmmss'); $second='PlayUpgrade-Second-'+(Get-Date -Format 'HHmmss')
    # Две задачи через старую форму, разные единицы периода; amount/quant по умолчанию.
    # Будущие сроки исключают намеренный notification dispatch во время сравнения.
    foreach($task in @(@{text=$first;unit='Час';english='Hours'},@{text=$second;unit='День';english='Days'})){
        Click-ReleaseId 'add_task'
        Click-ReleaseId 'description'
        Invoke-ReleaseAdb @('shell','input','text',$task.text)|Out-Null
        Wait-ReleaseNode "//node[@resource-id='${package}:id/description' and @text='$($task.text)']"|Out-Null
        $ime=Invoke-ReleaseAdb @('shell','dumpsys','input_method')
        if($ime -match '\bmInputShown=true\b|\bmIsInputViewShown=true\b'){Invoke-ReleaseAdb @('shell','input','keyevent','4')|Out-Null}
        # Play APK показывают английские названия на en_US AVD; поддерживаем
        # также русскую локализацию. Выбираем только точную нужную единицу.
        Click-ReleaseId 'spinner'; Click-ReleaseNode "//node[@text='$($task.unit)' or @text='$($task.english)']"
        Click-ReleaseId 'newtask_action'; Assert-ReleaseText $task.text
    }
    $before=Read-OwnSnapshot 'before'
    if($before.rows.Count -ne 2 -or (@($before.rows|ForEach-Object{$_[1]}) -notcontains $first) -or (@($before.rows|ForEach-Object{$_[1]}) -notcontains $second)){throw 'Old UI tasks missing from snapshot'}
    Complete-ReleasePhase 'old-ui-fixture'
    # Две старые синтетические задачи экспортируются настоящим UI/SAF до обновления.
    # Восемь полей/ID сверяются с SQLite-снимком, оригинальный JSON сохраняется.
    # Сам по себе успешный экспорт не доказывает поддержку файла новым импортёром.
    Export-OwnTasks $before -OldVersion
    Invoke-ReleaseAdb @('shell','am','force-stop',$package)|Out-Null
    Complete-ReleasePhase 'old-json-export'
    # Между установками нет uninstall/clear/import. Строго install -r исходного Play APK.
    $output=Invoke-ReleaseAdb @('install','-r',$NewApkPath)
    if($output -notmatch '(?m)^Success\r?$'){throw 'Update did not report Success'}
    $afterIdentity=Read-InstalledIdentity $ExpectedNewVersionCode
    if($beforeIdentity.uid -ne $afterIdentity.uid -or $beforeIdentity.firstInstallTime -ne $afterIdentity.firstInstallTime){throw 'Update replaced installation identity'}
    $summary.installationIdentity=$afterIdentity
    Complete-ReleasePhase 'install-r-identity'
    Launch-Release; Assert-ReleaseText $first; Assert-ReleaseText $second
    $after=Read-OwnSnapshot 'after'
    Assert-PreservedSnapshot $before $after
    Launch-Release
    $alarm=Wait-ReleaseAlarm
    if($alarm -ne [long]$before.rows[0][4]){throw 'Migrated earliest alarm differs from old deadline'}
    $summary.migratedAlarm=$alarm
    Complete-ReleasePhase 'preserved-schema-fields-alarm'
    Export-OwnTasks $after
    Complete-ReleasePhase 'json-export-agrees'
    # После экспорта останавливаем процесс и снова читаем собственную базу.
    # Все исходные значения должны остаться прежними; новое открытие показывает
    # обе задачи. Здесь нет reboot, receiver dispatch или реального пользовательского DB.
    $restart=Read-OwnSnapshot 'restart'
    Assert-PreservedSnapshot $before $restart
    Launch-Release; Assert-ReleaseText $first; Assert-ReleaseText $second
    Complete-ReleasePhase 'process-restart'
    # Меняем описание первой мигрированной строки, затем ACK; ID сохраняется,
    # last_ack/next_alarm меняются. Другие поля и вторая строка проверяются ниже.
    Open-ReleaseDetails $first; Open-ReleaseEdit; Focus-ReleaseDescription
    Invoke-ReleaseAdb @('shell','input','keyevent','KEYCODE_MOVE_END')|Out-Null
    Invoke-ReleaseAdb @('shell','input','text','-Edited')|Out-Null
    Close-ReleaseKeyboard; Click-ReleaseId 'btn_save_task'
    $edited="$first-Edited"; Assert-ReleaseText $edited
    Click-ReleaseId 'btnAckTask'; Assert-ReleaseText $edited
    $changed=Read-OwnSnapshot 'edited-ack'
    $row=$changed.rows[0]
    if($row[0] -ne $before.rows[0][0] -or $row[1] -cne $edited -or [long]$row[4] -le [long]$before.rows[0][4] -or [long]$row[6] -le [long]$before.rows[0][6]){throw 'Edit/ACK not persisted'}
    foreach($column in @(2,3,7)){if($row[$column] -ne $before.rows[0][$column]){throw 'Edit/ACK changed interval/amount/quant'}}
    for($column=0;$column -lt 8;$column++){if([string]$changed.rows[1][$column] -cne [string]$before.rows[1][$column]){throw 'Edit/ACK changed other task'}}
    Complete-ReleasePhase 'edit-and-ack'
    # Новая задача получает новый ID. Удаляем вторую исходную строку через UI,
    # затем проверяем итоговый список в базе и после повторного открытия.
    Launch-Release; Click-ReleaseId 'fab_add_task'; Focus-ReleaseDescription
    $third='PlayUpgrade-New-'+(Get-Date -Format 'HHmmss')
    Invoke-ReleaseAdb @('shell','input','text',$third)|Out-Null
    Close-ReleaseKeyboard; Click-ReleaseId 'spinner_interval'; Click-ReleaseNode "//node[@text='Час' or @text='Hours']"; Click-ReleaseId 'btn_save_task'
    Assert-ReleaseText $third
    Open-ReleaseDetails $second; Click-ReleaseId 'btnDeleteTask'; Click-ReleaseNode "//node[@resource-id='android:id/button1']"
    $final=Read-OwnSnapshot 'final'
    $descriptions=@($final.rows|ForEach-Object{$_[1]})
    $newRows=@($final.rows|Where-Object{$_[1] -ceq $third})
    if($final.rows.Count -ne 2 -or $descriptions -notcontains $edited -or $descriptions -contains $second -or $newRows.Count -ne 1 -or [long]$newRows[0][0] -le [long]$before.rows[1][0]){throw 'Insert/delete state or new ID invalid'}
    Complete-ReleasePhase 'insert-and-delete'
    Launch-Release; Assert-ReleaseText $edited; Assert-ReleaseText $third
    if((Read-ReleaseUi).SelectSingleNode("//node[@text='$second']")){throw 'Deleted task still shown'}
    Wait-ReleaseAlarm|Out-Null
    if((Invoke-ReleaseAdb @('shell','dumpsys','window')) -match 'Application Not Responding:'){throw 'ANR after upgrade'}
    $crash=Invoke-ReleaseAdb @('logcat','-b','crash','-d')
    if($crash -match 'Process: com\.vpe_soft\.intime\.intime,'){throw 'App crash recorded'}
    if((Get-FileHash $OldApkPath -Algorithm SHA256).Hash -ne $summary.oldApkSha256 -or (Get-FileHash $NewApkPath -Algorithm SHA256).Hash -ne $summary.newApkSha256){throw 'APK input changed'}
    Complete-ReleasePhase 'final-restart'
    $summary.status='PASSED'
} catch {
    $summary.status='FAILED'; $summary.error=$_.Exception.Message
    Write-Warning $summary.error
} finally {
    try {
        if($owned -and $installedHere){Invoke-ReleaseAdb @('uninstall',$package)|Out-Null}
        if($owned){
            foreach($path in $remoteSnapshots){Invoke-ReleaseAdb @('shell','su','0','rm','-f',$path)|Out-Null}
            if($probePushed){Invoke-ReleaseAdb @('shell','rm','-f','/data/local/tmp/intime-release-probe.jar','/data/local/tmp/intime-release-ui.xml')|Out-Null}
            foreach($path in $backupDevicePaths){Invoke-ReleaseAdb @('shell','rm','-f',$path)|Out-Null}
            $live=Get-Process -Id $state.processId -ErrorAction SilentlyContinue
            if($live -and $live.StartTime.ToUniversalTime().ToString('o') -eq $state.started){
                Invoke-ReleaseAdb @('emu','kill')|Out-Null
                $null=$live.WaitForExit(30000)
                if(Get-Process -Id $state.processId -ErrorAction SilentlyContinue){throw 'Owned AVD did not stop'}
                Remove-Item -LiteralPath (Join-Path $work 'emulator.json')
            }
        }
        $summary.cleanup=$true
    } catch {$summary.status='FAILED'; $summary.cleanupError=$_.Exception.Message}
    if($lock){$lock.Dispose()}
    $env:JAVA_HOME=$oldJavaHome
    $summary|ConvertTo-Json -Depth 6|Set-Content (Join-Path $run 'summary.json') -Encoding UTF8
}
Write-Output "$($summary.status): $($summary.passedPhases.Count)/9 phases. Report: $run"
if($summary.status -ne 'PASSED'){exit 1}
