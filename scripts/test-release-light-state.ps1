# Регрессии реальной классификации light delivery, извлечённой через AST.
# Проверяется отличие maintenance от IDLE; adb/питание/приложение не меняются.
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$tokens=$null;$errors=$null
$ast=[System.Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'test-release-smoke.ps1'),[ref]$tokens,[ref]$errors)
if($errors.Count){throw ($errors|Out-String)}
$function=$ast.Find({param($node)$node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Get-ReleaseLightDeliveryState'},$true)
Invoke-Expression $function.Extent.Text
$cases=@(
    # После входа экран/питание готовы, controller IDLE согласован с PowerManager.
    # Exact-режим должен разрешить продолжение и вернуть именно IDLE.
    @{name='exact-idle';state='IDLE';power=$true;ready=$true;forced=$false;maintenance=$false;expected='IDLE'},
    # Те же согласованные условия для fallback: разрешение maintenance не
    # переименовывает настоящий IDLE и не требует обязательного maintenance.
    @{name='fallback-idle';state='IDLE';power=$true;ready=$true;forced=$false;maintenance=$true;expected='IDLE'},
    # Controller сообщает maintenance, PowerManager light=false, экран/питание
    # остаются готовы. Fallback разрешает его отдельно, возвращая MAINTENANCE.
    @{name='fallback-maintenance';state='IDLE_MAINTENANCE';power=$false;ready=$true;forced=$false;maintenance=$true;expected='MAINTENANCE'},
    # Exact-режим требует сохранения IDLE. Даже согласованное maintenance
    # нельзя принять за продолжение непрерывного sampled IDLE: INVALID.
    @{name='exact-rejects-maintenance';state='IDLE_MAINTENANCE';power=$false;ready=$true;forced=$false;maintenance=$false;expected='INVALID'},
    # Controller IDLE при PowerManager light=false несогласован. Fallback
    # не должен засчитать такой промежуточный снимок: ожидается INVALID.
    @{name='idle-power-disagrees';state='IDLE';power=$false;ready=$true;forced=$false;maintenance=$true;expected='INVALID'},
    # Controller maintenance при PowerManager light=true тоже несогласован:
    # недопустимо называть это доказанным maintenance, ожидается INVALID.
    @{name='maintenance-power-disagrees';state='IDLE_MAINTENANCE';power=$true;ready=$true;forced=$false;maintenance=$true;expected='INVALID'},
    # Потеря подготовки экрана/питания делает IDLE недостаточным для проверки.
    # Даже два согласованных поля режима при ready=false дают INVALID.
    @{name='preparation-lost';state='IDLE';power=$true;ready=$false;forced=$false;maintenance=$true;expected='INVALID'},
    # Принудительный idle не соответствует штатному входу. Защитная проверка
    # классификатора обязана отвергнуть forced=true даже при остальных условиях.
    @{name='forced-rejected';state='IDLE';power=$true;ready=$true;forced=$true;maintenance=$true;expected='INVALID'},
    # Возврат controller в ACTIVE после подтверждения входа нельзя принять
    # за maintenance или успешную доставку: неизвестный переход даёт INVALID.
    @{name='active-rejected';state='ACTIVE';power=$false;ready=$true;forced=$false;maintenance=$true;expected='INVALID'}
)
$results=@()
foreach($case in $cases){
    $snapshot=@{lightState=$case.state;powerLightIdle=$case.power;preparationReady=$case.ready;forceIdle=$case.forced}
    $actual=Get-ReleaseLightDeliveryState $snapshot $case.maintenance
    if($actual -ne $case.expected){throw "State case failed: $($case.name): $actual"}
    $results+=@{name=$case.name;status='PASSED'}
}
@{status='PASSED';tests=$cases.Count;failures=0;results=$results}|ConvertTo-Json -Depth 4
