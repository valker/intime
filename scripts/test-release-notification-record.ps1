# Host-регрессии границ активного NotificationRecord; эмулятор не запускается.
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$runnerPath=Join-Path $PSScriptRoot 'test-release-smoke.ps1'
# Runner выполняется Windows PowerShell 5.1 и содержит русские UI-селекторы.
# До загрузки функций проверяем UTF-8 BOM: без него этот host читает UTF-8 как
# локальную ANSI-кодировку, и поиск кнопок может падать до проверки приложения.
$bytes=[IO.File]::ReadAllBytes($runnerPath)
if($bytes.Length -lt 3 -or $bytes[0] -ne 0xEF -or $bytes[1] -ne 0xBB -or $bytes[2] -ne 0xBF){throw 'Release runner requires UTF-8 BOM for Windows PowerShell 5.1'}
$tokens=$null;$errors=$null
$ast=[System.Management.Automation.Language.Parser]::ParseFile($runnerPath,[ref]$tokens,[ref]$errors)
if($errors.Count){throw 'Release runner syntax errors'}
foreach($name in @('Get-ReleaseNotificationRecord','Assert-ReleaseQuietNotification')){
    $definition=$ast.Find({param($node)$node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq $name},$true)
    if(-not $definition){throw "Missing $name"}
    Invoke-Expression $definition.Extent.Text
}
$TestApi=33
$own=@'
    NotificationRecord(pkg=com.vpe_soft.intime.intime user=UserHandle{0} Notification(vibrate=null sound=null defaults=0x0 flags=0x18))
      flags=0x18
      pri=-1
      groupKey=0|com.vpe_soft.intime.intime|g:silent
      groupAlertBehavior=1
      when=123456789

      extras={
        android.text=String (Current task)
      }
'@
$prefix="Current Notification Manager state:`n  Notification List:`n"
$history="`n  Ranking Config:`n    actions={OLD_ACK}`n    pri=0`n    when=999999999"
$foreign="`n    NotificationRecord(pkg=other.app user=UserHandle{0})`n      actions=1`n      when=999999999"
$cases=@(
    # Последняя активная тихая запись содержит пустую строку и вложенные extras;
    # далее идёт история с ACK и чужими when/priority. Извлечение должно сохранить
    # текущие поля, исключить историю и позволить настоящему quiet guard принять запись.
    @{name='last-active-before-history';dump=$prefix+$own+$history;found=$true;quiet=$true},
    # После собственной записи следует чужая с ACK. Извлекаем только собственную:
    # чужая кнопка и timestamp не должны привести к отказу проверки тихого повтора.
    @{name='following-foreign-record';dump=$prefix+$own+$foreign+$history;found=$true;quiet=$true},
    # Собственная запись расположена после чужой. Поиск должен выбрать нужный
    # пакет, сохранить вложенный текст и пройти guard без заимствования чужих полей.
    @{name='preceding-foreign-record';dump=$prefix+$foreign+"`n"+$own+$history;found=$true;quiet=$true},
    # Дамп заканчивается сразу после собственной записи. EOF является допустимой
    # границей: запись и тихие поля должны сохраниться без обязательного следующего раздела.
    @{name='record-at-eof';dump=$prefix+$own;found=$true;quiet=$true},
    # ACK добавлен в саму активную запись, а не историю. Извлечение обязано
    # сохранить actions=1, после чего реальный quiet guard должен отвергнуть повтор.
    @{name='active-ack-rejected';dump=$prefix+$own+"`n      actions=1"+$history;found=$true;quiet=$false},
    # Есть только пакет с суффиксом .dev. Он не равен production package:
    # ожидаем отсутствие записи, чтобы debug-уведомление не подтверждало release.
    @{name='package-prefix-excluded';dump=$prefix+$own.Replace('pkg=com.vpe_soft.intime.intime ','pkg=com.vpe_soft.intime.intime.dev ');found=$false},
    # Собственная запись существует только вне активного Notification List.
    # Ожидаем отсутствие: архивное уведомление не является текущей доставкой.
    @{name='history-only-excluded';dump="  Ranking Config:`n"+$own;found=$false},
    # В активном списке две собственные записи. Проверка должна отказаться от
    # неоднозначного выбора, а не принять первую как доказательство нужной доставки.
    @{name='ambiguous-own-records';dump=$prefix+$own+"`n"+$own+$history;found=$false}
)
$results=@()
foreach($case in $cases){
    $record=Get-ReleaseNotificationRecord $case.dump
    if($record.Success -ne $case.found){throw "Unexpected extraction: $($case.name)"}
    if($case.found){
        if($record.Value -notmatch 'when=123456789' -or $record.Value -notmatch 'android.text=String \(Current task\)' -or $record.Value -match '999999999|OLD_ACK|pkg=other.app'){throw "Record boundary mismatch: $($case.name)"}
        $quiet=$true
        try{Assert-ReleaseQuietNotification $record.Value}catch{$quiet=$false}
        if($quiet -ne $case.quiet){throw "Unexpected quiet outcome: $($case.name)"}
    }elseif($record.Value){throw 'Missing/ambiguous extraction must be empty'}
    $results+=@{name=$case.name;status='PASSED'}
}
# Проверяются формат и границы dumpsys, а не доставка, звук динамика или запуск worker.
[ordered]@{status='PASSED';tests=$results.Count;failures=0;results=$results}|ConvertTo-Json -Depth 5
