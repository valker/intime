# Running tests

### Проверка доступа Play API

Настройка сервисного аккаунта и команды — [PLAY_API.md](PLAY_API.md).
`scripts/test-play-api.ps1` проверяет только OAuth и generatedapks.list для InTime;
ключ вне проекта, без upload/edits/publish или устройств. После исправления
приглашения OAuth и generatedapks.list APK 26 PASSED, сертификат подтверждён.
Host-тесты `scripts/play-api.test.mjs`
запускаются существующим Node 24 в одном процессе; свежий результат 13/13.
Новые зависимости и Android-сборка для этих проверок не нужны.

### Обновление Play 25 → 27, 9 октября 2026

`test-play-upgrade.ps1` теперь требует явных `ExpectedNewVersionCode` и
`ExpectedNewVersionName`. Они используются для независимого APK preflight и
проверки установленного кода после install -r; исходная версия остаётся 25/1.1.12.
Сертификат по-прежнему только App signing key, AVD только Intime_Test_API35.
Подготовка прошла 18 host-случаев (9 snapshot, 7 identity, 2 forwarding) и четыре
реальных APK guard-проверки: Play 25/26 приняты, 26 при ожидании 27 и Upload-key
APK 27 отклонены. Это проверки runner/preflight, не новый device pass.
Позднее APK27 получен через API, подпись и DEX проверены; синтетический
API35 UI3/3 и Play25→27 upgrade9/9 прошли. Результаты — DEVICE_TEST_RESULTS.md,
получение файла — PLAY_API.md. Host-проверки выше не заменяют этот device-прогон.

После получения `C:/Users/Valentin/Downloads/27.apk` и успешного bootstrap API 35:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-play-upgrade.ps1 -OldApkPath 'C:/Users/Valentin/Downloads/25.apk' -NewApkPath 'C:/Users/Valentin/Downloads/27.apk' -ExpectedNewVersionCode 27 -ExpectedNewVersionName '2.0.0-rc3' -PythonPath 'C:/Users/Valentin/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe'
```

### Обновление исходных Play APK 25 → 26 на API 35

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test.ps1 -Mode device -TestApi 35 -Filter com.vpe_soft.intime.intime.activity.ScreenPlatformTest -Offline -KeepEmulator -EmulatorGpu host -DisableEmulatorVulkan -EmulatorRamMb 4096
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-play-upgrade.ps1 -OldApkPath 'C:/Users/Valentin/Downloads/25.apk' -NewApkPath 'C:/Users/Valentin/Downloads/26.apk' -ExpectedNewVersionCode 26 -ExpectedNewVersionName '2.0.0-rc2' -PythonPath 'C:/Users/Valentin/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe'
```

Вторую команду выполнять только после успешной первой. Нужны исходные universal
APK 25/1.1.12 и 26/2.0.0-rc2 с App signing key, root-capable AOSP API 35 и локальный
Python с sqlite3 (путь обязательный; при смене runtime уточнить фактический путь).
Runner проверяет ownership/API/user 0/отсутствие неизвестной production-установки,
создаёт две задачи старым UI и делает install -r без clear/uninstall между версиями.
Согласованные копии собственной базы/WAL/SHM читает mode=ro; после сравнения полей,
JSON, CRUD/ACK/restart удаляет свою установку/probe/файлы и останавливает AVD.
Личный телефон, Pixel_8 и пользовательские main*.db не используются.
9 октября первый upgrade прошёл 8/8; после добавления старого SAF экспорта
свежий UI 3/3 и расширенный upgrade 9/9 прошли. Оба экспорта сверяются по восьми
полям/ID со снимками, а метаданные проверяются для своего формата отдельно.
9 snapshot + 5 identity host-случаев и SQLite/WAL reader прошли на первом шаге.
Первые инфраструктурный/два runner-отказа сохранены.
Это синтетические задачи, не проверка реальных данных/уведомлений/reboot/всех API.
Затем runtime BackupImport исправлен для backup_version=1. Общая неизменённая
фикстура `app/src/sharedTest/resources/play25-backup.json` экспортирована старым
APK из своих синтетических задач и включается только в JVM/Android-тесты.
Play25BackupImportTest 4/4, полный local 101/101 и ImportTransactionPlatformTest
на API 35 4/4 прошли. Для повторения использовать test.ps1 -Mode local -Offline
и -Mode device -TestApi 35 -Filter com.vpe_soft.intime.intime.import_export.ImportTransactionPlatformTest
с указанной конфигурацией GPU/Vulkan/RAM. Native тестирует Room в памяти,
без UI-импорта и AlarmManager; скачанный 26.apk исправления не содержит.
Отчёт и ограничения — DEVICE_TEST_RESULTS.md/UPGRADE.md.

### Проверка подписи Play APK до установки

`scripts/verify-play-apk.ps1` читает только локальный APK. `ApkPath`,
`ExpectedVersionCode` и `ExpectedVersionName` обязательны; для текущего кандидата
передать 27 и 2.0.0-rc3; для исторического APK 26 — 26 и 2.0.0-rc2.
Нужны JDK 17 и SDK build-tools 35.0.0 (пути переопределяются).
Для старой скачанной Play-версии разрешён код 25 с именем 1.1.12; это не разрешение
собрать или загрузить новую production-версию с уже использованным кодом.
apksigner verify и точные метаданные должны пройти; Upload key отклоняется,
принимается только известный App signing certificate. Отчёт — `.test-tools/runs`.
Ни adb, ни установки, ни чтения пользовательской базы нет. PASSED не заменяет
device/upgrade-тесты. Порядок получения APK и ограничения — UPGRADE.md.
9 октября 11 host-случаев прошли; локальный Upload-key APK ожидаемо отклонён.
JUnit и device-тесты на этом шаге не запускались: runtime приложения не менялся.

The Windows runner works with Windows PowerShell 5.1 and JDK 17. Android Studio
does not need to be open. Use the repository's Gradle Wrapper (8.11.1), AGP 8.9.1
and installed compile SDK 36. Device API 24/31/33/35/36 is independent of compile/target SDK.

