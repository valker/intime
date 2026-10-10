# Проверки настоящего parser трёх служб через AST, без adb или изменения питания.
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$tokens=$null;$errors=$null
$ast=[System.Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'test-natural-light-idle.ps1'),[ref]$tokens,[ref]$errors)
if($errors.Count){throw ($errors|Out-String)}
$function=$ast.Find({param($node) $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Get-LightIdleObservation'},$true)
Invoke-Expression $function.Extent.Text
$idle="mLightEnabled=true mDeepEnabled=true`nmForceIdle=false`nmScreenOn=false`nmCharging=false`nmState=INACTIVE mLightState=IDLE"
$power="mLightDeviceIdleMode=true`nmIsPowered=false`nmWakefulness=Asleep"
$battery="AC powered: false`nUSB powered: false`nWireless powered: false"
$cases=@(
    # Экран выключен, батарея без зарядки, оба контроллера согласны с IDLE.
    # Parser обязан подтвердить вход и корректную подготовку без force-idle.
    @{name='idle-agreement';idle=$idle;power=$power;battery=$battery;expected=$true;reject=$false},
    # Экран неинтерактивен в Dozing, остальные службы подтверждают light IDLE
    # без зарядки. Этот допустимый screen-off вариант также даёт entryObserved,
    # не требуя перехода PowerManager именно в Asleep.
    @{name='dozing-screen';idle=$idle;power=$power.Replace('mWakefulness=Asleep','mWakefulness=Dozing');battery=$battery;expected=$true;reject=$false},
    # Контроллер ACTIVE при остальных готовых условиях. Вход ещё не произошёл:
    # даже PowerManager=true сам по себе не должен дать entryObserved=true.
    @{name='active';idle=$idle.Replace('mLightState=IDLE','mLightState=ACTIVE');power=$power;battery=$battery;expected=$false;reject=$false},
    # Окно maintenance является отдельным состоянием, а не light IDLE.
    # Parser обязан оставить entryObserved=false при совпадении остальных полей.
    @{name='maintenance';idle=$idle.Replace('mLightState=IDLE','mLightState=IDLE_MAINTENANCE');power=$power;battery=$battery;expected=$false;reject=$false},
    # Контроллер IDLE, но PowerManager ещё не включил light режим. Не считаем
    # такой несогласованный снимок подтверждением естественного входа.
    @{name='power-disagrees';idle=$idle;power=$power.Replace('mLightDeviceIdleMode=true','mLightDeviceIdleMode=false');battery=$battery;expected=$false;reject=$false},
    # Подключённая батарея нарушает условия входа. Независимый BatteryService
    # обязан запретить PASS даже при IDLE в двух остальных службах.
    @{name='charging-battery';idle=$idle;power=$power;battery=$battery.Replace('AC powered: false','AC powered: true');expected=$false;reject=$false},
    # Force idle уже включён: parser обязан отказать исключением, не выдавая
    # принудительный переход за штатное естественное наблюдение.
    @{name='forced';idle=$idle.Replace('mForceIdle=false','mForceIdle=true');power=$power;battery=$battery;expected=$false;reject=$true},
    # BatteryService updates остановлены симуляцией. Parser обязан отказать;
    # emulator console power и dumpsys battery unplug не смешиваются в сценарии.
    @{name='battery-simulation';idle=$idle;power=$power;battery="UPDATES STOPPED`n$battery";expected=$false;reject=$true},
    # Обязательное поле PowerManager отсутствует: неизвестный формат нельзя
    # трактовать как false/успех. Требуем исключение до утверждения о входе.
    @{name='unknown-format';idle=$idle;power='unknown';battery=$battery;expected=$false;reject=$true}
)
$results=@()
foreach($case in $cases){
    $rejected=$false;$observation=$null
    try {$observation=Get-LightIdleObservation $case.idle $case.power $case.battery} catch {$rejected=$true}
    if($rejected -ne $case.reject -or (-not $rejected -and $observation.entryObserved -ne $case.expected)){throw "Parser case failed: $($case.name)"}
    $results+=@{name=$case.name;status='PASSED'}
}
$result=@{status='PASSED';tests=$cases.Count;failures=0;results=$results}
$result|ConvertTo-Json -Depth 4
