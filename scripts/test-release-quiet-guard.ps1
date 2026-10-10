# Host-регрессии парсера тихих уведомлений без запуска приложения и эмулятора.
param([string[]]$NotificationSnapshots=@())
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$tokens=$null;$errors=$null
$ast=[System.Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'test-release-smoke.ps1'),[ref]$tokens,[ref]$errors)
if($errors.Count){throw 'Release script syntax errors'}
$definition=$ast.Find({param($node) $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Assert-ReleaseQuietNotification'},$true)
if(-not $definition){throw 'Quiet guard missing'}
# Загружаем только действительную функцию проверки, не запускаем release runner.
Invoke-Expression $definition.Extent.Text
$base=@'
NotificationRecord(pkg=com.vpe_soft.intime.intime Notification(vibrate=null sound=null defaults=0 flags=ONLY_ALERT_ONCE|AUTO_CANCEL groupKey=silent))
  flags=ONLY_ALERT_ONCE|AUTO_CANCEL
  pri=-1
  groupKey=0|com.vpe_soft.intime.intime|g:silent
  groupAlertBehavior=1
'@
$hex=$base.Replace('flags=ONLY_ALERT_ONCE|AUTO_CANCEL','flags=0x18')
$native=$base.Replace('flags=ONLY_ALERT_ONCE|AUTO_CANCEL','flags=ONLY_ALERT_ONCE|AUTO_CANCEL|SILENT').Replace('  groupKey=0|com.vpe_soft.intime.intime|g:silent', '  groupKey=null')
$cases=@(
    # На31 исходный record содержит hex0x18, тихую группу и остальные тихие
    # поля. Вызов реального guard должен принять его; физический звук не проверяется.
    @{name='api31-compat';api=31;record=$hex;accept=$true},
    # Такой же корректный старый формат на33 должен приниматься независимо
    # от отсутствия именованного SILENT; наличие группы обязательно.
    @{name='api33-compat';api=33;record=$hex;accept=$true},
    # На35 AndroidX-группа с null sound/vibrate и groupAlertBehavior1 допустима:
    # guard принимает record, не выдавая groupKey за отдельный нативный флаг.
    @{name='api35-compat';api=35;record=$base;accept=$true},
    # На35 отдельное flags со словом SILENT тоже допустимо без compat-группы.
    # Проверяем принятие второго поддерживаемого системного формата.
    @{name='api35-native';api=35;record=$native;accept=$true},
    # На36 отдельное поле flags содержит SILENT и все тихие поля присутствуют.
    # Настоящий guard должен принять этот именованный формат.
    @{name='api36-native';api=36;record=$native;accept=$true},
    # Регрессия: на36 слово silent осталось только в groupKey/header. Guard
    # обязан отвергнуть record, хотя прежний неограниченный regex его принимал.
    @{name='api36-group-is-not-native-flag';api=36;record=$base;accept=$false},
    # На35 нет ни отдельного SILENT, ни точной compat-группы; слово silent
    # осталось в header. Это не доказательство тихого механизма, ожидаем отказ.
    @{name='api35-header-only';api=35;record=$base.Replace('  groupKey=0|com.vpe_soft.intime.intime|g:silent','  groupKey=null');accept=$false},
    # На31 сохранены hex-флаги, но удалена тихая группа. Ожидаем отказ, чтобы
    # ONLY_ALERT_ONCE/AUTO_CANCEL сами по себе не считались подавлением звука.
    @{name='api31-group-missing';api=31;record=$hex.Replace('  groupKey=0|com.vpe_soft.intime.intime|g:silent','  groupKey=null');accept=$false},
    # На33 исчез бит ONLY_ALERT_ONCE из hex0x18; даже тихая группа не должна
    # скрыть нарушение требуемых флагов. Ожидаем отказ.
    @{name='api33-flags-missing';api=33;record=$hex.Replace('flags=0x18','flags=0x10');accept=$false},
    # К корректной записи36 добавлен ACK action. Тихий повтор не имеет ACK,
    # поэтому реальный guard обязан отвергнуть запись с actions=1.
    @{name='ack-action';api=36;record=($native+"`n  actions=1");accept=$false},
    # В корректной записи36 sound=null заменён URI. Проверяем отказ при
    # нарушении поля sound независимо от правильного флага SILENT.
    @{name='sound-present';api=36;record=$native.Replace('sound=null','sound=content://sound');accept=$false},
    # При priority0 вместо LOW(-1) guard обязан отвергнуть запись; остальные
    # тихие поля сохранены, чтобы изолировать проверяемое нарушение.
    @{name='priority-not-low';api=36;record=$native.Replace('pri=-1','pri=0');accept=$false},
    # При groupAlertBehavior0 вместо1 guard обязан отвергнуть запись; исходные
    # флаг, звук и priority корректны. Проверяется именно политика группы.
    @{name='group-policy-missing';api=36;record=$native.Replace('groupAlertBehavior=1','groupAlertBehavior=0');accept=$false}
)
$results=@()
foreach($case in $cases){
    $TestApi=$case.api
    $accepted=$true
    try { Assert-ReleaseQuietNotification $case.record } catch { $accepted=$false }
    if($accepted -ne $case.accept){throw "Unexpected quiet guard outcome: $($case.name)"}
    $results+=@{name=$case.name;api=$TestApi;status='PASSED'}
}
# Дополнительная сверка сохранённых активных records текущего device-этапа.
# API берём из свежего summary в том же каталоге; сохраняем только пути/счётчик.
# Эта проверка парсера снимков не заменяет device-наблюдение или WorkManager SUCCESS.
$snapshotsChecked=0
foreach($snapshot in $NotificationSnapshots){
    $path=(Resolve-Path -LiteralPath $snapshot).Path
    $summary=Get-Content (Join-Path ([IO.Path]::GetDirectoryName($path)) 'summary.json') -Raw | ConvertFrom-Json
    $TestApi=[int]$summary.testApi
    $text=Get-Content -LiteralPath $path -Raw
    $record=[regex]::Match($text,'(?ms)^\s*NotificationRecord\([^\r\n]*pkg=com\.vpe_soft\.intime\.intime [^\r\n]*\r?\n(?:(?!^\s*NotificationRecord\().)*')
    if(-not $record.Success){throw 'Active own record missing'}
    Assert-ReleaseQuietNotification $record.Value
    $snapshotsChecked++
}
[ordered]@{status='PASSED';tests=$results.Count;failures=0;results=$results;snapshotsChecked=$snapshotsChecked;notificationSnapshots=$NotificationSnapshots}|ConvertTo-Json -Depth 5
