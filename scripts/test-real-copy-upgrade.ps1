<#
Обновляет логическую копию реальных задач из явно переданного JSON25 только на Intime_Test_API35.
Перед запуском: test.ps1 -Mode device -TestApi 35 -Filter ...ScreenPlatformTest
-Offline -KeepEmulator -EmulatorGpu host -DisableEmulatorVulkan -EmulatorRamMb 4096.
Не переподписывает APK. Seed schema5 сохраняет все восемь полей/ID JSON. Читает только
копии базы собственной новой установки на root-capable AOSP AVD, учитывая WAL.
Не читает пользовательские main*.db и не адресует телефон. Нужен локальный Python
со стандартным sqlite3. После прогона удаляет свою установку и останавливает AVD.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$BackupJsonPath,
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
$run = Join-Path $work ('runs\' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-real-copy-upgrade-' + [guid]::NewGuid().ToString('N').Substring(0,8))
$null = New-Item -ItemType Directory -Path $run
$sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$tools = Join-Path $sdk 'build-tools\35.0.0'
$adb = Join-Path $sdk 'platform-tools\adb.exe'
$serial = 'emulator-5580'
$package = 'com.vpe_soft.intime.intime'
$commandNumber = 0
$summary = [ordered]@{status='RUNNING'; passedPhases=@(); reports=$run; testApi=35; serial=$serial; phoneUsed=$false; signatureScope='original Google Play APKs'; dataScope='logical task copy from explicit JSON, not original SQLite/preferences'; cleanup=$false; expectedNewVersionCode=$ExpectedNewVersionCode; expectedNewVersionName=$ExpectedNewVersionName}
$lock=$null; $owned=$false; $installedHere=$false; $probePushed=$false
$remoteSnapshots=@(); $backupDevicePaths=@(); $backupDevicePath=$null; $state=$null
$oldJavaHome=$env:JAVA_HOME

# Импортируем только выбранные определения UI-помощников из AST, не выполняя
# основную часть release runner с установкой/удалением. Подпись и владение AVD
# этот сценарий проверяет отдельно. Изменения помощников проверяются device-прогоном.
$tokens=$null; $errors=$null
$ast=[Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'test-release-smoke.ps1'),[ref]$tokens,[ref]$errors)
if($errors.Count){throw ($errors|Out-String)}
foreach($helper in @('Invoke-ReleaseTool','Invoke-ReleaseAdb','Read-ReleaseUi','Wait-ReleaseNode','Click-ReleaseNode','Click-ReleaseId','Assert-ReleaseText','Assert-ReleaseListTask','Launch-Release','Read-ReleaseAlarm','Wait-ReleaseAlarm','Complete-ReleasePhase','Focus-ReleaseDescription','Close-ReleaseKeyboard','Open-ReleaseDetails','Open-ReleaseEdit')){
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

# Через SAF экспортируем только установленную нами копию задач. После нажатия Save копируем созданный
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
    if(($backup.tables.tasks.columns -join ',') -ne 'id,description,interval,amount,next_alarm,next_caution,last_ack,quant' -or $backup.tables.tasks.rows.Count -ne $Expected.rows.Count){throw 'Unexpected export contract/count'}
    $rows=@($backup.tables.tasks.rows|Sort-Object {[long]$_[0]})
    for($row=0;$row -lt $Expected.rows.Count;$row++){for($column=0;$column -lt 8;$column++){if([string]$rows[$row][$column] -cne [string]$Expected.rows[$row][$column]){throw 'Export differs from migrated tasks'}}}
    if($OldVersion){
        $summary.oldExport=$local
        $summary.oldExportMetadata=$backup.meta
        $summary.oldExportSha256=(Get-FileHash $local -Algorithm SHA256).Hash
    }else{$summary.exportSha256=(Get-FileHash $local -Algorithm SHA256).Hash}
    Invoke-ReleaseAdb @('shell','input','keyevent','4')|Out-Null
}

