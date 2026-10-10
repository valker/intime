# Host-регрессии настоящей функции parser без приложения, adb или эмулятора.
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$run=Join-Path $root ('.test-tools/runs/'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'-activity-record-host')
New-Item -ItemType Directory -Path $run | Out-Null
$tokens=$null;$errors=$null
$ast=[System.Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'test-release-smoke.ps1'),[ref]$tokens,[ref]$errors)
if($errors.Count){throw 'Runner parser failed'}
$function=$ast.Find({param($node) $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Read-ReleaseResumed'},$true)
Invoke-Expression $function.Extent.Text
function Invoke-ReleaseAdb {return $script:fixtureDump}
$component='com.vpe_soft.intime.intime/.activity.MainActivityV2'
$cases=@(
    # Условия: формат API33 закрывает скобку перед task. Читаем запись и требуем
    # точные component/token/task без включения скобки в имя компонента.
    @{name='api33';dump="topResumedActivity=ActivityRecord{abc u0 $component} t24}";valid=$true},
    # Условия: формат API35/36 закрывает скобку после task. Те же три поля
    # обязаны совпасть с ожидаемыми, сохраняя поддержку прежнего формата.
    @{name='api35';dump="mResumedActivity: ActivityRecord{abc u0 $component t24}";valid=$true},
    # Условия: две строки описывают один ActivityRecord. Чтение обязано принять
    # единственный token, а не ошибочно объявить дублирование неоднозначностью.
    @{name='same-token';dump="topResumedActivity=ActivityRecord{abc u0 $component t24}`nmResumedActivity: ActivityRecord{abc u0 $component t24}";valid=$true},
    # Условия: две resumed-записи имеют разные token. Parser обязан отказать;
    # такой dump не доказывает возврат к единственному прежнему экрану.
    @{name='ambiguous';dump="topResumedActivity=ActivityRecord{abc u0 $component t24}`nmResumedActivity: ActivityRecord{def u0 $component t25}";valid=$false},
    # Условия: активен чужой пакет. Parser обязан отказать, не принимая launcher
    # за resumed Activity приложения; этот случай не вводит касания.
    @{name='other-package';dump='topResumedActivity=ActivityRecord{abc u0 com.example/.MainActivity t24}';valid=$false}
)
$results=@()
foreach($case in $cases){
    $script:fixtureDump=$case.dump
    $record=$null;$rejected=$false
    try {$record=Read-ReleaseResumed $case.name} catch {if($_.Exception.Message -ne 'Unique resumed release ActivityRecord missing'){throw};$rejected=$true}
    if($case.valid){
        if($rejected -or $record.component -ne $component -or $record.token -ne 'abc' -or $record.task -ne '24'){throw "Incorrect parser result: $($case.name)"}
    } elseif(-not $rejected){throw "Invalid dump accepted: $($case.name)"}
    $results+=@{name=$case.name;status='PASSED'}
}
$summary=@{status='PASSED';tests=$cases.Count;failures=0;results=$results;reports=$run}
$summary|ConvertTo-Json -Depth 4|Set-Content (Join-Path $run 'summary.json') -Encoding UTF8
$summary|ConvertTo-Json -Depth 4
