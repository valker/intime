# Running tests

The Windows runner works with Windows PowerShell 5.1 and JDK 17. Android Studio
does not need to be open. Use the repository's Gradle Wrapper (8.11.1), AGP 8.9.1
and installed compile SDK 36. Device API 24/31/33/35/36 is independent of compile/target SDK.

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

### Тихий periodic worker в production release/R8 (API 36)

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test.ps1 -Mode device -TestApi 36 -Filter com.vpe_soft.intime.intime.activity.ScreenPlatformTest -Offline -KeepEmulator
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-release-smoke.ps1 -TestApi 36 -ApkPath '.test-tools/runs/release-icons-20261004-221247/intime-25-2.0.0-rc1-icons.apk' -PeriodicOnly -PeriodicWaitMinutes 65
```

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

Этот режим принимает только API 36 и не совмещается с RebootOnly/DozeOnly.

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
с sound/vibrate=null и defaults=0 в Notification, SILENT и groupAlertBehavior=1.
В dumpsys mSound может оставаться звуком канала несмотря на SILENT;
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
./gradlew.bat ':app:assembleRelease' '-PproductionRelease=true' '-PreleaseVersionCode=25' '-PreleaseVersionName=2.0.0-rc1' --offline --no-daemon --no-watch-fs --no-configuration-cache
```

Сохранить APK из `app/build/outputs/apk/release` и `mapping.txt` из
`app/build/outputs/mapping/release` **одной сборки** в отдельную папку отчёта.
Mapping должен лежать рядом с переданным APK. Пример для проверенного артефакта:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-release-smoke.ps1 -ApkPath '.test-tools/runs/release-permissions-20261003-225632/intime-25-2.0.0-rc1-r8.apk'
```

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
RebootSettleSeconds (0–60) применяется только на API 31.
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