try {
    $BackupJsonPath=(Resolve-Path -LiteralPath $BackupJsonPath).Path
    if([IO.Path]::GetExtension($BackupJsonPath) -ine '.json'){throw 'Explicit JSON export required'}
    if((Get-Item -LiteralPath $BackupJsonPath).Length -gt 10MB){throw 'JSON exceeds 10 MiB limit'}
    $summary.sourceSha256=(Get-FileHash -LiteralPath $BackupJsonPath -Algorithm SHA256).Hash
    $copy=Join-Path $run 'private-backup.json'
    Copy-Item -LiteralPath $BackupJsonPath -Destination $copy
    if((Get-FileHash $copy -Algorithm SHA256).Hash -ne $summary.sourceSha256){throw 'Input changed during copy'}
    $seed=Join-Path $run 'seed.sqlite'
    $seedReport=Invoke-ReleaseTool $PythonPath @((Join-Path $PSScriptRoot 'real_copy_fixture.py'),'seed',$copy,$seed) 'seed-report.json' | ConvertFrom-Json
    $summary.taskCount=$seedReport.taskCount
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
    if((Invoke-ReleaseAdb @('shell','su','0','id')) -notmatch 'uid=0'){throw 'AOSP root required for own logical-copy snapshots'}
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
    # Запись разрешена только в нашу новую установку на проверенном AOSP AVD.
    # Не запускаем старый UI до переноса: нет исходной личной базы или WAL.
    $remoteSeed='/data/local/tmp/intime-real-seed-'+[guid]::NewGuid().ToString('N')+'.sqlite'
    $remoteSnapshots+=$remoteSeed
    Invoke-ReleaseAdb @('push',$seed,$remoteSeed)|Out-Null
    $dbdir="/data/user/0/$package/databases"
    Invoke-ReleaseAdb @('shell','su','0','mkdir','-p',$dbdir)|Out-Null
    Invoke-ReleaseAdb @('shell','su','0','cp',$remoteSeed,"$dbdir/main")|Out-Null
    foreach($target in @($dbdir,"$dbdir/main")){
        Invoke-ReleaseAdb @('shell','su','0','chown',"$($beforeIdentity.uid):$($beforeIdentity.uid)",$target)|Out-Null
        Invoke-ReleaseAdb @('shell','su','0','restorecon',$target)|Out-Null
    }
    Invoke-ReleaseAdb @('shell','su','0','chmod','771',$dbdir)|Out-Null
    Invoke-ReleaseAdb @('shell','su','0','chmod','660',"$dbdir/main")|Out-Null
    Complete-ReleasePhase 'logical-copy-seeded'
    Invoke-ReleaseAdb @('shell','am','start','-W','-n',"$package/com.vpe_soft.intime.intime.activity.MainActivity")|Out-Null
    Wait-ReleaseNode "//node[@resource-id='${package}:id/add_task']"|Out-Null
    $before=Read-OwnSnapshot 'before'
    Invoke-ReleaseTool $PythonPath @((Join-Path $PSScriptRoot 'real_copy_fixture.py'),'compare5',$copy,(Join-Path $run 'before.sqlite')) 'before-compare.json'|Out-Null
    Complete-ReleasePhase 'old-values-and-schema'
    Export-OwnTasks $before -OldVersion
    Complete-ReleasePhase 'old-json-export'
    Invoke-ReleaseAdb @('shell','am','force-stop',$package)|Out-Null
    $output=Invoke-ReleaseAdb @('install','-r',$NewApkPath)
    if($output -notmatch '(?m)^Success\r?$'){throw 'Update did not report Success'}
    $afterIdentity=Read-InstalledIdentity $ExpectedNewVersionCode
    if($beforeIdentity.uid -ne $afterIdentity.uid -or $beforeIdentity.firstInstallTime -ne $afterIdentity.firstInstallTime){throw 'Installation identity changed'}
    $summary.installationIdentity=$afterIdentity
    Complete-ReleasePhase 'install-r-identity'
    Launch-Release
    $after=Read-OwnSnapshot 'after'
    $comparison=Invoke-ReleaseTool $PythonPath @((Join-Path $PSScriptRoot 'real_copy_fixture.py'),'compare6',$copy,(Join-Path $run 'after.sqlite')) 'after-compare.json'|ConvertFrom-Json
    $summary.notifiedCount=$comparison.notifiedCount
    Complete-ReleasePhase 'migrated-values-and-schema'
    Export-OwnTasks $after
    Complete-ReleasePhase 'new-json-export'
    $restart=Read-OwnSnapshot 'restart'
    Invoke-ReleaseTool $PythonPath @((Join-Path $PSScriptRoot 'real_copy_fixture.py'),'compare6',$copy,(Join-Path $run 'restart.sqlite')) 'restart-compare.json'|Out-Null
    Launch-Release
    Complete-ReleasePhase 'process-restart'
    # CRUD/ACK проверяем только на дополнительно созданной синтетической строке.
    # Существующие 17 (или другое число) задач копии намеренно не редактируем.
    $probe='RealCopy-Probe-'+[guid]::NewGuid().ToString('N').Substring(0,8)
    Click-ReleaseId 'fab_add_task'; Focus-ReleaseDescription
    Invoke-ReleaseAdb @('shell','input','text',$probe)|Out-Null
    Close-ReleaseKeyboard; Click-ReleaseId 'spinner_interval'; Click-ReleaseNode "//node[@text='Час' or @text='Hours']"; Click-ReleaseId 'btn_save_task'
    Assert-ReleaseListTask $probe
    Open-ReleaseDetails $probe; Click-ReleaseId 'btnAckTask'
    $probeSnapshot=Read-OwnSnapshot 'probe-ack'
    $probeRows=@($probeSnapshot.rows|Where-Object{$_[1] -ceq $probe})
    $maxId=($before.rows|ForEach-Object{[long]$_[0]}|Measure-Object -Maximum).Maximum
    if($probeSnapshot.rows.Count -ne $before.rows.Count+1 -or $probeRows.Count -ne 1 -or [long]$probeRows[0][0] -le $maxId -or [long]$probeRows[0][6] -le 0){throw 'Probe insert/ACK failed'}
    Launch-Release; Assert-ReleaseListTask $probe; Open-ReleaseDetails $probe; Click-ReleaseId 'btnDeleteTask'; Click-ReleaseNode "//node[@resource-id='android:id/button1']"
    # Удаление выполняется асинхронно; finish() экрана не доказывает commit.
    # До force-stop подтверждаем через новый экспорт точные исходные строки:
    # синтетический probe отсутствует, все реальные задачи сохранены.
    Wait-ReleaseNode "//node[@resource-id='${package}:id/fab_add_task']"|Out-Null
    Export-OwnTasks $after
    $final=Read-OwnSnapshot 'final'
    Invoke-ReleaseTool $PythonPath @((Join-Path $PSScriptRoot 'real_copy_fixture.py'),'compare6',$copy,(Join-Path $run 'final.sqlite')) 'final-compare.json'|Out-Null
    Complete-ReleasePhase 'probe-insert-ack-delete'
    Launch-Release
    if((Invoke-ReleaseAdb @('shell','dumpsys','window')) -match 'Application Not Responding:'){throw 'ANR after upgrade'}
    $crash=Invoke-ReleaseAdb @('logcat','-b','crash','-d')
    if($crash -match 'Process: com\.vpe_soft\.intime\.intime,'){throw 'App crash recorded'}
    $last=Read-OwnSnapshot 'last'
    Invoke-ReleaseTool $PythonPath @((Join-Path $PSScriptRoot 'real_copy_fixture.py'),'compare6',$copy,(Join-Path $run 'last.sqlite')) 'last-compare.json'|Out-Null
    if((Get-FileHash -LiteralPath $BackupJsonPath -Algorithm SHA256).Hash -ne $summary.sourceSha256){throw 'Original JSON changed'}
    foreach($artifactInput in @(@{path=$OldApkPath;hash=$summary.oldApkSha256},@{path=$NewApkPath;hash=$summary.newApkSha256})){
        if((Get-FileHash $artifactInput.path -Algorithm SHA256).Hash -ne $artifactInput.hash){throw 'APK input changed'}
    }
    Complete-ReleasePhase 'final-restart-and-inputs'
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