PowerShell scripts containing Russian strings/comments must retain UTF-8 with BOM
for Windows PowerShell 5.1. Saving without BOM can corrupt UI selectors before
any application checks run; such a runner failure is not an application failure.
See [PowerShell character encoding](https://learn.microsoft.com/en-us/powershell/module/microsoft.powershell.core/about/about_character_encoding?view=powershell-5.1).

### Диагностика графического backend эмулятора

Для существующих выделенных AVD можно явно выбрать host GPU и отключить
Vulkan, не сбрасывая данные и не меняя системный образ:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test.ps1 -Mode device -TestApi 36 -Filter com.vpe_soft.intime.intime.activity.ScreenPlatformTest -Offline -EmulatorGpu host -DisableEmulatorVulkan -EmulatorRamMb 4096 -BootTimeoutSeconds 600 -KeepEmulator
```

Это диагностический вариант, описанный в
[Android Emulator troubleshooting](https://developer.android.com/studio/run/emulator-troubleshooting),
а не установленная причина или универсальное исправление SystemUI ANR.
Флаг добавляет `-feature -Vulkan` только к новому собственному процессу.
В summary записывается `emulatorVulkanDisabled`, в записи владения —
`vulkanDisabled`. При повторном использовании runner сверяет явно запрошенный
режим с записью запуска; смена требует остановки собственного AVD. Старая
запись без поля означает неизвестный режим, а не подтверждённый false.
Без нового параметра режим нового процесса остаётся прежним. Проверки
ANR/FATAL EXCEPTION не отключаются. Результаты конкретных прогонов см.
DEVICE_TEST_RESULTS.md.

### Подготовка API 24

Runner поддерживает отдельный `Intime_Test_API24`, порт `5588`, образ
`system-images;android-24;default;x86_64`, 2 CPU/2048 МБ/software GPU.
Его автономное создание/использование требует включения этого AVD в разрешённый
список AGENTS.md по разрешению пользователя. Пока образ не установлен и
запуск на API 24 не выполнен; поддержка в скрипте не является проверкой на устройстве.
Пользователь ограничил новый AVD дополнительными 100 МБ. Архив AOSP API 24 x86_64
rev 8 уже занимает 419261998 байт до распаковки; текущая установка не разрешена
этим условием. Не устанавливать образ/не создавать AVD без изменения ограничения
или подходящего уже имеющегося окружения.

После разрешения и установки образа:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test.ps1 -Mode device -TestApi 24 -Filter com.vpe_soft.intime.intime.activity.ScreenPlatformTest -Offline -KeepEmulator
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-release-smoke.ps1 -TestApi 24 -ApkPath '.test-tools/runs/release-permissions-20261003-225632/intime-25-2.0.0-rc1-r8.apk' -RebootOnly
```

ScreenPlatformTest использует AndroidX WindowInsetsCompat и Display.getRealSize
на API 24–29 вместо WindowMetrics (API 30+). На API 24 release runner пока
принимает только RebootOnly: семь фаз CRUD/JSON и reboot. Полный smoke отклоняется
до установки APK: notification channels появились на API 26, системный экран
уведомлений и его dumpsys на API 24 ещё нужно адаптировать. Новый image/AVD
займёт дополнительное место; личный Pixel_8 и телефон не используются.

## Commands

### Доставка release/R8 при запрете exact alarm (API 31/33/35/36)

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test.ps1 -Mode device -TestApi 36 -Filter com.vpe_soft.intime.intime.activity.ScreenPlatformTest -Offline -EmulatorGpu host -EmulatorRamMb 4096 -KeepEmulator
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-release-smoke.ps1 -TestApi 36 -ApkPath '.test-tools/runs/release-icons-20261004-221247/intime-25-2.0.0-rc1-icons.apk' -InexactOnly
```

InexactOnly работает на выделенных API 31/33/35/36 и не совмещается с RebootOnly
или PeriodicOnly. Совместное использование с DozeOnly описано ниже. 11 host-фаз включают CRUD/JSON, reboot, самостоятельную
доставку fallback и ACK из уведомления с перезапуском приложения. Runner задаёт
SCHEDULE_EXACT_ALARM=deny только собственной новой production-установке, проверяет
положительное окно собственного AlarmReceiver и совпадение срока. До доставки
не запускает Activity или receiver вручную. Quiet worker без ACK не засчитывается:
требуются запись AlarmReceiver в logcat и уведомление задачи с action ACK.
После ACK/restart запрет сохраняется, будущий alarm также имеет положительное окно.

Ожидание ограничено 15 минутами: тайм-аут означает INCONCLUSIVE, а разрыв host-
опросов более 180 секунд или перевод часов назад — INFRASTRUCTURE_INTERRUPTED.
Это проверка обычной доставки на активном эмуляторе; Doze, квоты и максимальная
задержка Android ею не подтверждаются. Appop исчезает при удалении тестовой
установки в finally. Наличие режима само по себе не означает успешный device-прогон;
свежие результаты и ограничения записываются в DEVICE_TEST_RESULTS.md.

Успешный прогон 6 октября использовал существующий профиль API 36 с 2 CPU,
host GPU/4096 МБ. Число CPU хранится в config.ini проектного AVD; после системных
ANR оно уменьшено с 8 до 2 при остановленном эмуляторе. Команда выше не меняет
CPU существующего профиля. Данные AVD сохранены; причина ANR пока не доказана.

### Тихий periodic worker в production release/R8 (API 31/33/35/36)

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test.ps1 -Mode device -TestApi 36 -Filter com.vpe_soft.intime.intime.activity.ScreenPlatformTest -Offline -KeepEmulator
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-release-smoke.ps1 -TestApi 36 -ApkPath '.test-tools/runs/release-icons-20261004-221247/intime-25-2.0.0-rc1-icons.apk' -PeriodicOnly -PeriodicWaitMinutes 65
```

Для двух последовательных естественных тихих повторов добавьте `-PeriodicRepeats 2`.
`PeriodicWaitMinutes` задаёт общий срок ожидания обоих повторов. Второй `when`
должен быть минимум на 15 минут позже первого; одинаковый record не засчитывается
повторно. Проверяется тишина каждого уведомления и минимум две записи SUCCESS
worker после смерти процесса. Между повторами JobScheduler не опрашивается;
снимки обоих уведомлений сохраняются отдельно. Такой запуск содержит 11 фаз.

В PeriodicOnly снимки `periodic-diagnostic-{before,latest,after}-*.log`
содержат power, deviceidle, battery и `/proc/uptime`. Полная очередь JobScheduler
сохраняется только в before/after, вне интервала ожидания: установленный API 36
в `dumpInternal` вызывает пересчёт Ready, который может менять TimeController.
Промежуточный dumpsys jobscheduler способен влиять на проверяемое планирование;
его не следует выполнять во время natural-прогона. Before снимается
после завершения процесса, latest обновляется раз в пять минут, after — после
ожидания, в том числе при тайм-ауте. Фиксированные имена ограничивают число
файлов; отдельно сохраняются время и fingerprint сборки Android. Настройки не
меняются; stateful scheduler dump ограничен границами наблюдения. Ready:true сам по себе
не доказывает доставку; наличие batch threshold не доказывает причину задержки.

Компьютер должен непрерывно работать на протяжении ожидания. Разрыв между
опросами свыше 180 секунд или перевод host-часов назад приводит к
`INFRASTRUCTURE_INTERRUPTED` и `naturalDispatchValidated=false`, с exit 1.
Финальные снимки/logcat и очистка выполняются; это не обычный 65-минутный
тайм-аут JobScheduler. Runner не отключает сон компьютера и не меняет питание.

Для API 35 укажите `-TestApi 35` в обеих командах: используется только
существующий проектный `Intime_Test_API35`, порт 5580. Поддержка режима в runner
сама по себе не подтверждает успешную доставку; результат записывается отдельно
для каждой платформы в DEVICE_TEST_RESULTS.md.
Для API 33 укажите `-TestApi 33` в обеих командах: используется существующий
проектный `Intime_Test_API33`, порт 5582. На API 33 WorkManager работает без
namespace JobScheduler; natural-режим не вызывает ручной запуск задания.
Успешный прогон 6 октября использовал при bootstrap `-EmulatorGpu host
-EmulatorRamMb 4096`: прежний software/2048 дал системную ANR Google Search/Launcher.
Это параметры нового процесса; уже запущенный принадлежащий runner AVD сначала
нужно остановить. Defaults не изменены, устранение ANR не гарантируется.
Для API 31 укажите `-TestApi 31` в обеих командах: используется существующий
проектный `Intime_Test_API31`, порт 5586. Здесь нет runtime POST_NOTIFICATIONS
и namespace JobScheduler. Параметры нового процесса для текущей проверки:
`-EmulatorGpu host -EmulatorRamMb 4096`; поддержку режима нужно отличать от
успешного device-прогона, результаты сохраняются отдельно для каждого API.
Этот режим принимает API 31/33/35/36 и не совмещается с RebootOnly/DozeOnly.

Разбор `when` учитывает одиночное число на API 33 и формат `when/postingTime`
на новых платформах. Для тихого повтора API 31/33 требуются hex defaults=0x0,
null sound/vibrate, LOW, отсутствие ACK, groupAlertBehavior=1 и биты
ONLY_ALERT_ONCE/AUTO_CANCEL (0x18). На API 35/36 дополнительно проверяется
текстовый SILENT. Это системные свойства Notification, а не измерение звука.

Перед reboot на API 36 runner ждёт `cmd package wait-for-handler` и
`wait-for-background-handler` с лимитом 30 секунд для каждой очереди.
Это подготовка host после force-stop/повторного запуска, а не принудительное
снятие stopped или вызов BootReceiver. Однажды перед reboot было stopped=false,
а после — stopped=true и alarm не восстановился; причина этого перехода
не доказана. Успех последующего прогона не закрывает нестабильность всей матрицы.
Требует прежнего разрешённого Intime_Test_API36/5584 с маркером владельца,
подписанного non-debuggable production APK code 25 и соседнего mapping.txt
с переименованным R8 TaskRepository. Новые образы/зависимости не скачиваются.

Чистая собственная установка проходит семь подготовительных CRUD/JSON-фаз,
реальный reboot и первое минутное уведомление AlarmReceiver с ACK. Затем Home,
am kill только фонового процесса, проверка отсутствия PID и stopped=false.
До тихого повтора не открываются Activity/шторка, не запускаются UIAutomator,
instrumentation или ручной job run. Период, часы, ограничения WorkManager
и batching ОС не меняются. Время ожидания 16–75 минут (по умолчанию 65)
не является обещанием доставки: Android может пропустить/задержать исполнение.

Повтор должен быть в активном record production-пакета с уникальным описанием,
when не раньше первой отправки +15 минут, LOW priority, без actions/ACK,
с sound/vibrate=null и defaults=0 в Notification и groupAlertBehavior=1.
На31/33 нужны отдельные hex flags0x18 и точная AndroidX-группа silent;
на35 — точная группа либо нативный SILENT, на36 — отдельный нативный SILENT.
Слово silent в header/тексте не засчитывается как нативный флаг.
В dumpsys mSound может оставаться звуком канала несмотря на тихий режим;
его null не требуется. Это проверка полей тихой доставки, не измерение звука.
Logcat после завершения процесса должен содержать SUCCESS настоящего
TaskNotificationWorker; будущий alarm для единственной просрочки отсутствует.
Private Room/preferences не читаются: wasNotified, UUID work и точные значения
последнего ACK этими наблюдениями не утверждаются. Doze/OEM, очередной период
после worker и остальные API не входят в сценарий.

Во время ожидания один notifications snapshot перезаписывается, чтобы не
увеличивать расход диска; финальные jobs/logcat/summary сохраняются в runs.
Собственная установка/probe/JSON удаляются в finally; эмулятор остаётся для
контролируемой остановки с проверкой PID/StartTime/AVD. Успех подтверждает только
summary PASSED с десятью фазами; наличие режима не подтверждает пройденную проверку.

При `-PeriodicRepeats 2` требуются одиннадцать фаз, два разных тихих when,
каждый не раньше предыдущего+15мин, и минимум два SUCCESS worker после kill.
Фактический статус и границы — DEVICE_TEST_RESULTS.md.

Фактический первый release-прогон 4 октября завершён **контролируемо**, 10/10:
естественный повтор не наблюдался за 52 минуты 4 секунды от первого уведомления,
хотя JobScheduler показывал Ready:true. Затем host отдельно запустил именно это
собственное задание через `cmd jobscheduler run -s -n androidx.work.systemjobscheduler
com.vpe_soft.intime.intime 1` (ID выбран из dumpsys этого запуска, не универсальная
константа). Вызов -s требует выполненных ограничений; не использовать -f вместо него.
Настройки/период/часы не менялись, но это ручной dispatch и не natural pass.
Перед таким вызовом обязательно проверить маркер владельца/PID/StartTime/AVD и
единственное задание production-пакета. Сохранить periodic-dispatch-control.json
(observedAt, ownJobCount=1, jobId) и periodic-controlled-run.log с выводом команды
в каталог активного прогона. Маркер не исполняет никаких команд: он позволяет
runner уточнить итог как PASSED_CONTROLLED_PLATFORM_DISPATCH,
naturalDispatchValidated=false. Без успешного Running job маркер отвергается.
Оба PASS-статуса дают exit 0, но имеют разные границы; natural timeout —
INCONCLUSIVE/exit 1. Исходный summary первого прогона до уточнения сохранён отдельно
и не подтверждает естественную доставку. Классификатор проверен на его реальных
файлах; весь сценарий после этой доработки заново не запускался.

### Регистрация расписания самим MainActivity

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test.ps1 -Mode device -TestApi 36 -Filter 'com.vpe_soft.intime.intime.activity.ScreenPlatformTest#mainStartup_keepsOnePeriodicReminderAcrossRecreationAndReopening' -Offline
```

Новый тест ScreenPlatformTest открывает настоящий MainActivityV2 и читает
созданную им уникальную work через WorkManager: одна активная работа ENQUEUED,
период 15 минут, BatteryNotLow, NetworkType.NOT_REQUIRED. После recreate и нового
ActivityScenario UUID/nextScheduleTimeMillis должны сохраниться, активная работа
должна остаться единственной. Исторические CANCELLED записи не считаются активными.
До теста прежняя unique work отменяется, после — созданный UUID; Room синтетическая
in-memory. Notification/ACK, смерть процесса, Doze и release/R8 не проверяются.
На API 36 новый тест прошёл отдельно и в составе UI-класса: теперь в нём три теста.
Допуск API 24+ не означает фактическую проверку нового сценария на всех API.

### Второй естественный период после завершения процесса (API 36)

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test.ps1 -Mode device -TestApi 36 -Filter com.vpe_soft.intime.intime.activity.ScreenPlatformTest -Offline -KeepEmulator
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-periodic-smoke.ps1
```

Host использует только существующий Intime_Test_API36/5584 с проверкой PID,
StartTime, AVD name и API, держит run.lock. Новые SDK-образы не устанавливаются.
Скрипт отказывается перезаписывать существующие .dev/.dev.test APK, устанавливает
свои собранные debug APK и удаляет их в finally (включая Room/preferences/jobs).

Две фазы PeriodicRecoveryTest имеют подробные русские Javadoc и требуют
отдельного метода/periodicPhase=true; обычная suite пропускает их. Первая
создаёт уже уведомлённую просроченную задачу в пустой файловой Room, отменяет
штатную periodic work для изоляции и ставит настоящий PeriodicWorkRequest:
15 минут, BatteryNotLow, initial delay 10 секунд. Проверяет первую тихую отправку
и ENQUEUED с будущим сроком; сохраняет UUID/задачу/поля/firstPost для второй фазы.

Host после окончания instrumentation вызывает am kill только фонового .dev процесса, требует отсутствия процесса
и stopped=false. Shell force-stop не используется. Далее до 50 минут опрашивается только
системная диагностика: никакой Activity/instrumentation, ручного job run, изменения
часов или ускорения периода. Активный notification record должен иметь своё описание
и when не раньше firstPost+15 минут. После этого вторая фаза сверяет postTime,
тихие поля без ACK, неизменные lastAck/nextAlarm/wasNotified, новый LAST_POST
и будущий WorkInfo. Фикстура удаляется, прежний LAST_POST возвращается.

Снимки опроса перезаписываются для ограничения размера отчёта; финальная диагностика
сохраняется в `.test-tools/runs/*-periodic-smoke`. `-WaitMinutes` допускает 16–60,
но не ускоряет период. Эти фазы проверяют debug worker и обычную смерть процесса,
не force-stop, reboot, Doze, release/R8, регистрацию MainActivity или OEM-квоты.
Результат подтверждается только summary PASSED с двумя фазами, не наличием скрипта.

Окно по умолчанию увеличено до 50 минут: AOSP API 36 может пакетно задерживать
одиночный CPU job до 31 минуты даже после наступления его срока. Для контролируемого
варианта добавлен `-ReduceBatching`: временный override
`jobscheduler/min_ready_cpu_only_jobs_count=1`, с точным возвратом прежнего override
(или удалением ключа, если был null) в finally. Этот режим не меняет период/часы
и не вызывает jobs вручную, но не проверяет срок доставки при default batching.
В фактическом прогоне 4 октября override применён отдельной контрольной командой
после примерно 24 минут ожидания и восстановлен; обе фазы прошли. Полный прогон
с новым флагом с самого начала ещё не выполнен. Системные логи показывают, что
первый процесс завершён при окончании instrumentation; am kill дополнительно
проверяет отсутствие фонового процесса, а не доказывает единственную причину его смерти.

### Настоящий периодический WorkManager (debug integration)

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test.ps1 -Mode device -TestApi 36 -Filter 'com.vpe_soft.intime.intime.receiver.NotificationDeliveryTest#realPeriodicWorker_postsQuietReminderAndSchedulesNextRun' -Offline
```

Тест использует настоящий WorkManager, PeriodicWorkRequest с интервалом 15 минут,
BatteryNotLow и initial delay 10 секунд. Проверяет регистрацию UUID в JobScheduler
(namespace `androidx.work.systemjobscheduler` на API 34+), первое исполнение,
тихое системное уведомление без ACK и будущий nextScheduleTimeMillis.
Исходные lastAck/nextAlarm/wasNotified и время отправки проверяются в синтетической
in-memory Room/preferences. Остаточная штатная periodic work тестового приложения
отменяется для изоляции; новый UUID отменяется в finally, LAST_POST возвращается.
Приватные пользовательские main*.db не читаются.

Это debug-тест с реальным WorkManager, а не вызов doWork через TestWorkerBuilder.
Он не ждёт второго 15-минутного исполнения и не определяет, какой scheduler
выполнил первое (SystemJobScheduler или GreedyScheduler). Регистрация расписания
MainActivity, release/R8, выживание после убийства процесса и Doze не покрыты.
Полный NotificationDeliveryTest также содержит отдельный условный тест отказа
POST_NOTIFICATIONS; его precondition по Assume требует отсутствующего разрешения.

### Предпосылки естественного deep Doze (API 31/33/35/36)

После свежего UI bootstrap соответствующего выделенного AVD с `-KeepEmulator`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-natural-doze-preflight.ps1 -TestApi 36
```

Диагностика проверяет владение процессом/serial/AVD/API, читает DeviceIdleController,
PowerManager, AlarmManager, BatteryService и SensorService. Она не меняет питание,
режимы, датчики, часы или приложение; существующие forced idle/battery simulation
и неизвестный формат отклоняются. Deep enabled сверяется между dumpsys и cmd.
`NATURAL_DEEP_UNAVAILABLE` (exit 3) означает отключённый deep и/или отсутствующий
motion sensor, который контроллер требует. `NATURAL_DEEP_PREREQUISITES_PRESENT`
(exit 0) означает только предварительную готовность. `INFRASTRUCTURE_ERROR`
(exit 2) означает ошибку диагностики. Ни один из этих результатов не является
PASS естественного перехода или доставки: оба validated-поля остаются false.
Присутствие обычного акселерометра не подменяет используемый контроллером датчик.
Повторять последовательно на всех выбранных API и останавливать только принадлежащий
прогону AVD по проверенным marker/PID/start time/name; порядок cleanup описан ниже.

Полный Doze и облегчённый light idle — разные проверки. Требования к датчику
и отключение Doze по умолчанию в AOSP описаны в
[документации платформы](https://source.android.com/docs/core/power/platform_mgmt).
Для конкретного SDK-образа решающим остаётся сохранённый снимок контроллера.
Включение deep, force-idle, step, изменение таймеров или настроек датчиков не
превращают результат управляемой проверки в естественный переход текущего образа.

Если deep-предпосылки отсутствуют, следующий порядок для физического устройства:

1. Получить отдельное согласие пользователя на личный телефон. Первый шаг — только
   чтение модели/API, версии установленного InTime и состояния Doze/питания/датчиков.
   Не устанавливать APK, не читать main*.db, не сбрасывать данные или режимы.
2. Сверить версию приложения с кандидатом release: проверка старой v1 не даёт
   покрытия v2. Обновление/тестовая установка согласуются отдельно при необходимости.
3. Согласовать отдельные синтетические задачи и способ наблюдения с отключённой
   зарядкой; USB-зарядка мешает естественному переходу. Не включать беспроводную
   отладку или менять настройки телефона автоматически.
4. Погасить экран, оставить устройство неподвижным; ждать без force-idle/step,
   ускорения таймеров, ручного receiver или запуска worker. Сохранить доказательство
   controller IDLE, mForceIdle=false, PowerManager idle и действующего idle-until
   до доставки, затем собственный callback/уведомление и новый срок после ACK.
5. Раздельно отметить timeout/неподтверждённый вход, delivery и cleanup. Одно
   уведомление при погашенном экране без состояния ОС не считается PASS Doze.

Это подготовленный порядок следующей проверки, а не выполненный тест телефона.

После отдельного согласия владельца первый шаг выполняет `scripts/read-phone-doze.ps1`.
Передайте явный USB serial из списка `adb devices -l`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/read-phone-doze.ps1 -Serial 'SERIAL_ТЕЛЕФОНА'
```

Скрипт отказывается работать с emulator serial и qemu-устройством. Читает модель/API,
versionCode/versionName InTime и перечисленные системные службы, не запускает
Activity/receiver/worker, не меняет battery/appops/Doze/настройки отладки и не
читает базы задач. `READ_ONLY_DIAGNOSTICS_COLLECTED` означает собранные сведения,
а не PASS теста: naturalEntryValidated, notificationDeliveryValidated и
candidateApkIdentityValidated остаются false. Совпадение номера версии не
доказывает идентичности release APK. Не подключает телефон по сети автоматически.

Для подключения по USB может понадобиться уже настроенная отладка и подтверждение
доступа на самом телефоне. Без подключённого/авторизованного устройства диагностика
не выполняется; не заменять этот шаг чтением старых снимков эмуляторов.

Матрица 8 октября: UI 3/3 API 31/33/35/36 (36 после сохранённого отказа ожидания
фокуса), все preflight NATURAL_DEEP_UNAVAILABLE. Контроллер каждого требует
motion sensor, но сообщает null; deep/light включены на 31/33 и выключены
на 35/36. Естественное ожидание deep не запускалось. Девять host-фикстур разбора
прошли; собственные AVD остановлены. Общий агрегат и границы — DEVICE_TEST_RESULTS.md.

### Неточный release alarm в принудительном deep Doze (API 31/33/35/36)

После успешного UI bootstrap на существующем проектном AVD того же API.
Пример для API 36:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-release-smoke.ps1 -TestApi 36 -ApkPath '.test-tools/runs/release-icons-20261004-221247/intime-25-2.0.0-rc1-icons.apk' -InexactOnly -DozeOnly
```

Для API 35/33/31 задайте соответствующий -TestApi в обеих командах.
Bootstrap выполняется с -EmulatorGpu host -EmulatorRamMb 4096 -KeepEmulator.
Каждый AVD запускается последовательно; перед переходом к следующему остановите
только принадлежащий прогону процесс, сверив PID/started и имя AVD.

Совместный режим содержит 12 host-фаз. Exact appop остаётся deny, собственный
alarm должен иметь положительное окно. Перед ожиданием проверяется отсутствие
production package в постоянном whitelist, имитируется отключение зарядки,
экран выключается и включается forced deep idle. При каждом опросе и после
доставки требуется IDLE; уведомление с уникальным описанием/action ACK,
logcat настоящего AlarmReceiver и исчезновение alarm обязательны.
UI/receiver вручную во время ожидания не запускаются. Лимит — 15 минут,
тайм-аут — INCONCLUSIVE; разрыв опросов более 180 секунд — INFRASTRUCTURE_INTERRUPTED.
После доставки питание/исходное enabled deep idle восстанавливаются до открытия
шторки и ACK; при ошибке это также выполняется в finally. Проверяется новый
inexact alarm после ACK/restart и сохранение deny. Естественный вход в Doze,
квоты нескольких alarm, OEM и повторная доставка после ACK не покрываются.
Успех режима фиксируется только по свежему DEVICE_TEST_RESULTS.md.
6 октября API 35/33/31 проверены последовательно за один этап, каждый 12/12,
UI 3/3; API 36 ранее прошёл 12/12. Первый API 33 FAILED до Doze сохранён:
runner повторяет tap только по SAF android:id/title до focused=true (30 секунд),
не вводя имя без фокуса; затем проверяет точное имя и реальный JSON. Повтор прошёл.

### Сверка idle-политики перед проверкой квот

После bootstrap того же выделенного API с `-KeepEmulator`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-doze-policy.ps1 -TestApi 36
```

Сценарий поддерживает API 31/33/35/36 и требует совпадения serial, PID,
времени запуска, имени AVD и версии Android с записью владения проекта.
При уже симулированной батарее или forced idle отказывается менять питание.
Сохраняет исходный enabled deep, включает forced deep IDLE и делает два
снимка deviceidle/power/alarm с паузой 20 секунд. Для IDLE_POLICY_READY
в обоих снимках требуются controller IDLE, PowerManager idle=true,
действующий idle-until alarm и tag DeviceIdleController.deep.
IDLE_POLICY_NOT_CONFIRMED — измерение завершено, но эти признаки не совпали;
это не падение теста InTime. Ошибка окружения/восстановления —
INFRASTRUCTURE_ERROR, код 2. Код 0 означает завершённую диагностику,
а не успешное применение квоты. Снимки и summary находятся в отдельном
каталоге `.test-tools/runs/*-doze-policy`.

В finally снимаются внесённый force idle и симуляция батареи, пробуждается
экран, восстанавливается исходный enabled deep; результат проверяется чтением.
Сценарий не устанавливает APK, не вызывает receiver и не меняет квоты,
системный образ или данные AVD. Он записывает наблюдаемые compat quota/window,
но всегда оставляет quotaEnforcementValidated=false. Даже готовая политика
не доказывает отсрочку alarm приложения после исчерпания лимита. После
диагностики остановить только собственный AVD по записи владения.
7 октября свежие UI bootstrap прошли 3/3 каждого API. Диагностика дала READY
на API 35/36 и NOT_CONFIRMED на 31/33: на последних controller/power idle=true,
но idle-until отсутствует. На 35/36 записаны compat 7/час, на 31/33 — 1/9 минут;
квоты не изменялись и их исчерпание не проверялось. Для будущей проверки квот
на 31/33 сначала требуется восстановить настоящую idle-политику AlarmManager.
Подробности и сохранённые raw dump — DEVICE_TEST_RESULTS.md.

Для контролируемой проверки восстановления отсутствующего idle-until:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-doze-policy.ps1 -TestApi 33 -RepairMissingIdleUntil
```

При уже подтверждённых controller IDLE и PowerManager idle=true и отсутствии
idle-until сценарий один раз выполняет `step deep` в maintenance. Перед вторым
шагом читает состояние: если maintenance закончилась сама, повторный шаг не
нужен; иначе возвращает её в IDLE. Неизвестное состояние отклоняется.
При существующем idle-until шаги не выполняются. Исходное измерение сохраняется
в initialForced и initial-forced-*.log, факт вмешательства —
controlledMaintenanceCycle, ответы шагов — maintenanceStep/idleStep.
Квоты не меняются. Вмешательство относится только к подготовке; шаги между
наблюдаемыми доставками не допускаются как часть проверки непрерывного idle.
Это не естественный переход в Doze и не исправление ОС или приложения.
Сценарий восстанавливает питание в finally: его запуск отдельно перед release
не сохраняет восстановленный режим для следующего теста. Для проверки alarm
приложения та же подготовка должна быть встроена до начала ожидания доставки.
Свежая проверка 7 октября с этим флагом: UI 3/3 каждого API, READY всех четырёх.
На API 31/33 выполнен один цикл, на 35/36 он пропущен; quota/window до/после
совпадают. Доставка приложения при восстановленной политике ещё не проверена.

### Проверка отсрочки из-за квоты на API 31/33

После свежего UI bootstrap соответствующего API запустить:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-release-smoke.ps1 -TestApi 33 -ApkPath '.test-tools/runs/release-icons-20261004-221247/intime-25-2.0.0-rc1-icons.apk' -InexactOnly -DozeOnly -DozeSequence -DozeQuota
```

Повторить последовательно на API 31 с отдельным bootstrap и cleanup.
DozeQuota требует остальные три флага и API 31/33; сценарий содержит 15 host-фаз.
До ожидания первой доставки проверяются controller IDLE, PowerManager idle=true
и текущий idle-until AlarmManager. Если последний отсутствует, допускается один
управляемый цикл maintenance → IDLE; исходные снимки и вмешательство сохраняются.
Требуются фактические compat quota=1 и положительное окно, одинаковые до и после
подготовки. Настройки квоты и часов не меняются.

После первой самостоятельной доставки требуются её запись в compat history,
flags ALLOW_WHILE_IDLE_COMPAT без UNRESTRICTED и явный перенос второй alarm:
device_idle должен быть позже requester более чем на секунду. Затем проверяется
самостоятельная доставка второй задачи и интервал не меньше измеренного окна
минус 2 секунды: timestamp уведомления следует после системной отправки alarm.
Одного большого интервала недостаточно для подтверждения квоты.

На каждом опросе требуются все три idle-признака. Между доставками нет UI, ACK,
ручного receiver или maintenance. Потеря политики останавливает проверку;
пауза host-опросов более 180 секунд, включая последнюю, означает прерывание
инфраструктуры. Ожидание каждой доставки ограничено 20 минутами. Только после
переноса и обеих доставок quotaEnforcementValidated становится true. Питание
восстанавливается, затем проверяются ACK и перезапуск, установленный этим
прогоном production APK удаляется. API 35/36 с наблюдаемой квотой 7/час требуют
отдельного сценария; естественный Doze и OEM этим режимом не покрываются.

Прогоны 7 октября: API 31/33 прошли 15/15, свежие UI 3/3 каждого;
quotaEnforcementValidated=true, интервалы 539,983/540,018 с. На API 33 сохранены
два ранних отказа инфраструктуры до квоты (SystemUI и Google launcher/search ANR).
Успешный повтор использовал существующий профиль с hw.cpu.ncore=4 вместо 2,
host GPU, DisableEmulatorVulkan и EmulatorRamMb=4096; API 31 также имел 4 CPU.
Данные не сбрасывались. Изменено только число ядер существующего API 33,
не шаблон новых AVD; долгосрочная устойчивость режима не установлена.
Подробные доказательства и все попытки — DEVICE_TEST_RESULTS.md.

### Насыщение часовой квоты на API 35/36

После свежего UI bootstrap соответствующего API:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-release-smoke.ps1 -TestApi 36 -ApkPath '.test-tools/runs/release-icons-20261004-221247/intime-25-2.0.0-rc1-icons.apk' -InexactOnly -DozeOnly -DozeSequence -DozeQuotaSeven
```

Повторить последовательно на API 35, с отдельным bootstrap/cleanup. Режим требует
API 35/36 и остальные три флага; DozeQuota одновременно запрещён. Ожидаются 21
host-фаза, восемь отдельных системных alarm dispatch и последующий ACK/restart восьмой задачи.
Приложение и release APK не модифицируются. До ожидания восемь синтетических задач
импортируются штатным SAF/JSON parser в новую установку: первый срок через две
минуты, следующие с шагом три минуты. Это подготовка через backup, не проверка
создания восьми задач UI. Экспорт предыдущих CRUD-проверок сохраняется отдельно.
Каждый receiver должен самостоятельно назначить точный срок следующей задачи.

Требуются измеренные compat quota=7 и window=3600000 мс. Перед первым ожиданием
допускаются не больше трёх управляемых maintenance-циклов для горизонта idle-until
не меньше 80 минут; неизвестный горизонт отклоняется. Шаги и снимки сохраняются,
квоты/часы/постоянные idle-настройки не меняются. Все вмешательства завершаются
до наблюдения первой доставки. Во время ожидания требуются controller IDLE,
PowerManager idle=true и текущий системный idle-until на каждом опросе.

После седьмой отправки требуются ровно семь записей своего пакета внутри окна
в compat history, restricted flags=0x20 без unrestricted и положительный перенос
восьмой alarm. Её device_idle сверяется с освобождением самой старой записи
с допуском 5 секунд для чтения dumpsys. Затем восьмая должна отправиться сама:
требуются восемь доставок в счётчике пакета, восемь onReceive его процесса,
потреблённая собственная alarm и последний callback после окончания окна.
История вправе сохранить семь записей, удалив вышедшую из окна при восьмой отправке;
поэтому её размер сам по себе не доказывает число доставок. PID сверяется с ps и записями
Start proc своего пакета: остановка процесса до dispatch допускается.
Время callback читается из logcat с измеренным годом/часовым поясом устройства;
интервал от первого Notification.when не меньше окна минус 2 секунды.
Только после этого quotaEnforcementValidated=true.
В AOSP AppWakeupHistory.recordAlarmForPackage вызывает snapToWindow, который
удаляет вышедшие из временного окна записи при добавлении новой:
[AlarmManagerService.java](https://android.googlesource.com/platform/frameworks/base/+/296c28979c5b212e9e32be397901f93e15b60bf3/apex/jobscheduler/service/java/com/android/server/alarm/AlarmManagerService.java).
Это объясняет наблюдаемые семь записей после восьми отправок; идентичность
исходников конкретных SDK-образов этому commit отдельно не установлена.
Между доставками нет UI, ACK, ручного receiver, maintenance или изменения часов.

Первые семь доставок ограничены 15 минутами каждая, восьмая — 75 минутами.
Alarm-квота не ограничивает все уведомления приложения. Естественный periodic
worker может раньше показать тихую сводку восьми просроченных задач и отметить
восьмую уведомлённой; receiver тогда не публикует дубль. Такой путь требует
сводку своего пакета, корректный срок и SUCCESS worker собственного процесса.
Прямой путь требует текст восьмой задачи и ACK action. Сводка worker по текущему
контракту содержит contentIntent списка, но не ACK action: через неё открывается
список, восьмая задача подтверждается в деталях. Перед действием уведомление
повторно проверяется: worker вправе заменить прямое уведомление сводкой.
После пробуждения выделенного AVD обычный keyguard снимается командой
`wm dismiss-keyguard`, без изменения PIN или настроек блокировки. После нажатия
contentIntent runner до 30 секунд ждёт настоящий recyclerView приложения,
затем ищет задачу ограниченной прокруткой. Снимок SystemUI во время перехода
не считается отсутствием задачи. Для короткого сценария публикации сводки
ожидание должно покрывать положительное окно inexact alarm, а не только её срок.
В обоих случаях проверяются
новый inexact alarm, deny exact и тот же срок после restart. Постановка worker
вручную и изменение его периода запрещены. Путь и два отдельных timestamp
уведомления/dispatch сохраняются в summary; восемь alarm dispatch не означают
восемь уникальных публикаций NotificationManager.
quotaEighthDispatchWhen — timestamp системного callback,
quotaEighthNotificationWhen — независимый timestamp текущего уведомления.
Для восьмой строки quotaDeliveries поле when и служебное inexactNotificationWhen
содержат timestamp callback; для первых семи строк — Notification.when.
sequenceGapSeconds считается от первого Notification.when до восьмого callback.

Обычный полный прогон занимает около часа с дополнительной подготовкой;
компьютер должен оставаться активным. Разрыв опросов более 180 секунд означает
прерванное наблюдение. Ранние строки logcat сохраняются до каждого ожидания,
чтобы часовая пауза не потеряла доказательство постановки alarm в кольцевом буфере.
Опросы используют фиксированные файлы и не накапливают dump каждые 15 секунд.
Естественный Doze, OEM и долговременная устойчивость этим режимом не проверяются.

Завершённая матрица 7–8 октября: API 35/36 — свежие UI 3/3 и release 21/21
каждого; gap 3599,998/3600,002 с, ACK/restart/cleanup прошли. API 35 использовал
сводку штатного worker и детали восьмой задачи, API 36 — action прямого
уведомления. Семь/восемь сохранённых записей после восьмой отправки принимаются
только вместе с независимыми восемью callback и счётчиком пакета.
Отчёты, неудачные попытки и общий агрегат — DEVICE_TEST_RESULTS.md.

### Два заранее назначенных inexact alarm без выхода из Doze

После свежего UI bootstrap того же API:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-release-smoke.ps1 -TestApi 36 -ApkPath '.test-tools/runs/release-icons-20261004-221247/intime-25-2.0.0-rc1-icons.apk' -InexactOnly -DozeOnly -DozeSequence
```

Повторить последовательно на существующих API 35/33/31, с отдельной очисткой
и остановкой принадлежащего прогону AVD перед следующим bootstrap. DozeSequence
требует InexactOnly + DozeOnly, содержит 14 host-фаз. До входа в IDLE создаются
две задачи: первая на минуту, вторая на пять минут. Срок второй читается из
AlarmManager, пока она ближайшая; затем первая задача становится ближайшей.
После первой доставки receiver должен самостоятельно поставить alarm второй.
Между доставками нет UI, ACK, ручного receiver, выхода из IDLE или изменения
часов/квот. Требуются разные описания/when, два receiver в logcat, deny и
положительные окна, IDLE на каждом опросе и после доставки. Снимки первого
alarm/уведомления сохраняются с префиксом inexact, второго — inexact-repeat.

Ожидание каждой доставки ограничено 20 минутами; тайм-аут INCONCLUSIVE не
доказывает ошибку приложения или точную величину квоты. В summary сохраняются
sequenceFirst/sequenceSecond, фактический gap и count receiver. Это наблюдение
повторной доставки на конкретном forced Doze AVD, не универсальная гарантия
частоты. После обеих доставок настройки питания восстанавливаются и выполняется
ACK второго уведомления с проверкой нового alarm после перезапуска. Существующий
DozeSequence smoke не подтверждает естественный вход в Doze или физический OEM.
В новых прогонах дополнительно сохраняется dumpsys power при опросе и поле
powerDeviceIdle для каждой доставки. Значение true не подтверждает применение
конкретной квоты: quotaEnforcementValidated остаётся false. На API 33 успешный
прогон предшествовал добавлению этой диагностики, поэтому powerDeviceIdle там
не измерен. Прогоны 6–7 октября: API 31/33 PASSED 14/14, UI 3/3 каждого;
API 35/36 не завершены из-за SystemUI ANR. Подробности в DEVICE_TEST_RESULTS.md.
7 октября после явного разрешения пользователя данные только проектных
API 36/35 сброшены. UI тесты прошли 3/3 каждого; оба release-повтора вновь
остановились на SystemUI ANR при reboot после семи фаз, до доставки в Doze.
Сброс не исправил инфраструктуру. Последующий запуск с host GPU и
-DisableEmulatorVulkan прошёл 14/14 на API 36/35, UI 3/3 каждого. Полная
матрица двух доставок подтверждена; квоты этим прежним режимом не проверялись; проверки
ANR сохранены. Все попытки сохранены. Если AVD запущен внешней
обвязкой с записью владения, до вызова runner следует дождаться его serial
в adb: иначе runner безопасно отклоняет ещё не подключённый процесс.

### Точечный release Doze smoke на существующем API 36

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test.ps1 -Mode device -TestApi 36 -Filter com.vpe_soft.intime.intime.activity.ScreenPlatformTest -Offline -KeepEmulator
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-release-smoke.ps1 -TestApi 36 -ApkPath '.test-tools/runs/release-permissions-20261003-225632/intime-25-2.0.0-rc1-r8.apk' -DozeOnly
```

Режим проходит восемь подготовительных фаз CRUD/JSON/reboot, одну доставку
в принудительном deep idle, затем проверку шторки и ACK с перезапуском процесса.
Только собственная новая production-установка получает exact alarm permission.
Отсутствие пакета в постоянном whitelist, deep state IDLE при каждом опросе,
активный notification record своего пакета/уникальное описание, лог receiver
и исчезновение alarm проверяются до включения экрана. Во время ожидания UIAutomator
и Activity не запускаются. После доставки снимается forced idle, battery simulation
сбрасывается, экран включается. При ошибке восстановление тоже выполняется в finally.
Скрипт отказывается заменять существующую battery simulation или forced idle.
Если deep idle изначально выключен (AOSP API 36 этого хоста), временно включается
только deep idle; при восстановлении возвращается исходный enabled. Light idle
и постоянный whitelist не меняются. Переход из списка к деталям подтверждается
по btnAckTask; потерянный tap повторяется лишь пока видна исходная строка задачи.
Логи `doze-*.log` и summary сохраняются в каталоге запуска.

Это одна доставка exact-and-allow-while-idle, не естественный переход в Doze,
квоты нескольких alarm, inexact fallback, периодический WorkManager или поведение
физического телефона. Фазы отказа разрешения/канала пропущены; RebootOnly и
DozeOnly несовместимы. Наличие режима само по себе не доказывает pass на устройстве.

GitHub Actions (`.github/workflows/android-ci.yml`) добавлен при слиянии локальной
master. Он использует JDK 17, SDK 36/build-tools 35.0.0, текущий Gradle Wrapper
и задачи `:app:lintDebug`, `assembleDebug`, `:app:testDebugUnitTest`.
Общая установка SDK и кэширование — `.github/actions/android-setup/action.yml`.
GitLab CI сохранён; успешность удалённых GitHub/GitLab jobs проверяется отдельно.

CI на Ubuntu устанавливает пакеты с `DEBIAN_FRONTEND=noninteractive` и
`TZ=Etc/UTC`, затем применяет UTC к системному tzdata. Один `apt-get --yes`
не отключает вопросы debconf: job 16913813414 остановился на выборе региона
при установке tzdata до запуска Gradle. Настройки закреплены в `.gitlab-ci.yml`.
После изменения нужен новый pipeline на коммите с исправлением;
повтор старого job использует прежнюю конфигурацию.

`lintDebug` сохраняет HTML/XML и текстовый lint report как artifacts `when: always`,
чтобы при отказе видеть все ошибки, а не только первую в консоли. Для повторения
проверок build-стадии локально (JDK 17, SDK 36):

```powershell
./gradlew.bat -Pci --console=plain --no-watch-fs :app:lintDebug :app:assembleDebug --offline --no-daemon
```

Pipeline 2909444152 остановился на lint (9 errors/43 warnings); assembleDebug был
успешен, тесты не выполнялись. После исправления локально 0 errors/24 warnings:
неиспользуемые ресурсы, плотности raster icons, notifyDataSetChanged. Lint не
отключён, baseline не добавлен. Результаты — DEVICE_TEST_RESULTS.md.

### Production release smoke с R8 на API 36

`scripts/test-release-smoke.ps1` проверяет чистую установку кандидата
25/2.0.0-rc1 через настоящий UI: создание/alarm, edit и перезапуск процесса,
ACK из деталей/перенос срока, настройки/Back, JSON export через SAF,
удаление/повторное открытие и JSON import с перезапуском. Далее настоящий
reboot/восстановление alarm до запуска Activity, доставка уведомления и ACK
из шторки, отказ POST_NOTIFICATIONS/восстановление (API 31: общий запрет через Settings) и отключение/включение
канала — всего четырнадцать фаз. Прогон занимает несколько минут:
минутные будильники ожидаются в реальном времени, часы ОС не изменяются.
Runner принимает `-TestApi 31`, `33`, `35` или `36` (по умолчанию 36),
строго для проектных Intime_Test_API31/33/35/36 с serial
`emulator-5586`/`emulator-5582`/`emulator-5580`/`emulator-5584` соответственно.
Выбор API должен совпадать с bootstrap; произвольный serial не поддерживается.
Нужны JDK `C:\Program Files\Java\jdk-17`, SDK в `%LOCALAPPDATA%\Android\Sdk`,
platform android-36 с `android.jar`/`uiautomator.jar`, build-tools 35.0.0
и JUnit 4.13.2 в Gradle cache после bootstrap. Signing credentials вводятся
локально по RELEASE.md; пароли не передаются аргументами smoke.

```powershell
# Запустить и оставить принадлежащий runner проектный AVD; это debug bootstrap.
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test.ps1 -Mode device -TestApi 36 -Filter com.vpe_soft.intime.intime.activity.ScreenPlatformTest -Offline -KeepEmulator
# Отдельная последовательная сборка подписанного production APK с R8.
./gradlew.bat ':app:assembleRelease' '-PproductionRelease=true' '-PreleaseVersionCode=26' '-PreleaseVersionName=2.0.0-rc2' --offline --no-daemon --no-watch-fs --no-configuration-cache
```

Сохранить APK из `app/build/outputs/apk/release` и `mapping.txt` из
`app/build/outputs/mapping/release` **одной сборки** в отдельную папку отчёта.
Mapping должен лежать рядом с переданным APK. Пример для проверенного артефакта:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-release-smoke.ps1 -ApkPath '.test-tools/runs/release-permissions-20261003-225632/intime-25-2.0.0-rc1-r8.apk'
```

Исторический APK 25 принимается при стандартном `-ExpectedVersionCode 25`.
Новые production-сборки требуют код не ниже 26: опубликованная v1 уже имеет 25,
пользователь подтвердил максимум 25 в Console. Для APK следующего кандидата
26/2.0.0-rc2 явно передавать `-ExpectedVersionCode 26 -ExpectedVersionName 2.0.0-rc2`;
значения сохраняются в summary и проверяются по aapt до установки, имя — также
на экране настроек. Другой пакет, код/имя или debuggable
отклоняются; подпись и mapping проверяются отдельно. Эта проверка не устанавливает
свободность кода в Console. Для будущих загрузок её нужно сверять заново.

Проверка 8 октября: candidate 26/2.0.0-rc2 — local 97/97, API 36 UI 3/3,
release smoke 14/14. Первый FAILED 3/14 из-за старого rc1-селектора сохранён;
после исправления имени выполнен полный повтор на том же APK. Шесть реальных
Gradle dry-run границ и девять host-случаев badging прошли. Команды, точные
артефакты, SHA и границы — RELEASE.md/DEVICE_TEST_RESULTS.md.

Для API 35 сначала оставьте запущенным соответствующий проектный AVD, затем
передайте тот же API release-runner (приложение и shell-probe компилируются с SDK 36):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test.ps1 -Mode device -TestApi 35 -SystemImage 'system-images\android-35\default\x86_64' -EmulatorGpu host -EmulatorRamMb 4096 -Filter com.vpe_soft.intime.intime.activity.ScreenPlatformTest -Offline -KeepEmulator
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-release-smoke.ps1 -TestApi 35 -ApkPath '.test-tools/runs/release-permissions-20261003-225632/intime-25-2.0.0-rc1-r8.apk'
```

По умолчанию API 35 теперь использует AOSP, 4 ГБ RAM и host GPU. Образ устанавливается
отдельно через sdkmanager. Существующий проектный AVD с другим SystemImage runner
не переиспользует: перед сменой образа нужно остановить принадлежащий runner процесс
и сохранить прежний профиль. Для новой конфигурации здесь прежний Play 16 КБ профиль
сохранён в `.test-tools/avd/Intime_Test_API35-ps16k-20261004.avd`.
`-EmulatorRamMb` задаёт RAM нового процесса (2048–8192 МБ); явное значение
переопределяет INI через `-memory` без удаления данных. RAM записывается в summary
и запись владения; смена/подтверждение другого значения у работающего AVD отклоняется.

Для API 31 выполните отдельный bootstrap (ScreenPlatformTest теперь допускает API 24+):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test.ps1 -Mode device -TestApi 31 -Filter com.vpe_soft.intime.intime.activity.ScreenPlatformTest -Offline -KeepEmulator
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-release-smoke.ps1 -TestApi 31 -ApkPath '.test-tools/runs/release-permissions-20261003-225632/intime-25-2.0.0-rc1-r8.apk'
```

Для API 33 используйте отдельный проектный Google APIs AVD (после остановки
предыдущего принадлежащего runner эмулятора):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test.ps1 -Mode device -TestApi 33 -Filter com.vpe_soft.intime.intime.activity.ScreenPlatformTest -Offline -KeepEmulator
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-release-smoke.ps1 -TestApi 33 -ApkPath '.test-tools/runs/release-permissions-20261003-225632/intime-25-2.0.0-rc1-r8.apk'
```

Runner проверяет PID/start/имя/API AVD, upload-сертификат, production пакет,
код 25, non-debuggable и переименование репозитория в mapping; снимает SHA-256.
При существующем production-пакете отказывается продолжать. Использует только
синтетическую задачу и после проверки удаляет собственную установку/probe.
Уникальный экспорт сохраняется в Downloads и также удаляется после smoke;
его локальная копия остаётся в отчёте. Проверенный picker — DocumentsUI
на Google APIs API 33 и AOSP API 35/36,
с открытой папкой Downloads; другие providers/каталоги не проверялись.
Ошибка сбора диагностики не отменяет попытку очистки. Общий `run.lock` исключает
параллельный запуск с другими runner. Отчёты — `.test-tools/runs/*-release-smoke`.
Эмулятор остаётся запущенным: завершать только принадлежащий runner процесс,
повторно сверив `.test-tools/emulator.json`, PID/start и имя AVD; личный телефон не использовать.

Отдельный legacy SDK UIAutomator/JUnit3 probe получает свежий XML без ожидания idle
(часы приложения обновляются каждую секунду). Он проверяет свежесть файла и собственный
маркер успеха; зависимости и тестовые хуки в release APK не добавляются.
Это вспомогательная диагностика, не дополнительный тест приложения.
JSON проверяется через настоящий ContentResolver: контракт экспорта и
восстановление описания/срока alarm после удаления и перезапуска. Все поля
повторным экспортом, неверный JSON и откат в этой UI-проверке не сравниваются.
На API 33+ POST_NOTIFICATIONS выдано до запуска. Первые фазы и reboot там используют
inexact fallback; на API 31 exact-доступ уже доступен в чистой установке, поэтому
этот прогон не доказывает fallback при его запрете. После reboot runner разрешает SCHEDULE_EXACT_ALARM своей
установке через appops, выбирает минутный период и выполняет UI ACK для нового
отсчёта. Уходит Home, ждёт настоящее уведомление до 150 секунд, нажимает
Acknowledge и проверяет исчезновение уведомления/новый alarm/перезапуск процесса.
Тест не меняет часы ОС и не вызывает receivers вручную. Reboot ограничен
300 секундами и ростом boot_count, затем до 180 секунд на восстановление того же
alarm до открытия Activity. Полные журналы до/после reboot проверяются на ANR/crash.
Разрешение exact исчезает с удалением собственного APK после проверки.
После доставки/ACK на API 33+ runner отзывает POST_NOTIFICATIONS своей установке и задаёт
user-set/user-fixed, чтобы permission dialog не перехватывал UI. Dumpsys подтверждает
granted=false. Новый UI ACK назначает минутный срок; описание не должно появиться
в шторке до пяти секунд после срока, alarm должен исчезнуть, logcat — подтвердить
AlarmReceiver. После возврата разрешения новый ACK той же задачи проверяет доставку.
На API 31 runtime permission отсутствует: вместо него используется главный переключатель
собственного приложения в системном APP_NOTIFICATION_SETTINGS. Выключение подтверждается
unchecked и importance=NONE/0 в строке собственного пакета dumpsys notification,
включение — checked и отсутствием запрета (Android 12 опускает default importance).
Фазы называются `release-app-notifications-denied/restored`; summary указывает
`notificationBlockMode=settings-switch`. Общий запрет через appops POST_NOTIFICATION
ignore здесь неэквивалентен переключателю: такая проба пропустила уведомление и не считается pass.
Доставка после снятия запрета проверяется новым UI ACK; permission dialog не проверяется.
Канал DefaultChannelId выключается/включается через системный экран Android;
проверяются состояние переключателя и importance собственного канала в dumpsys.
Отсутствие уведомления, работа receiver и восстановление доставки проверяются
аналогично. Флаги разрешений и состояние канала удаляются с собственным APK.
Сценарий проверен на английском Settings/notification action проектных Google APIs
API 31/33 и AOSP API 35/36. Перед вводом описание должно получить фокус, введённый
текст сверяется со свежим XML. Имя экспорта заменяется через MOVE_END/DEL и проверяется
до Save: shell Ctrl+A на API 31 вводил лишнюю букву. Перед запуском Activity шторка
сворачивается, чтобы предыдущая failed-фаза не перекрывала UI. Back для скрытия IME выполняется только при
показанной клавиатуре по dumpsys input_method. Переход в Edit подтверждается
появлением поля, потерянный tap повторяется лишь пока видна кнопка деталей.
Все проверки уведомлений требуют реально раскрытой шторки по resource-id
notification_stack_scroller; повторное раскрытие ограничено 20 секундами.
Перед reboot сохраняется пакет и требуется stopped=false; после — пакет,
broadcasts и logcat каждого повтора, в том числе успешного. На API 31 по умолчанию
15 секунд ожидания после последних операций с процессом. Причина одного случая
без вызова BootReceiver не установлена: пауза не считается доказанным исправлением.
Для точечного исследования используйте тот же bootstrap API 31 с KeepEmulator,
затем (production APK должен отсутствовать):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-release-smoke.ps1 -TestApi 31 -ApkPath '.test-tools/runs/release-permissions-20261003-225632/intime-25-2.0.0-rc1-r8.apk' -RebootOnly -RebootRepeats 3 -RebootSettleSeconds 0
```

Этот режим проходит семь подготовительных фаз CRUD/JSON, затем указанное число
настоящих reboot. Перед повтором снова выполняется force-stop/запуск и сверяется
описание/alarm, после reboot alarm проверяется до открытия Activity. При первом
сбое запуск прекращается, summary сохраняет rebootResults и диагностику.
Минутные уведомления и разрешения в этом режиме не проверяются; его успех не
равнозначен полному smoke. RebootRepeats (1–10) требует RebootOnly;
RebootSettleSeconds (0–60) применяется на API 31/35/36; на35/36 после паузы
runner дополнительно ожидает обе очереди PackageManager. Реально выполненная
пауза записывается в rebootSettleAppliedSeconds. Это подготовка теста,
а не доказательство причины или устойчивого устранения прежнего stopped=true.
Black-box проверки не читают wasNotified, не доказывают сохранение этой отметки
или автоматическую доставку старой просрочки без нового пользовательского ACK.
Doze, точность доставки в миллисекундах,
тихий worker, boot-уведомление о просрочках, обновление Play v1 и реальные данные
не покрыты. Результаты и точные границы прогонов — DEVICE_TEST_RESULTS.md.

### Local и instrumented tests

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test.ps1 -Mode local
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test.ps1 -Mode local -Offline
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test.ps1 -Mode local -Filter '*ReminderCalculatorTest'
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test.ps1 -Mode device
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test.ps1 -Mode device -Offline
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test.ps1 -Mode device -TestApi 33 -Offline
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test.ps1 -Mode device -TestApi 36 -Offline
```

Local mode runs JUnit and Robolectric, including Room/import/scheduling integration
checks. Device mode checks native Room/migrations, Calendar/JSON/import, ACK
PendingIntent, platform notifications (delivery/ACK, denied POST_NOTIFICATIONS,
silent worker output, disabled channel) and screen insets/navigation on API 33+.
Two reboot and three upgrade phases are skipped in a normal suite and run separately
by their host scripts.
The denied-permission test skips when permission is already granted; the smoke
script always revokes it before a separate run. A skipped test is not verification.
Robolectric does not replace testing Android platform behavior on a device.

## Комментарии к тестам

Каждый новый тест должен содержать подробный комментарий на русском языке
в формате Javadoc (`/** ... */`) непосредственно перед аннотацией `@Test`.
Правило распространяется на все юнит- и интеграционные тесты в `app/src/test`
и `app/src/androidTest`, включая инструментальные тесты Android.

В комментарии необходимо описать:

- исходные условия и значимые тестовые данные;
- действие или вызов, поведение которого проверяется;
- ожидаемый результат и конкретные проверяемые утверждения;
- существенные границы проверки, если название теста или сценарий могут
  создать впечатление более широкого покрытия.

Комментарий должен соответствовать фактическому коду: не приписывайте тесту
проверки, которых в нём нет. При изменении сценария или утверждений обновляйте
комментарий вместе с тестом. Используйте существующие комментарии как образец.

## Environment

- JDK 17 with java.exe and javac.exe; discovered through JAVA_HOME or PATH.
  Override discovery with -JdkPath.
- Android SDK platform 36. SDK path is read from local.properties, then
  ANDROID_HOME; -SdkPath overrides runner discovery. Ensure local.properties
  points to the same SDK because Gradle also reads it.
- For device mode: platform-tools, modern emulator/emulator.exe, hardware
  virtualization and a system image. Default API 35 now uses
  system-images/android-35/default/x86_64 (AOSP revision 2 installed here).
  The older Google Play 16 KB profile is preserved separately after system failures;
  its results do not establish a release pass.
  -TestApi 31 uses system-images/android-31/google_apis/x86_64 (revision 12 here).
  -TestApi 33 uses system-images/android-33/google_apis/x86_64 (revision 17
  installed here). -TestApi 36 uses system-images/android-36/default/x86_64 (AOSP)
  in its own Intime_Test_API36 AVD. -SystemImage can choose another installed x86_64 image for
  the selected API, relative to SDK; compilation requires platform 36.
- Initial builds require network access to Google Maven/Maven Central and the
  wrapper distribution. Robolectric also resolves Android runtime JARs. An online
  local run copies available instrumented runtime JARs from the user's Maven cache
  to .test-tools/robolectric. Offline mode disables both Gradle and Robolectric
  downloads and uses that prepared runtime directory.
- Instrumentation has its own dependencies: bootstrap device mode online too.
- After changing Gradle/AGP versions, repeat both online bootstraps: cached JARs
  do not guarantee that the new Gradle has usable dependency metadata offline.
- The Codex sandbox may need an approved escalation to access the user Gradle
  cache. This is independent of whether the emulator is running.

## Emulator ownership

Для диагностики графики нового экземпляра можно указать `-EmulatorGpu host`
(GPU компьютера), `auto` (выбор эмулятора) или `software`.
По умолчанию API 35/36 используют host, API 31/33 — software.
Параметр меняет только запуск процесса, не сохранённый config.ini. Режим записывается
в `.test-tools/emulator.json` и summary.json. Явный режим при повторном использовании
должен совпадать с записью; иначе сначала остановите принадлежащий runner AVD.
Это не гарантирует совместимость драйвера GPU на другом компьютере.
Описание режимов — [документация Android](https://developer.android.com/studio/run/emulator-acceleration).

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test.ps1 -Mode device -TestApi 36 -EmulatorGpu host -Offline -KeepEmulator
```

Если UI-тест сообщает `focused=false`, проверьте `dumpsys window` и logcat
именно проектного AVD. На API 36 профиль 2 ядра/2 ГБ давал ANR System UI при
загрузке: системный диалог перехватывал ввод. Такой сбой окружения нужно
диагностировать отдельно; нельзя убирать проверку фокуса или считать повторный
успех доказательством устранения ANR. Проба 4 ядер/4 ГБ также дала ANR;
увеличение ресурсов не закреплено как исправление. Результаты — DEVICE_TEST_RESULTS.md.
Для API 36 runner выбирает отдельный профиль AOSP без служб Google.
На этом компьютере проверен Emulator 37.2.12, 8 виртуальных ядер, 2048 МБ RAM,
720×1280/density 280, host GPU и отключённая загрузочная анимация ОС.
Новый профиль получает не больше 8 ядер и не больше числа логических CPU хоста;
стабильность на другом хосте/драйвере или с меньшим числом ядер отдельно не проверена.
API 31/33/35 используют 2 ядра, 1080×1920/density 420;
API 31/33 — 2048 МБ RAM, API 35 — 4096 МБ RAM.
Отключение boot animation не меняет анимации приложения; параметр описан в
[документации Android](https://developer.android.com/studio/run/emulator-commandline).
Он проверяет Android API, но не интеграцию Google Play/Google services.
Если существующий API 36 AVD создан с Google APIs, runner откажется менять его образ
на месте: сначала остановите именно принадлежащий ему процесс и сохраните старый
профиль отдельно. Не переносите пользовательские данные между разными образами.
После boot runner сохраняет `boot-window.log`; уже видимый ANR System UI останавливает
запуск до Gradle как ошибку окружения. Эта проверка не исключает ANR позднее.
Runner также сверяет `ro.build.version.sdk` с выбранным API; summary.json содержит
версию эмулятора и фактические CPU/RAM из config.ini. Шаблон не перезаписывает
существующий профиль; изменение defaults не меняет ресурсы уже созданного AVD.

The runner creates a small dedicated AVD inside .test-tools/avd, launches it
without a window, waits for boot completion and sets ANDROID_SERIAL explicitly.
Default port is 5586 for API 31, 5580 for API 35, 5582 for API 33 and 5584 for API 36; use -EmulatorPort with another
even port if occupied. Each API has its own Intime_Test_API<API> AVD.
It refuses to use an emulator of another name on that port. No existing personal
AVD is copied or reset. AVD disk data persists, but Gradle removes test APKs after
the suite, which can remove the test app's data. The smoke script installs APKs
once before its phases and does not reinstall them between preparation and reboot
verification, so that fixture must survive the actual reboot.

By default the runner stops an emulator it started. -KeepEmulator retains it for
another run; a reused emulator is left running. To stop the retained test emulator:

```powershell
adb -s emulator-5580 emu avd name # must print Intime_Test_API35
adb -s emulator-5580 emu kill
```

Do not run Gradle or tests in this checkout concurrently with the runner. A lock
prevents two runner invocations from sharing reports or the AVD.

## Реальная доставка и перезагрузка

Для каждого API сначала выполняется обычная suite с сохранением AVD, затем smoke:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test.ps1 -Mode device -TestApi 35 -Offline -KeepEmulator
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-notification-smoke.ps1 -TestApi 35
# После остановки AVD API 35 повторить оба запуска с -TestApi 33.
# Для Android 16 повторить оба запуска с -TestApi 36 (порт 5584).
```

Smoke использует стандартные порты 5580/5582. Перед сменой API остановите прежний
проектный AVD: запись владения рассчитана на один живой экземпляр. Скрипт проверяет
имя AVD, API, PID и время старта, блокирует параллельные прогоны и сохраняет логи
в `.test-tools/runs/*-notification-smoke`. Он оставляет AVD запущенным; остановка
показана выше (для API 33 используйте `emulator-5582`/`Intime_Test_API33`,
для API 36 — `emulator-5584`/`Intime_Test_API36`).

APK устанавливаются из последней сборки через `adb install -r`. Разрешения изменяются
только у `.dev`-приложения на проектном AVD. Запуск launcher и уход домой перед
фикстурой снимают `stopped/notLaunched`: одной instrumentation после установки
недостаточно для получения BOOT_COMPLETED. Отзыв POST_NOTIFICATIONS выполняется
до instrumentation, поскольку Android может завершить процесс при отзыве.

Фазы проверки:

1. Отказ в POST_NOTIFICATIONS и реальная доставка AlarmManager с ACK.
2. Создание двух синтетических задач в файловой Room-базе тестового приложения.
3. Настоящий `adb reboot`; ожидание нового BOOT_COUNT и восстановленного будильника
   AlarmReceiver с точно тем же сроком в `dumpsys alarm`.
4. Проверка сохранности задач и опубликованной сводки BootReceiver без ACK,
   затем удаление только задач фикстуры и восстановление отметки использования.

`sys.boot_completed=1` не означает, что BOOT_COMPLETED доставлен приложению.
Instrumentation до доставки может подавить ожидающий broadcast, поэтому smoke
сначала ждёт восстановления будильника. Тест не заменяет системный reboot вызовом
обработчика. При сбое до второй фазы фикстура может остаться; её проверка и очистка
выполняются отдельным запуском `RebootRecoveryTest#verifyAfterReboot` с тем же APK.

Проверки подтверждают публикацию в Android NotificationManager, параметры тишины,
ACK и восстановление расписания. Прочтение уведомления и физическое звучание на
реальном телефоне ими не подтверждаются. После успешного smoke исходные
пользовательские `main*.db` в рабочем каталоге не используются и не изменяются.

## Обновление APK с синтетическими данными

После извлечения комплекта старых APK по UPGRADE.md запустить выделенный API 35,
затем smoke (вторая команда использует сохранённый AVD):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test.ps1 -Mode device -TestApi 35 -Filter com.vpe_soft.intime.intime.database.HistoricalMigrationPlatformTest -Offline -KeepEmulator
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-upgrade-smoke.ps1 -ApkDirectory .test-tools/upgrade-apks/20261003-113647-3c8591ca -Offline
```

Путь заменить на папку своего извлечённого комплекта со source.json. Текущий сценарий
рассчитан на production-пакет версии 24 и проектный Intime_Test_API35 на emulator-5580.
Если зависимостей ещё нет в кеше, первый запуск выполнить без -Offline.
Smoke проверяет PID/время старта, имя/API AVD, хеши APK и отсутствие установленного
production-пакета; использует общий run.lock. Личные устройства не используются.

Скрипт собирает специальный debug APK с кодом 25, переподписывает копии старых APK
тем же debug-ключом, запускает старый launcher и создаёт синтетическую фикстуру.
Между prepare и verify выполняется install -r без uninstall/pm clear. Фазы проверяют
сохранение данных, Room-миграцию, CRUD/ACK, повторный запуск и активный alarm.
Подробные границы покрытия — в UPGRADE.md. Результат не подтверждает production-подпись
или реальные пользовательские данные. Исходные APK и main*.db не изменяются.

По завершении удаляются установленные этим smoke пакеты, останавливается принадлежащий
runner проектный AVD; -KeepEmulator оставляет его для следующей проверки.
Логи и summary.json сохраняются в `.test-tools/runs/*-upgrade-smoke-*`.
Три фазовых @Test имеют русские комментарии; обычная suite пропускает их,
как и две отдельные reboot-фазы. Запускать upgrade-тесты следует этим host-скриптом.
Не передавать -PupgradeSmoke=true обычным connected/release-задачам:
Gradle разрешает с этим флагом только три явные debug-задачи, перечисленные в UPGRADE.md.

## Импорт исторического JSON в release/R8

`scripts/test-release-smoke.ps1 -HistoricalJsonImport` добавляет к полному smoke
фазу `historical-json-import-and-restart` (всего 15 вместо 14). Она берёт meta
из общей синтетической Play-фикстуры `app/src/sharedTest/resources/play25-backup.json`
и строки из собственного текущего экспорта. Будущий срок задачи позволяет
проверять точный alarm независимо от даты создания исторической фикстуры.
Перед импортом задача удаляется через UI; требуются пустой список и отменённый
alarm. SAF-импорт должен восстановить описание и срок, включая restart процесса.
Все восемь полей в Room эта UI-фаза не читает: их покрывают отдельные
Play25BackupImportTest и ImportTransactionPlatformTest. Личные базы не используются.

Пример для локального кандидата 27/rc3 после bootstrap API 35 с `-KeepEmulator`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-release-smoke.ps1 -TestApi 35 -ApkPath .test-tools/runs/20261009-103501-release27-historical-json/candidate/intime-27-2.0.0-rc3.apk -ExpectedVersionCode 27 -ExpectedVersionName 2.0.0-rc3 -HistoricalJsonImport
```

Требуется matching `mapping.txt` рядом с APK и подтверждённый Upload key.
Скрипт очищает только собственную установку/файл на проектном AVD и оставляет
эмулятор работающим. Этот кандидат не предназначен для установки поверх Play
на личном телефоне: сертификат Upload key отличается от App signing key.
Флаг совместим с полным smoke и RebootOnly; комбинации с Doze/Inexact/Periodic
отклоняются. Прогон не подтверждает естественный Doze или обновление Play APK.

## Results and diagnosis

Every invocation creates .test-tools/runs/<timestamp>-<id>/ with stdout/stderr,
fresh JUnit XML and summary.json. Gradle HTML reports remain under
app/build/reports/tests/testDebugUnitTest and
app/build/reports/androidTests/connected/debug. Device test failures also capture
logcat. No fresh tests means infrastructure failure, even if Gradle exits zero.

Exit codes: 0 = PASSED, 1 = test failures, 2 = infrastructure error,
124 = timeout. Each run forces the test task with -PforceTestRun=true, retaining
incremental compilation. UP-TO-DATE prerequisites are allowed; old test results
cannot be presented as a fresh pass. Default build timeout is 900 seconds;
boot timeout is 300 seconds. Override with -TimeoutSeconds/-BootTimeoutSeconds.

Gradle filesystem watching hung on this machine in
PosixFileSystemFunctions.listFileSystems. The runner uses --no-watch-fs;
configuration cache remains enabled. --no-daemon scopes process cleanup to the
current build; UTF-8 and UTC are explicit for local test execution.

For a failing run, inspect summary.json and gradle.stderr.log/stdout.log first.
Missing cached artifacts require an online bootstrap. Missing SDK/image or
acceleration must be resolved before tests can execute. Timeout cleanup affects
only the runner's process tree, not unrelated Java processes.

## CI

GitLab jobs use compile SDK 36 and publish JUnit/HTML reports even on failure. The manual
instrumentedTests job requires a runner tagged android-emulator with /dev/kvm
available in its container. Without such a runner, device verification runs
locally; the manual CI job must not be claimed as verified.

Проверка публичного GitLab API11 октября2026: последний pipeline
[2909673906](https://gitlab.com/valker/intime/-/pipelines/2909673906) от3 октября
на7fbae394 — success. lintDebug/assembleDebug/debugTests выполнены успешно,
JUnit97/97, failures/errors/skipped0. instrumentedTests — manual,
allow_failure=true, без started_at/finished_at: он не запускался.
Локальный d087322 опережает удалённую chatgpt на59 коммитов. Свежие проверки
актуального кода: scripts/test.ps1 -Mode local -Offline,101/101; затем
gradlew --offline -Pci --console=plain --no-watch-fs :app:lintDebug :app:assembleDebug,
build PASS, lint0 ошибок/30 warnings. Эти локальные результаты не заменяют
pipeline текущего кода. Сводка/копии свежего lint/API:
.test-tools/runs/20261011-083935-ci-preflight; unit:
.test-tools/runs/20261011-084008-f631ff79. CI-конфигурация не менялась;
push/запуск нового pipeline не выполнены, устройство не использовалось.
# Проверки загрузки Play-кандидата (9 октября 2026)

`node scripts/prepare-play-candidate.test.mjs` использует mock HTTP и точный
архивный AAB27; сервисный ключ, API, телефон и БД не читает. Все 8 тестов
содержат русские JSDoc-комментарии. После рефакторинга общего OAuth/requestJson
также выполнен `node scripts/play-api.test.mjs`: 13/13. Свежие логи:
`.test-tools/runs/20261009-140119-play27-api-host/`.
Это не проверка реального commit, скачанной подписи или upgrade. Реальный
upload27 прошёл; первоначальный download дал404. После явного разрешения
commit APK получен и проверен. Android runtime не менялся; свежие API35 UI3/3
и отдельный Play25→27 upgrade9/9 фаз прошли в следующем этапе, см.
DEVICE_TEST_RESULTS.md и UPGRADE.md. JVM suite не повторялась для API/документации.
Ограничения и продолжение описаны в PLAY_API.md.

Для проверки реальных задач пользователь отдельно предоставил JSON export25
с 17 строками. Порядок и границы логической копии описаны в UPGRADE.md.
test-play-upgrade.ps1 остаётся runner для двух синтетических будущих задач;
для JSON-копии добавлен отдельный test-real-copy-upgrade.ps1 и Python helper.

После успешного штатного API35 UI bootstrap с KeepEmulator:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-real-copy-upgrade.ps1 -BackupJsonPath 'C:/путь/к/экспорту25.json' -OldApkPath 'C:/Users/Valentin/Downloads/25.apk' -NewApkPath 'C:/Projects/intime/.test-tools/runs/20261009-151639-play27-af6bca99/intime-play-27-2.0.0-rc3.apk' -PythonPath 'C:/Users/Valentin/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe' -ExpectedNewVersionCode 27 -ExpectedNewVersionName '2.0.0-rc3'
```

Runner принимает только явно указанный JSON, сохраняет его hash и отдельную
копию в ignored run; не адресует телефон и пользовательские main*.db. Seed
создаёт новую schema5 с исходными восемью полями/ID и датами. Install-r
использует оригинальные Play APK; metadata/signature и ownership проверяются.
Все исходные строки сверяются после Room migration, exports и process restart.
CRUD/ACK выполняется только на дополнительно созданной синтетической задаче.
Перед force-stop после удаления проверяется экспорт: finish() экрана сам по
себе не доказывает завершение асинхронной операции. WasNotified проверяется
как 0/1, а не обязательно 0 для всех реальных строк. Доставка alarm этим
сценарием не доказывается. Snapshot учитывает WAL/SHM, SQLite открывается ro.
Логи/UI/snapshots/private-backup содержат частные значения и остаются только
в ignored .test-tools/runs; общий summary содержит counts/hashes/status.
После прогона runner удаляет свою установку/файлы с AVD и останавливает его.
Исходный JSON не меняется; физический SQLite/WAL и preferences не воспроизводятся.

Host-проверки, без пользовательских файлов:

```powershell
& 'C:/Users/Valentin/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe' -B scripts/test_real_copy_fixture.py
```

Четыре теста с русскими комментариями проверяют точные значения/ID/Unicode,
AUTOINCREMENT по сохранившимся ID, неизменность исходника, отказ перезаписи,
неверные contracts и обнаружение изменённого поля schema6. Android migration
в host-тесте моделируется; настоящий Room проверяется только device-сценарием.

Свежие результаты для предоставленного JSON17 задач: host4/4
`.test-tools/runs/20261009-154841-real-copy-host/tests.log`,API35 UI3/3
`.test-tools/runs/20261009-154324-03e5d242/summary.json`,device9/9
`.test-tools/runs/20261009-154653-real-copy-upgrade-638aba16/summary.json`.
Первый device7/9 сохранён,исправлено раннее force-stop после async delete probe.
Cleanup=true,исходный JSON не менялся. R4.2 закрыт для логической JSON-копии;
не для исходных SQLite/WAL/preferences телефона. Runtime приложения не менялся,
JVM suite повторно не запускалась; новые host/device-сценарии выполнены последовательно.
# Focused rc3 matrix API31/33/36

9 октября свежие UI3/3 и release9/9 прошли на каждом API31/33/36. Для повторения
на каждом выбранном API последовательно: штатный test.ps1 device bootstrap
с ScreenPlatformTest,-Offline,-KeepEmulator,-EmulatorGpu host,
-DisableEmulatorVulkan,-EmulatorRamMb4096; затем:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-release-smoke.ps1 -ApkPath 'C:/Projects/intime/.test-tools/runs/20261009-103501-release27-historical-json/candidate/intime-27-2.0.0-rc3.apk' -TestApi 31 -RebootOnly -HistoricalJsonImport -ExpectedVersionCode 27 -ExpectedVersionName '2.0.0-rc3'
```

Подставлять тот же API31/33/36 в оба runner. Это архивный Upload-key APK с
matching mapping,не Play APK. Release runner удаляет свою установку,но оставляет
AVD; остановка в этом batch выполнялась только после проверки собственного
PID/starttime/AVD name. Не останавливать чужие эмуляторы. Общий проверенный итог:
`.test-tools/runs/20261009-164433-rc3-cross-api/confirmed-matrix.json`.
No new images; scope/отказы host-агрегатора сохранены в DEVICE_TEST_RESULTS.md.
При shell-агрегации KeepEmulator может наследовать pipe/stdout handles:
не считать зависшую оболочку провалом тестов и не принимать null exit code за0.
Скрытый Start-Process с сохранённым Handle и WaitForExit самого дочернего
PowerShell позволяет получить exit code; активный UI stdout может требовать
file sharing,поэтому outcome подтверждается свежим runner summary.
Не использовать эти сведения как разрешение на повторное чтение старых отчётов
в новых тестовых запусках:31/33 здесь возобновлялись только в текущем этапе.

## Функциональная проверка жеста Back на API 33/35/36

`PredictiveBackPlatformTest` запускается только на выделенных `Intime_Test_API*`.
Каждый выбранный API проверять последовательно, без `-KeepEmulator`:

```powershell
foreach ($gestureApi in @(33, 35, 36)) {
    powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test.ps1 -Mode device -TestApi $gestureApi -Filter com.vpe_soft.intime.intime.activity.PredictiveBackPlatformTest -Offline -EmulatorGpu host -DisableEmulatorVulkan -EmulatorRamMb 4096 -EmulatorSoftwareNavigation
    if ($LASTEXITCODE -ne 0) { throw "Gesture test failed on API $gestureApi" }
}
```

`-EmulatorSoftwareNavigation` передаёт новому процессу `qemu.hw.mainkeys=0`:
системная экранная навигация нужна для обработчика бокового жеста. INI/данные
AVD не изменяются, образ не скачивается. При подтверждении этого параметра для
сохранённого процесса runner требует совпадающую запись владения; старые записи
без этого поля не подтверждают режим. Обработчик SystemUI должен быть enabled,
attached и gestural: одного `settings navigation_mode=2` недостаточно.

Тест временно включает gestural overlay, на API 33/34 developer flag Back,
вводит касания пальцем от левого края (отмена с возвратом к краю, затем завершение).
Проверяет прежний экземпляр настроек после отмены, прежний экземпляр списка
после завершения и сохранность ID/описания/next_alarm/last_ack одной in-memory
задачи. Исходные overlay/navigation mode и developer flag восстанавливаются
в finally с проверкой mode/flag; runner останавливает собственный AVD.
KEYCODE_BACK и вызовы callback не используются. Анимация визуально, правый край,
back-to-home, остальные экраны, release/R8 и OEM этим debug-тестом не покрыты.
API ниже 33 пропускаются по SdkSuppress и не считаются покрытием этого сценария.

## Полный rc3 smoke уведомлений API 31/33/36 — 9 октября

Свежий последовательный этап: UI3/3 и release15/15 каждого. Summary и общая
сверка: `.test-tools/runs/20261009-175200-rc3-notification-matrix/confirmed-matrix.json`.
На каждом выбранном API сначала штатный `test.ps1 -Mode device -TestApi <API>`
с фильтром `com.vpe_soft.intime.intime.activity.ScreenPlatformTest`, `-Offline`,
`-KeepEmulator`, `-EmulatorGpu host -DisableEmulatorVulkan -EmulatorRamMb 4096`.
Затем тот же API передать полному release runner:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-release-smoke.ps1 -ApkPath 'C:/Projects/intime/.test-tools/runs/20261009-103501-release27-historical-json/candidate/intime-27-2.0.0-rc3.apk' -TestApi 31 -HistoricalJsonImport -ExpectedVersionCode 27 -ExpectedVersionName '2.0.0-rc3'
```

Без `-RebootOnly`: добавляются настоящая доставка/notification ACK, запрет и
возврат уведомлений, выключение/включение канала. На API31 общий запрет через
Settings, на API33/36 POST_NOTIFICATIONS. Восстановление проверяется после нового
UI ACK; старая просрочка без ACK не покрыта. Receiver вручную не вызывается,
часы не меняются. Это exact alarm при разрешённом appop, не Doze/inexact/worker.
Release runner удаляет собственную установку и файлы, но оставляет AVD.
После каждого API batch остановил только свой PID с проверкой starttime/AVD
name, затем удалил свою запись владения; чужие процессы не останавливать.
Новые образы не скачивались. Итоговые отчёты занимают около82.74 MiB: удалены
три byte-identical adb-копии итогового logcat, основные logcat.log сохранены.
Данные AVD и Gradle cache этим измерением не охватываются. Подробности результатов
и границы — DEVICE_TEST_RESULTS.md; runtime не менялся, JVM suite не повторялась.

## Свежая inexact-матрица rc3 — 9 октября

На API31/33/35/36 последовательно прошли свежие ScreenPlatformTest3/3 и
release11/11 каждого. Общая сверка:
`.test-tools/runs/20261009-185151-rc3-inexact-matrix/confirmed-matrix.json`.
Bootstrap: `test.ps1 -Mode device -TestApi <API>` с ScreenPlatformTest,
`-Offline -KeepEmulator -EmulatorGpu host -DisableEmulatorVulkan -EmulatorRamMb 4096`.
Затем тот же API передать release runner:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-release-smoke.ps1 -ApkPath 'C:/Projects/intime/.test-tools/runs/20261009-103501-release27-historical-json/candidate/intime-27-2.0.0-rc3.apk' -TestApi 31 -InexactOnly -ExpectedVersionCode 27 -ExpectedVersionName '2.0.0-rc3'
```

Не совмещать с HistoricalJsonImport/RebootOnly/PeriodicOnly. Exact appop запрещён
только новой установке runner; требуется положительное окно alarm, запись
собственного AlarmReceiver и уведомление с ACK. После ACK/process restart
проверяются сохранение запрета и новое положительное окно. Quiet worker не
заменяет доставку alarm. Часы/планировщик не ускоряются. Doze/квоты этим режимом
не проверяются. Тайм-аут15 минут — INCONCLUSIVE, разрыв наблюдения — отдельный
инфраструктурный результат. В этой матрице все четыре прошли с первой попытки.
После каждого API остановлен только собственный PID с проверкой starttime/AVD
name, установки очищены. Новые образы не скачивались; отчёты около74.09 MiB,
без учёта роста AVD/cache. Runtime не менялся, JVM suite повторно не запускалась.

## Rc3 natural periodic на API35/36 — 9 октября

Свежий этап: UI3/3 и release10/10 каждого,по одному естественному тихому повтору
после смерти процесса. Итог после повтора API35 и границы:
`.test-tools/runs/20261009-201736-rc3-periodic-matrix/confirmed-matrix.json`.
Bootstrap каждого API: штатный test.ps1 с ScreenPlatformTest,
`-Offline -KeepEmulator -EmulatorGpu host -DisableEmulatorVulkan -EmulatorRamMb 4096`.
Затем на том же выбранном API:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-release-smoke.ps1 -ApkPath 'C:/Projects/intime/.test-tools/runs/20261009-103501-release27-historical-json/candidate/intime-27-2.0.0-rc3.apk' -TestApi 35 -PeriodicOnly -PeriodicWaitMinutes 65 -ExpectedVersionCode 27 -ExpectedVersionName '2.0.0-rc3'
```

PeriodicRepeats=1 по умолчанию. Новый этап не подтверждает второй период,rc3 на
31/33 или Doze/OEM. До ожидания есть реальный alarm,процесс убивается через am kill
в фоне,его отсутствие сверяется. Затем нужны новое тихое уведомление без ACK и
WorkManager SUCCESS; JobScheduler не опрашивается внутри ожидания. Непрерывность
host/guest проверяется,тайм-аут и разрыв наблюдения не становятся natural PASS.

Перед reboot на API35/36 теперь проверяется наличие wait-for-handler и
wait-for-background-handler в cmd package help и ожидаются обе штатные очереди.
Команды не снимают stopped и не вызывают receiver. Новый help guard выполнен на
повторе35; прогон36 был запущен до этой правки и использовал прежнее ожидание
очередей36. Первый35 FAILED при stopped=true после reboot сохранён; успех с более
строгой подготовкой не доказывает причину/устранение редкого сбоя или запись всех
файлов PackageManager. Подробности — DEVICE_TEST_RESULTS.md.
Свои установки/AVD очищены,новых образов нет. Отчёты всего этапа около43.89 MiB
после удаления17.53 MiB побайтовых adb-дубликатов; основные журналы сохранены,
удалённые/сохранённые пути и SHA записаны в removed-duplicate-logs.json.
Размер AVD/cache этим измерением не охвачен. Код приложения не менялся; JVM suite
не повторялась,PS parser/diff check и реальные device-сценарии выполнены.

## Rc3 natural periodic на API31/33 — 9–10 октября

Следующий последовательный этап прошёл с первой попытки: UI3/3 и release10/10
каждого API, один естественный тихий повтор после смерти процесса.
Сверка: `.test-tools/runs/20261009-233856-rc3-periodic31-33-matrix/confirmed-matrix.json`.
Bootstrap ScreenPlatformTest выполнялся через scripts/test.ps1 с параметрами
`-Offline -KeepEmulator -EmulatorGpu host -DisableEmulatorVulkan -EmulatorRamMb 4096`.
После bootstrap на том же выделенном AVD:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-release-smoke.ps1 -ApkPath 'C:/Projects/intime/.test-tools/runs/20261009-103501-release27-historical-json/candidate/intime-27-2.0.0-rc3.apk' -TestApi 31 -PeriodicOnly -PeriodicWaitMinutes 65 -ExpectedVersionCode 27 -ExpectedVersionName '2.0.0-rc3'
```

Для API33 повторить bootstrap и команду с `-TestApi 33` после завершения/остановки
API31. PeriodicRepeats=1 по умолчанию. Интервалы от первого alarm:
1592.132/1583.747с; observedRepeats=1, WorkManager SUCCESS=2 каждого.
Два SUCCESS не доказывают второй период. NaturalDispatchValidated=true;
процесс отсутствовал после am kill, пакет оставался stopped=false. Новое тихое
уведомление без ACK подтверждено без ручного запуска/ускорения worker и без
JobScheduler dumps внутри ожидания. Reboot восстановил тот же alarm.
Перед reboot на31 действует RebootSettleSeconds=15; ожидания очередей35/36
на31/33 не используются. Код приложения/постоянного runner не менялся,
JVM suite не повторялась. Собственные AVD очищены/остановлены, телефон и новые
образы не использовались. Отчёты около47.75 MiB, без измерения роста AVD/cache.
Один повтор rc3 проверен на31/33/35/36; второй период, Doze/квоты, OEM,
API24/16КБ и release predictive остаются отдельными проверками.

## Rc3: второй natural periodic период API35/36 — 10 октября

Свежие UI3/3 и release11/11 каждого; API35 после сохранённого отказа подготовки
reboot, API36 с первой попытки. Итог/границы:
`.test-tools/runs/20261010-110833-rc3-two-period35-36-matrix/confirmed-matrix.json`.
После последовательного bootstrap ScreenPlatformTest через scripts/test.ps1
с `-Offline -KeepEmulator -EmulatorGpu host -DisableEmulatorVulkan -EmulatorRamMb 4096`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-release-smoke.ps1 -ApkPath 'C:/Projects/intime/.test-tools/runs/20261009-103501-release27-historical-json/candidate/intime-27-2.0.0-rc3.apk' -TestApi 35 -PeriodicOnly -PeriodicRepeats 2 -PeriodicWaitMinutes 65 -ExpectedVersionCode 27 -ExpectedVersionName '2.0.0-rc3'
```

Для36 сменить TestApi после очистки/остановки35. Два разных when и два SUCCESS
нужны в одном непрерывном окне; не открывать Activity, не вызывать manual job run,
не менять период/часы/batching и не опрашивать JobScheduler между отправками.
Собственные AVD остановлены. Новых образов/телефона нет; логи около41.76 MiB,
без измерения роста AVD/cache. Детали интервалов и сохранённого FAILED — в
DEVICE_TEST_RESULTS.md. Второй период rc3 на31/33 и остальные открытые
устройственные сценарии этим этапом не закрыты.

Перед reboot пауза RebootSettleSeconds теперь применяется на31/35/36, затем
на35/36 ожидаются очереди PackageManager. Фактическая пауза отдельно отмечается
rebootSettleAppliedSeconds; одно наличие параметра не доказывает его применение.
Retry35 проверил новую паузу15с;36 работал до правки. Это подготовка теста,
не доказательство причины или устойчивого устранения stopped после reboot.

Host-регрессии проверяют реальную функцию guard через AST без запуска release
runner, приложения или AVD. Каждый случай содержит подробный русский комментарий:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-release-quiet-guard.ps1
```

Прошли13 фикстур: корректные compat/native форматы и отказ при подмене
SILENT словом в header, отсутствии группы/флагов, наличии ACK/звука,
неверном priority/groupAlertBehavior. По завершении текущего этапа новым guard
также проверены все4 сохранённых repeat-records; результат в quiet-guard-summary.json
общего batch. Live observers были запущены до изменения guard; post-run проверка
record не подменяет fresh device-наблюдение или WorkManager SUCCESS.
Код приложения не менялся, JVM suite не повторялась; PS parser/diff checks прошли.

## Rc3: два natural periodic повтора API31/33 — 10 октября

Свежие UI3/3 и release11/11 каждого прошли с первой попытки. Итог:
`.test-tools/runs/20261010-130930-rc3-two-period31-33-matrix/confirmed-matrix.json`.
После последовательного bootstrap ScreenPlatformTest через scripts/test.ps1
с `-Offline -KeepEmulator -EmulatorGpu host -DisableEmulatorVulkan -EmulatorRamMb 4096`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-release-smoke.ps1 -ApkPath 'C:/Projects/intime/.test-tools/runs/20261009-103501-release27-historical-json/candidate/intime-27-2.0.0-rc3.apk' -TestApi 31 -PeriodicOnly -PeriodicRepeats 2 -PeriodicWaitMinutes 65 -ExpectedVersionCode 27 -ExpectedVersionName '2.0.0-rc3'
```

Для33 сменить TestApi после очистки/остановки31. Подтверждены два разных when
и каждый интервал>=900с:31 —1700.476/900.117с;33 —1826.242/1042.914с.
На каждом workerSuccessCount=3, observedRepeats=2; не выводить число уведомлений
из числа SUCCESS. NaturalDispatchValidated=true, процесс отсутствовал после
am kill. Activity/manual dispatch, изменение периода/часов/batching и live
JobScheduler dumps внутри окна не использовались. Новый quiet guard выполнялся
в live-наблюдателях. Reboot восстановил alarm до Activity; фактическая пауза
до reboot31=15с,33=0с. Подробности record и границы — DEVICE_TEST_RESULTS.md.

AVD очищены/остановлены, телефон и новые образы не использовались. Отчёты
около29.86 MiB после удаления18.46 MiB побайтовых adb-дубликатов; named logs
сохранены, инвентарь в batch. Рост AVD/cache отдельно не измерен. Приложение
и постоянные скрипты не менялись, JVM suite не повторялась. Вместе с предыдущим
этапом два тихих повтора rc3 проверены на31/33/35/36; Doze/квоты/OEM,
API24/16КБ, release predictive, Play-signing APK и устойчивость reboot35/36
остаются отдельными сценариями. Следующий шаг — повторные reboot35/36
с новой подготовкой.

## Rc3: повторные reboot API35/36 с новой подготовкой — 10 октября

Оба API с первой попытки: UI3/3 и release10/10 каждого, три reboot на каждом.
Свежая независимая сверка:
`.test-tools/runs/20261010-165355-rc3-reboot35-36-matrix/confirmed-matrix.json`.
После bootstrap ScreenPlatformTest через scripts/test.ps1 с `-Offline
-KeepEmulator -EmulatorGpu host -DisableEmulatorVulkan -EmulatorRamMb 4096`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-release-smoke.ps1 -ApkPath 'C:/Projects/intime/.test-tools/runs/20261009-103501-release27-historical-json/candidate/intime-27-2.0.0-rc3.apk' -TestApi 35 -RebootOnly -RebootRepeats 3 -RebootSettleSeconds 15 -ExpectedVersionCode 27 -ExpectedVersionName '2.0.0-rc3'
```

Для36 сменить TestApi после очистки/остановки35. Семь подготовительных фаз
и три reboot дают10 фаз. Перед каждым повтором воспроизводится force-stop/запуск;
до reboot проверяются описание/alarm, после — тот же alarm до Activity.
Фактическая пауза15с, обе очереди Success, stopped=false до/после и вызов
BootReceiver сверены для всех шести циклов. Boot count35:46→47→48→49,
36:29→30→31→32. ANR/fatal нет. Не снимали stopped и не вызывали receiver вручную.

Это успешная конечная выборка, не доказательство причины/устойчивого устранения
редкого stopped-сбоя. Доставка при boot просроченной задачи, notification,
worker, Doze/квоты/OEM и release predictive этим режимом не покрываются.
AVD остановлены, телефон/новые образы не использовались. Отчёты43.91 MiB
после удаления34.35 MiB идентичных adb-дубликатов; named logs сохранены,
инвентарь в batch. Рост AVD/cache отдельно не измерен. Приложение/постоянные
скрипты не менялись, JVM suite не повторялась. Следующий шаг — release/R8
отмена и завершение бокового Back-жеста на33/35/36.

## Rc3: release/R8 боковой Back-жест API33/35/36 — 10 октября

Финальные UI3/3 + release9/9 каждого.35/36 с первой попытки,33 после
сохранённых focus/parser отказов. Сводка/точные границы — DEVICE_TEST_RESULTS.md;
`.test-tools/runs/20261010-172054-rc3-gesture33-35-36-matrix/confirmed-matrix.json`.
Bootstrap через scripts/test.ps1: ScreenPlatformTest, `-Offline -KeepEmulator
-EmulatorGpu host -DisableEmulatorVulkan -EmulatorRamMb 4096 -EmulatorSoftwareNavigation`.
Экранная навигация обязательна для активного EdgeBackGestureHandler.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-release-smoke.ps1 -ApkPath 'C:/Projects/intime/.test-tools/runs/20261009-103501-release27-historical-json/candidate/intime-27-2.0.0-rc3.apk' -TestApi 33 -GestureOnly -ExpectedVersionCode 27 -ExpectedVersionName '2.0.0-rc3'
```

Повторять35/36 последовательно после очистки/остановки предыдущего AVD.
GestureOnly совместим только с API33/35/36, без остальных режимов/repeats.
Семь подготовительных фаз и cancel/commit дают9 фаз; reboot/notification
не запускаются. Реальный непрерывный левый свайп вводится shell UiDevice;
отмена сохраняет Settings ActivityRecord, завершение возвращает прежний Main
ActivityRecord/task, сохраняет PID, видимое описание и alarm. Прямой Java
instance/поля Room/кадры анимации не проверяются. Navigation overlay/mode
и developer flag восстанавливаются даже при ошибке; на33 flag временно1.
После этапа остановить только собственный AVD с проверкой PID/starttime/имени.

Регрессии реальной функции ActivityRecord parser через AST, без adb/AVD:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-release-activity-record.ps1
```

Свежие5/5 проверяют оба формата скобки, одинаковый token в двух строках,
отказ при неоднозначности и чужом пакете. Все12 финальных сохранённых снимков
трёх API дополнительно сверены новым parser. Quiet guard host13/13 прошли.
UI фокус не ослаблялся; исходные FAILED сохранены. Приложение/архивный APK
не менялись, JVM suite не повторялась. AVD остановлены, телефон/новые образы
не использовались. Отчёты15.23 MiB после удаления6.63 MiB идентичных adb-логов;
named logs сохранены, рост AVD/cache отдельно не измерен. Следующий шаг —
естественный light idle на31/33. Visual predictive/OEM/прочие открытые проверки
не заменяются этим функциональным жестом.

## Штатный вход контроллера в light idle API31/33 — 10 октября

Свежие UI3/3 каждого, оба API с первой попытки; новый parser host9/9.
Итог: `.test-tools/runs/20261010-194057-natural-light31-33-matrix/confirmed-matrix.json`.
После bootstrap ScreenPlatformTest через scripts/test.ps1 с `-Offline
-KeepEmulator -EmulatorGpu host -DisableEmulatorVulkan -EmulatorRamMb 4096`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-natural-light-idle.ps1 -TestApi 31 -WaitMinutes 20
```

Для33 сменить TestApi после остановки своего31. Observer принимает только
проверенный выделенный AVD31/33, исходный screen on/light ACTIVE, отсутствие
forced idle и BatteryService simulation. Устанавливает emulator-console virtual
AC off/status discharging и KEYCODE_SLEEP; настройки Doze/таймеры не меняет,
force-idle/step не выполняет. Три службы опрашиваются каждые30с до двух подряд
согласованных IDLE/light=true при screen off/нет зарядки/Asleep или Dozing.
Ограничение WaitMinutes6–30, по умолчанию20; отсутствие входа даёт INCONCLUSIVE,
не PASS приложения. Потеря условий/ошибка/host-разрыв>90с дают infrastructure
error. В finally восстанавливаются исходные emulator AC/status и screen on;
проверяется ACTIVE и battery state. После этого остановить только свой AVD
с проверкой process/starttime/имени. Не применять к телефону/Pixel_8.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-light-idle-parser.ps1
```

Host9/9 проверяет реальную функцию через AST, включая отрицательные случаи;
подробные русские комментарии описывают условия/действия/ожидания.
Фактический результат observer NATURAL_LIGHT_IDLE_OBSERVED (exit0) означает
вход среды, naturalEntryValidated=true и notificationDeliveryValidated=false.
На31 последние два IDLE-снимка122.245/152.646с, на33 —400.496/431.039с.
Это время опросов, не точный момент входа/непрерывная трасса. Все light_* constants
до/после совпали. Первичные snapshots сохраняются, временные успешные adb logs
удаляются после чтения. Отчёты10.11 MiB, рост AVD/cache отдельно не измерен.
Питание восстановлено, AVD остановлены, новых образов/телефона нет.
Приложение/release APK не менялись, JVM suite не повторялась. Этот сценарий
не устанавливает release APK и не проверяет его доставку/ACK/worker. Следующий
шаг — отдельная доставка rc3 в штатном light idle31/33; физический natural
Doze/OEM и остальные gaps остаются открытыми. Различие full/light описано в
[документации Android](https://source.android.com/docs/core/power/platform_mgmt).

## Exact-доставка rc3 после штатного light entry API31/33

После bootstrap через `scripts/test.ps1 -Mode device -TestApi 31
-Filter com.vpe_soft.intime.intime.activity.ScreenPlatformTest -Offline
-KeepEmulator -EmulatorGpu host -DisableEmulatorVulkan -EmulatorRamMb 4096`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-release-smoke.ps1 -ApkPath .test-tools/runs/20261009-103501-release27-historical-json/candidate/intime-27-2.0.0-rc3.apk -TestApi 31 -LightIdleOnly -ExpectedVersionCode 27 -ExpectedVersionName 2.0.0-rc3
```

Для33 заменить TestApi в обеих командах, последовательно остановив свой31.
LightIdleOnly принимает только31/33 и несовместим с другими режимами/повторами.
13 фаз включают CRUD/JSON/reboot, exact alarm с appops allow/window0, light
entry, реальную доставку и notification ACK/restart. После подготовки интервал
задачи4мин на31/8мин на33; срок проверяется по AlarmManager. Это обычный срок
задачи, системные light timers не меняются. Virtual AC off/status discharging
и screen off подготавливают среду; force-idle/step/ручной dispatch отсутствуют.

Каждые15с сохраняются первичные deviceidle/power/battery snapshots. Нужны два
подряд согласованных light IDLE до срока; после подтверждения каждый снимок
до доставки должен оставаться IDLE. Отсутствие входа/потеря IDLE/таймаут18мин
дают INCONCLUSIVE, разрыв опросов>90с — INFRASTRUCTURE_INTERRUPTED. Это дискретные
снимки, не непрерывная трасса. Доставка требует собственного NotificationRecord
с action и временем не раньше alarm, реального AlarmReceiver и потребления alarm;
при доставке ещё раз сверяются три службы. Звук/точный момент видимости не измеряются.

Затем восстанавливаются исходные virtual AC/status/экран; проверяются ACTIVE,
battery state и неизменность всех light_* constants. ACK через notification action
выполняется уже с включённым экраном, проверяет новый срок с тем же интервалом,
исчезновение уведомления и сохранение alarm после process restart. В finally
повторяется незавершённое восстановление питания, удаляется только своя установка.
По окончании остановить только собственный AVD с проверкой PID/starttime/имени.
Телефон/Pixel_8 не использовать. Этот режим не проверяет inexact fallback,
worker в maintenance, квоты, physical/OEM Doze или Play-signing APK.

Свежий итог10 октября:
`.test-tools/runs/20261010-210008-rc3-light-delivery31-33-matrix/confirmed-matrix.json`.
UI3/3 и release13/13 обоих, host light9/9/ActivityRecord5/5/quiet13/13.
API31 с первой попытки; API33 после отдельного полного повтора вслед за ANR
Google launcher/search при reboot. Исходный batch FAILED и первичные журналы
сохранены; ANR checks не отключались, AVD не сбрасывался. Два IDLE-снимка31:
77.266/92.603с,33:355.393/370.822с. Notification when позже alarm на0.018/0.023с;
ACK remaining239.269/478.932с при интервале4/8мин, alarm тот же после restart.
Все сырые light-снимки независимо сверены parser, включая состояние при доставке.
Питание восстановлено, light constants неизменны, AVD остановлены.
Отчёты40.33 MiB после удаления23.64 MiB идентичных numbered adb logs с проверкой
SHA-256; named logs сохранены. Рост AVD/cache не измерен, новых образов/телефона
нет. Runtime/архивный APK прежние, JVM suite не повторялась. Следующий шаг —
inexact light fallback с отдельным наблюдением maintenance; worker остаётся открыт.

## Fallback после штатного light entry API31/33

После такого же bootstrap через `scripts/test.ps1` добавить `-InexactOnly`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-release-smoke.ps1 -ApkPath .test-tools/runs/20261009-103501-release27-historical-json/candidate/intime-27-2.0.0-rc3.apk -TestApi 31 -LightIdleOnly -InexactOnly -ExpectedVersionCode 27 -ExpectedVersionName 2.0.0-rc3
```

На33 заменить TestApi в bootstrap/release, выполнять последовательно.14 фаз:
13 light-фаз и дополнительная проверка положительного ACK alarm window.
До отключения питания требуются appops deny, собственный будущий RTC_WAKEUP
с положительным окном и реальная строка fallback scheduling. Два согласованных
light IDLE-снимка нужны до срока задачи. После входа fallback допускает только
готовый по экрану/питанию IDLE с PowerManager light=true либо IDLE_MAINTENANCE
с light=false. Maintenance отмечается отдельно, не называется сохранением IDLE;
иные/несогласованные состояния дают INCONCLUSIVE. Exact-режим по-прежнему
принимает только IDLE. Наблюдение каждые15с не доказывает отсутствия кратких
переходов между снимками; `lightMaintenanceObserved=false` означает, что они
не попали в сохранённые снимки. Состояние при обнаружении уведомления
сохраняется отдельно в `lightDeliveryState` и трёх первичных журналах.

Доставка/ACK/restart/cleanup проверяются как в exact light-режиме; после ACK
appops должен оставаться deny, alarm — иметь тот же срок и положительное окно.
Scheduling log сохраняется до ожидания, чтобы не зависеть от вытеснения ранних
строк logcat. Питание/экран восстанавливаются до ACK. Таймаут18мин и отсутствие
входа не являются PASS; force-idle/step/системные таймеры не используются.
Не применять к личному телефону/Pixel_8. Worker, квоты и physical/OEM Doze
остаются отдельными проверками.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-release-light-state.ps1
```

9 host-фикстур берут реальный классификатор через AST: exact/fallback IDLE,
разрешённое только fallback maintenance, несогласованность обеих служб,
потеря подготовки, force-idle и возврат ACTIVE. Каждый случай снабжён русским
комментарием о начальных условиях, действии и ожидаемом результате.

Свежий результат10 октября — PASSED с первой попытки обоих API:
`.test-tools/runs/20261010-213902-rc3-light-inexact31-33-matrix/confirmed-matrix.json`.
UI3/3 и release14/14 каждого; классификатор9/9, parser9/9, ActivityRecord5/5,
quiet13/13.21 сохранённый exact-снимок принят новым строгим exact-классификатором
как регрессионные данные, а не новый device-прогон. Два IDLE-снимка31:
124.759/140.252с,33:323.747/339.108с до срока. Delivery detected31 в maintenance,
33 в IDLE; when−deadline179.915/114.712с при window179.891/359.903с.
На33 maintenance в сохранённых снимках не наблюдалось; непрерывное отсутствие
переходов/причина задержки/квоты не доказаны. ACK remaining239.232/479.228с,
deny/положительное окно и тот же alarm после restart подтверждены.
Первичные снимки независимо сверены, питание восстановлено, constants прежние,
ANR/fatal не обнаружены, свои AVD остановлены. Отчёты31.53 MiB после удаления
16.10 MiB идентичных adb-дублей с проверкой размера/SHA-256; named logs сохранены.
Рост AVD/cache не измерен, новых образов/телефона нет. Runtime/архивный APK
прежние, JVM suite не повторялась. Далее — отдельный тихий повтор worker после
штатного light entry31/33 с наблюдением maintenance.

## Тихий worker после штатного light entry API31/33

После bootstrap ScreenPlatformTest через `scripts/test.ps1 -Mode device -TestApi 31
-Filter com.vpe_soft.intime.intime.activity.ScreenPlatformTest -Offline -KeepEmulator
-EmulatorGpu host -DisableEmulatorVulkan -EmulatorRamMb 4096`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-release-smoke.ps1 -ApkPath .test-tools/runs/20261009-103501-release27-historical-json/candidate/intime-27-2.0.0-rc3.apk -TestApi 31 -PeriodicOnly -PeriodicLightIdle -PeriodicWaitMinutes 45 -ExpectedVersionCode 27 -ExpectedVersionName 2.0.0-rc3
```

Для33 заменить TestApi в обеих командах после остановки своего31. Режим
PeriodicLightIdle допускает только PeriodicOnly/один повтор на31/33, без других
режимов и дополнительных reboot.11 фаз:10 обычных periodic и подтверждение
штатного light entry. Архивный APK не пересобирается; личный телефон/Pixel_8
не использовать. Новый SDK image не требуется.

После первого настоящего минутного alarm (уведомление с action ACK, без нажатия)
сохраняются job, питание и stopped=false; `am kill` завершает лишь фоновый процесс.
Включение virtual AC off/status discharging и screen off начинает наблюдение.
До first notification when+15мин требуются два подряд согласованных IDLE-снимка
DeviceIdleController/PowerManager/BatteryService. Затем каждые30с сохраняются
первичные light snapshots и читается notification; готовые IDLE/light=true либо
IDLE_MAINTENANCE/light=false допускаются отдельно. Возврат ACTIVE/несогласованность
даёт INCONCLUSIVE; потеря подготовки — отказ проверки. Host gap>180с/перевод
часов назад дают INFRASTRUCTURE_INTERRUPTED. Это дискретный опрос, не непрерывная
трасса; отсутствие sampled maintenance не доказывает отсутствия короткого перехода.

Нужен новый quiet notification when>=first+15мин: своё описание, без ACK,
LOW, null sound/vibrate/defaults0 и требуемые flags/group suppression. Затем
проверяются настоящий WorkManager SUCCESS TaskNotificationWorker после смерти
процесса и отсутствие будущего alarm. При обнаружении повторно снимаются три
службы; periodicLightDeliveryState означает состояние обнаружения, не состояние
на точный момент вызова worker. Объект Notification проверяется; физический звук
не измеряется. Private Room/wasNotified/work UUID не читаются.

Не вызываются ручной jobscheduler run/force-idle/step и не меняются системные
light timers, batching, часы или период WorkManager. Jobscheduler dumpsys читается
только до и после ожидания; во время ожидания раз в5мин сохраняется диагностика
трёх служб/uptime. Срок45мин относится к одному ожиданию; отсутствие запуска
к лимиту — INCONCLUSIVE, а не доказанный дефект приложения. Восстанавливаются
исходные virtual AC/status/экран, проверяются ACTIVE и неизменность light_* constants;
finally восстанавливает питание и при отказе. Удаляются только свои установки/
probe/backup. По окончании остановить только свой AVD с проверкой PID/starttime/имени.
Deep/physical/OEM Doze, idle quotas и долговременная устойчивость остаются вне scope.

### Границы активного NotificationRecord

`Get-ReleaseNotificationRecord` в release runner читает только раздел
Notification List и ограничивает запись отступом заголовка. История, настройки
каналов и соседние записи не участвуют в проверках actions/when/flags/описания.
Пустые строки и вложенные extras сохраняются. Production package сравнивается
точно; отсутствие или несколько собственных записей не дают доказательства
нужного уведомления. Старый regex до следующего NotificationRecord мог захватывать
оставшийся dumpsys и ошибочно видеть исторический ACK у тихого повтора.

```powershell
powershell -NoProfile -File scripts/test-release-notification-record.ps1
powershell -NoProfile -File scripts/test-release-quiet-guard.ps1
```

Первый сценарий проверяет 8 случаев границ и реальный quiet guard, второй —
13 комбинаций тихих полей. У каждого случая есть русский комментарий условий,
действия и ожидаемого результата. Это host-проверки формата; они не заменяют
свежий device-прогон, WorkManager SUCCESS или измерение физического звука.

Release runner сохранять в UTF-8 с BOM: Windows PowerShell 5.1 должен правильно
читать русские UI-селекторы. Host-проверка границ также проверяет BOM до загрузки функций.

### Результат light worker31/33 (10–11 октября)

| API | UI | Release | Два IDLE, с | Quiet gap, с | SUCCESS | Состояние обнаружения | Maintenance в снимках |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 31 | 3/3 | 11/11 | 93.322 / 124.421 | 1090.63 | 1 | IDLE | False |
| 33 | 3/3 | 11/11 | 243.427 / 273.778 | 1278.756 | 1 | IDLE | False |

Итог .test-tools/runs/20261010-221654-rc3-light-worker31-33-matrix/confirmed-matrix.json. API33 после исправления
границ NotificationRecord и восстановления UTF-8 BOM, оба прежних FAILED сохранены.
API31 не повторялся: его исходное текущее уведомление сверено новым парсером.
Host8+13+9+9+5; 10 архивных quiet-снимков — регрессия парсера, не fresh devices.
Питание/constants/cleanup проверены, свои AVD остановлены. Отчёты 62.89 MiB,
рост AVD/cache не измерен; телефон/новые images не использовались. Один повтор
каждого AVD; sampled state не доказывает точное состояние запуска, квоты или
физический звук. JVM suite не повторялась; детали/отказы — DEVICE_TEST_RESULTS.md.
Следующий шаг — актуальный удалённый CI R4.4, остальные release gaps открыты.
