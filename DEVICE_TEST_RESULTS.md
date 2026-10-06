# Проверки уведомлений на Android — 2 октября 2026

## Два естественных quiet periodic периода в release/R8 на API 33 — 6 октября 2026

**PASSED, 11/11 host-фаз**, exit 0:
`.test-tools/runs/20261006-131742-release-smoke/summary.json`.
Свежий UI bootstrap — **3/3**, без ошибок и пропусков:
`.test-tools/runs/20261006-131508-ea2af0d6/summary.json`.
Существующий Google APIs API 33 (`Intime_Test_API33`, emulator-5582), fingerprint
`google/sdk_gphone64_x86_64/emu64x:13/TE1A.240213.009/12342917:userdebug/dev-keys`.
Профиль этого успешного запуска: 2 CPU, 4096 МБ RAM, host GPU, Emulator 37.2.12.
Прежний signed non-debuggable code 25/2.0.0-rc1 APK с иконками не пересобирался,
SHA-256 4F5911443EF3D9A18645C2285E1D1BD2F7B26566F51E7448D2A6D39C40B9D056;
mapping 7B5DEBF5DCCE8160A40A2F0B0442B722CA9DA511984806B7981587E4DC2E2E23.

CRUD/JSON, reboot 9 → 10 и первый минутный alarm с ACK прошли. После Home/am kill
процесс отсутствовал, stopped=false подтверждён. Первый alarm
`when=1791282131353`; первый quiet repeat `1791283902409` пришёл через
**1771.056 с (29 минут 31 секунда)**, следующий `1791285057363` — ещё через
**1154.954 с (19 минут 15 секунд)**. Оба снимка сохранены отдельно; каждый имеет
то же уникальное описание, LOW (-1), без actions/ACK, sound/vibrate=null,
defaults=0x0, flags=0x18, groupKey=silent и groupAlertBehavior=1.
`mSound` сохраняет адрес звука канала; его null и физический звук не утверждаются.
Будущий alarm отсутствует.

В logcat **три** SUCCESS TaskNotificationWorker, один work id
`46ffaa3e-c46c-4d06-8a18-5a41316b761f`, новый PID 5231:
10:33:20.034, 10:51:42.416 и 11:10:57.371 UTC (13:33, 13:51 и 14:10 по Москве).
Ранний SUCCESS через примерно 11 минут после alarm не обновил активное уведомление;
это согласуется с 15-минутной паузой NotificationHelper, но private LAST_POST
и фактическая ветка раннего worker этим сценарием не читались.
Последние два SUCCESS соответствуют двум новым quiet when;
`periodicWorkerSuccessCount=3`, `naturalDispatchValidated=true`.

Перед ожиданием собственное задание Ready:false, future TIMING_DELAY, bucket ACTIVE.
Между повторами Activity/probe/instrumentation не запускались, JobScheduler не
читался; ручного dispatch/doWork и изменения batching/периода/часов не было.
Uptime вырос с 178.26 до 3104.37 с, в согласии с host 13:22:16 → 14:11:02.
Контроль разрывов не сработал; latest показывают питание от сети, заряд 100%,
ACTIVE для deep/light idle. В финальных health-checks нет ANR/FATAL.
Этот успешный запуск не доказывает устранение прежней системной ANR; две
предыдущие неудачные попытки сохранены отдельно ниже. Defaults AVD не менялись.

Runner удалил свою production-установку, probe и уникальный JSON; отсутствие
production-пакета дополнительно подтверждено. PID/StartTime/AVD проверены,
выделенный эмулятор остановлен, маркер удалён. Успешный release-отчёт — 27.4 МиБ;
три release-отчёта этого этапа суммарно — около 69.2 МиБ. SDK/system images не
скачивались, пользовательские main*.db не читались. Host-проверки parser/assertions
описаны ниже. Локальная suite не повторялась: код приложения не менялся.
Обычный Doze/OEM, точный срок 15 минут, private Room и прочие API не покрыты.

## Адаптация periodic runner к формату API 33 — 6 октября 2026

Первая попытка `.test-tools/runs/20261006-130224-release-smoke/summary.json`
завершилась **FAILED** после девяти прошедших фаз, включая настоящий reboot
6 → 7 и доставку минутного alarm. Сообщение `Initial alarm record with ACK missing`
вызвано host-parser: активная запись содержит нужное описание и actions=1,
но API 33 печатает `when=1791281246907` без `/postingTime`. Периодическое ожидание
в этом запуске не начиналось; это не подтверждение сбоя worker или natural pass.
Runner удалил свою установку/probe/JSON; исходный результат сохранён.

Разбор учитывает оба формата when. Общий helper тихого уведомления принимает
hex defaults API 33, проверяет null sound/vibrate, LOW, отсутствие ACK и
groupAlertBehavior=1. На API 33 дополнительно требуются флаги ONLY_ALERT_ONCE и
AUTO_CANCEL (биты 0x18); на API 35/36 сохраняется требование текстового SILENT.
Код приложения не менялся. До повторного device-прогона host-проверка прошла
на реальной исходной записи API 33 и синтетических quiet-записях: одинаковый
record/слишком ранний when не считаются вторым периодом, нехватка SUCCESS и семь
испорченных вариантов уведомления отвергаются. Результат:
`.test-tools/runs/20261006-api33-format-validation/validation.txt`.
Прежний fixture API 36 также повторно прошёл; эти проверки не заменяют доставку ОС.

Вторая попытка `.test-tools/runs/20261006-131019-release-smoke/summary.json`
завершилась **FAILED** после семи фаз: reboot 7 → 8 восстановил тот же alarm,
BootReceiver зарегистрирован, но health-check обнаружил ANR Google Search/Launcher.
В `reboot-1-logcat-after.log` 10:13:12.283 UTC — WindowManager ANR окна
NexusLauncherActivity; 10:13:14.381 — ActivityManager ANR
`com.google.android.googlequicksearchbox:search`, input dispatch/focus timeout 5008 мс.
Это сбой системного окружения, не обнаруженная ANR InTime; сырые FAILED/exit 1
сохранены. Периодическое ожидание ещё не начиналось. Исходный профиль — 2 CPU,
2048 МБ RAM, software GPU. Его эмулятор остановлен после проверки владельца
и отсутствия production-пакета; для следующей попытки выбран host GPU/4096 МБ.
Изменение ресурсов само по себе не считается доказанным устранением ANR.

## Два естественных quiet periodic периода в release/R8 на API 35 — 6 октября 2026

**PASSED, 11/11 host-фаз**, exit 0:
`.test-tools/runs/20261006-110045-release-smoke/summary.json`.
Свежий UI bootstrap — **3/3**, без ошибок и пропусков:
`.test-tools/runs/20261006-105754-2306e139/summary.json`.
Существующий AOSP API 35 (`Intime_Test_API35`, emulator-5580), fingerprint
`Android/sdk_phone64_x86_64/emu64x:15/AE3A.240806.019/12368160:userdebug/test-keys`.
Использован прежний signed non-debuggable code 25/2.0.0-rc1 APK с иконками,
SHA-256 4F5911443EF3D9A18645C2285E1D1BD2F7B26566F51E7448D2A6D39C40B9D056;
mapping 7B5DEBF5DCCE8160A40A2F0B0442B722CA9DA511984806B7981587E4DC2E2E23.
Приложение не пересобиралось; PeriodicOnly теперь допускает API 35/36,
в этом прогоне использован `-TestApi 35 -PeriodicRepeats 2`.

CRUD/JSON, reboot 3 → 4 и первый минутный alarm с ACK прошли. После Home/am kill
процесс отсутствовал, stopped=false подтверждён. Первый alarm
`when=1791273858277`; первый quiet repeat `1791275132431` пришёл через
**1274.154 с (21 минута 14 секунд)**, следующий `1791276707488` — ещё через
**1575.057 с (26 минут 15 секунд)**. Оба уведомления имеют то же уникальное
описание, LOW (-1), без actions/ACK, sound/vibrate=null, defaults=0, SILENT
и groupAlertBehavior=1; снимки сохранены отдельно. Будущий alarm отсутствует.

В logcat две записи SUCCESS настоящего TaskNotificationWorker, один work id
`1353bec6-0c3e-4c88-b63f-5a9aa1c403b1`, новый PID 2938:
08:25:32.439 и 08:51:47.493 UTC (11:25 и 11:51 по Москве).
`periodicWorkerSuccessCount=2`, `naturalDispatchValidated=true`.
Между повторами Activity/probe/instrumentation не запускались, JobScheduler
не читался; ручного dispatch/doWork и изменения batching/периода/часов не было.
Uptime вырос с 130.72 до 2994.57 с, в согласии с host 11:04:23 → 11:52:07.
Контроль разрывов опроса не сработал; latest-снимки показывают питание от сети,
заряд 100%, ACTIVE для deep/light idle. Это обычный активный AVD, не Doze/OEM.

Тестовая production-установка, probe и уникальный JSON удалены runner;
отсутствие production-пакета дополнительно подтверждено. PID/StartTime/AVD
проверены, выделенный эмулятор остановлен, маркер удалён. Release-отчёт — 17.0 МиБ.
Новые SDK/system images не скачивались; пользовательские main*.db не читались.
Host-проверка подсчёта повторов повторно прошла до device-прогона.
Локальная suite не повторялась: изменены допуск API в host-сценарии и документация,
код приложения не менялся. Точный срок 15 минут, физический звук, сопоставление
work id с private Room, другие API и обычный Doze/OEM этой проверкой не покрыты.

## Два естественных quiet periodic периода в release/R8 — 6 октября 2026

**PASSED, 11/11 host-фаз**, exit 0:
`.test-tools/runs/20261006-083041-release-smoke/summary.json`.
Свежий UI bootstrap — **3/3**, без ошибок и пропусков:
`.test-tools/runs/20261006-082806-016a9e9f/summary.json`.
Использован прежний signed non-debuggable code 25/2.0.0-rc1 APK с иконками,
SHA-256 4F5911443EF3D9A18645C2285E1D1BD2F7B26566F51E7448D2A6D39C40B9D056;
mapping 7B5DEBF5DCCE8160A40A2F0B0442B722CA9DA511984806B7981587E4DC2E2E23.
Приложение не пересобиралось; новый runner запущен с `-PeriodicRepeats 2`.

CRUD/JSON, reboot 36 → 37 и первый минутный alarm с ACK прошли.
После Home/am kill процесс отсутствовал, stopped=false подтверждён.
Первый alarm `when=1791264837869`; первый quiet repeat `1791266138325`
пришёл через **1300.456 с (21 минута 40 секунд)**, следующий `1791267713459`
— ещё через **1575.134 с (26 минут 15 секунд)**. Оба активных уведомления
сохранены в `periodic-notifications-repeat-1.log` и `-2.log`; каждое имеет
то же уникальное описание, LOW (-1), без actions/ACK, sound/vibrate=null,
defaults=0, SILENT и groupAlertBehavior=1. Будущий alarm отсутствует.

В logcat две записи SUCCESS настоящего TaskNotificationWorker, один work id
`14fb8f2e-4e12-4821-9baf-7a950e5ccc00`, новый PID 3118:
05:55:38.341 и 06:21:53.472 UTC (08:55 и 09:21 по Москве).
`periodicWorkerSuccessCount=2`, `naturalDispatchValidated=true`.
Между повторами Activity/probe/instrumentation не запускались; JobScheduler
не читался, ручного dispatch/doWork и изменения batching/периода/часов не было.
Uptime вырос с 132.28 до 3028.57 с; host 08:34:05 → 09:22:21 согласуется
с непрерывным временем guest. Контроль разрывов опроса не сработал.

Таким образом, на существующем AOSP API 36 проверены два последовательных
естественных тихих периода после завершения исходного процесса. Это не гарантия
доставки ровно через 15 минут. Физический звук, сопоставление work id с приватными
данными Room, обычный Doze, OEM и другие API не проверялись. R5.3 остаётся
открытым по оставшейся матрице.
Host-проверка нового сценария также отклоняет повторное чтение того же record,
изменение when на 1 мс и два уведомления при только одной записи SUCCESS;
она использует синтетические снимки и не заменяет описанный device-прогон.
Runner удалил свою production-установку, probe и уникальный JSON; отсутствие
production-пакета дополнительно подтверждено. PID/StartTime/AVD проверены,
выделенный эмулятор остановлен, маркер владельца удалён. Release-отчёт — 33.7 МиБ;
новые SDK/system images не скачивались, пользовательские main*.db не читались.

## Естественный quiet periodic worker в release/R8 — 6 октября 2026

**PASSED, 10/10 host-фаз**, exit 0:
`.test-tools/runs/20261006-002602-release-smoke/summary.json`.
Использован тот же подписанный non-debuggable code 25/2.0.0-rc1 APK с иконками,
SHA-256 4F5911443EF3D9A18645C2285E1D1BD2F7B26566F51E7448D2A6D39C40B9D056;
mapping 7B5DEBF5DCCE8160A40A2F0B0442B722CA9DA511984806B7981587E4DC2E2E23.
Приложение не пересобиралось. Свежий UI bootstrap этого этапа — 3/3,
`.test-tools/runs/20261005-234316-c07e7559/summary.json`.

CRUD/JSON, reboot 34 → 35 и первое минутное уведомление с ACK прошли.
После Home/am kill процесс отсутствовал, stopped=false подтверждён.
Далее Activity/probe/instrumentation не запускались; период/batching/часы
не менялись, ручного jobscheduler run и прямого doWork не было.
periodicLiveJobDumps=false: полная очередь читалась только до ожидания
(собственное задание Ready:false с будущим TIMING_DELAY) и после обнаружения
нового уведомления. Между ними — notifications/power/deviceidle/battery/uptime.
Контроль host-разрывов не сработал; guest uptime вырос с 118.96 до 1445.31 с,
в согласии с временными отметками host 00:29:18 → 00:51:25 (Москва).

Первый Notification.when=1791235751256, quiet repeat=1791237070337:
**1319.081 секунды, 21 минута 59 секунд**. Новый процесс PID 3142 выполнил
настоящий WorkManager: SUCCESS для TaskNotificationWorker,
id 97e37ab6-59a8-4c44-9cc9-6237be7d67ae. В logcat время UTC 21:51:10
5 октября соответствует 00:51:10 6 октября по Москве.
Повтор имеет то же уникальное описание, LOW (-1), без actions/ACK,
Notification sound/vibrate=null, defaults=0, SILENT и groupAlertBehavior=1.
Будущий alarm для единственной просрочки отсутствует. Физический звук,
private UUID/wasNotified и другие private поля этим сценарием не измеряются.

Сравнение с прерванным диагностическим прогоном и установленный байткод
подтверждают необходимость убрать stateful dumpsys из интервала ожидания.
Свежая естественная доставка на этом API 36 подтверждена; точный срок 15 минут,
следующий период после данного повтора, обычный Doze/OEM и остальные API — нет.
Неуспешный reboot и прерванное наблюдение сохранены отдельно ниже.
Java/Room/scheduling/R8 приложения не менялись; unit suite не повторялась.

Runner удалил свои APK/probe/JSON; host проверил отсутствие production-пакета,
PID/StartTime/имя AVD и остановил его. Маркер удалён. Новых образов/AVD нет.
Архив успешного прогона около 27.2 МиБ; три release-архива этого этапа
после удаления временных JAR/DEX занимают около 69.3 МиБ логических файлов.
Следующий локальный шаг R5.3 — следующий естественный periodic-период после
первого тихого повтора; матрица ОС/Play upgrade остаются открытыми.

## Stateful-диагностика JobScheduler и подготовка reboot — 5–6 октября 2026

Свежий UI bootstrap **3/3**:
`.test-tools/runs/20261005-234316-c07e7559/summary.json`.
Первый повтор PeriodicOnly остановился до ожидания worker: **FAILED**, семь
CRUD/JSON-фаз прошли, reboot 32 → 33 не восстановил alarm.
`.test-tools/runs/20261005-234542-release-smoke/summary.json`.
Пакет до reboot stopped=false, после stopped=true, actualAlarm=0;
вызов собственного BootReceiver не зарегистрирован. Это отдельный неуспешный
результат подготовки/восстановления, не скрытый последующим успехом.
Сходный случай ранее наблюдался на API 31; причина перехода stopped не доказана.

Справка API 36 подтвердила wait-for-handler/wait-for-background-handler.
Runner теперь ждёт обе очереди PackageManager перед reboot, по 30 секунд
максимум; никаких принудительных set-stopped/receiver broadcast нет.
Следующий запуск прошёл reboot и первое уведомление (девять фаз), но выявил
влияние собственной диагностики на состояние планирования:
`.test-tools/runs/20261005-235317-release-smoke/summary.json`,
**ABORTED_STATEFUL_DIAGNOSTICS**. Уникальный host-процесс PID 34888 остановлен
после проверки команды и времени создания; runner finally не выполнялся.
Host отдельно проверил PID/StartTime/имя AVD и SHA-256 установленного APK,
сохранил логи и удалил только свою установку/probe/уникальный synthetic JSON.
Summary составлен host после остановки, а не выдан завершившимся runner.

В снимке 00:12:37 одновременно Unsatisfied TIMING_DELAY и Ready:true;
в следующем снимке history фиксирует удовлетворение TIMING_DELAY именно
в момент предыдущего dumpsys. Снимок сохранён как periodic-jobs-timing-transition.log.
Для проверки скопирован публичный `/system/framework/services.jar` установленного
API 36, SHA-256 D82D6412C5843131F6992F53021DBCFBD368695BF5FD440EDFB2C8A2046A0B5A.
SDK dexdump на извлечённом DEX подтвердил цепочку: dumpInternal печатает Ready
через isReadyToBeExecutedLocked; при неготовом job вызывается
evaluateControllerStatesLocked; TimeController может отметить TIMING_DELAY
выполненным и удалить job из mTrackedJobs. В этой timing-ветке нет немедленного
сигнала планировщику. Таким образом текстовый dumpsys не является чисто пассивной
диагностикой установленной системы. Само влияние на поля подтверждено;
полную причинность прежних задержек без сравнения прогонов не утверждаем.
Дизассемблированные классы сохранены в classes2.dex.job-methods.log (около 1 МиБ).
Временные JAR/три DEX (около 42 МиБ суммарно) удалены после сверки.

Механизм также виден в [AOSP JobSchedulerService](https://android.googlesource.com/platform/frameworks/base/+/749650c2cb0c1ab68221ec86f86027d5bd48d634/apex/jobscheduler/service/java/com/android/server/job/JobSchedulerService.java)
и [TimeController](https://android.googlesource.com/platform/frameworks/base/+/main/apex/jobscheduler/service/java/com/android/server/job/controllers/TimeController.java).
Сверка установленного байткода важнее предположения о точном исходном commit.

Промежуточный jobscheduler dumpsys удалён из runner: latest содержит только
power/deviceidle/battery/uptime/fingerprint/time; очередь — before/after,
вне интервала наблюдения. Host-проверка новых вызовов прошла: 17 чтений,
только два scheduler dump, без команд изменения настроек или запуска работы.
`.test-tools/runs/20261006-passive-periodic-validation/validation.txt`.
Код Java/Room/WorkManager/R8 APK не менялся и не пересобирался.

## Диагностический periodic-прогон: разрыв окружения — 5 октября 2026

Свежий UI bootstrap **3/3**, failures/errors/skipped=0:
`.test-tools/runs/20261005-085250-06fc8e73/summary.json`.
Release/R8 PeriodicOnly использовал прежний APK с SHA-256
4F5911443EF3D9A18645C2285E1D1BD2F7B26566F51E7448D2A6D39C40B9D056;
**9 подготовительных фаз прошли**, включая reboot 30 → 31, доставку первого
уведомления, Home/am kill и stopped=false. Ручного job run, изменения batching,
периода или часов не было. Новых образов/AVD/загрузок нет.

**Итог INFRASTRUCTURE_INTERRUPTED, не natural PASS и не обычный timeout:**
`.test-tools/runs/20261005-085517-release-smoke/summary.json`.
Снимки host: before 08:58:26, latest 09:03:28, after 23:38:29 (Москва).
Между latest/after прошло 52500.925 секунды — более 14 часов;
guest elapsed clock за это время вырос лишь с 427131 до 453563 мс,
то есть на 26.432 секунды. Между before/after guest прошёл 328.234 секунды.
Факт разрыва подтверждён, конкретная причина (сон компьютера/приостановка VM/
другая задержка окружения) не устанавливалась. Непрерывных 65 минут не было.
В after собственное задание всё ещё Ready:false, Unsatisfied TIMING_DELAY,
до earliest оставалось 6 минут 30 секунд. Отсутствие повтора ожидаемо при
таком состоянии и не воспроизводит вчерашнее удержание Ready:true.

Исходный summary runner со статусом INCONCLUSIVE сохранён отдельно в
summary-runner-before-interruption-assessment.json; новый статус выставлен
после разбора временных отметок и guest elapsed. Справка shell/get-job-state
сохранена: первоначально waiting; get-pending-job-reasons команды в справке нет.
Команда help напечатала справку с ненулевым кодом, это не падение приложения.
Сборка Android: Android/sdk_phone64_x86_64/emu64x:16/BE2A.250530.026.D1/13818094:userdebug/test-keys.

Runner теперь обнаруживает разрыв между опросами свыше 180 секунд либо ход
часов назад, сохраняет финальную диагностику и выставляет
INFRASTRUCTURE_INTERRUPTED, naturalDispatchValidated=false. Проверяется также
последняя пауза при выходе из while, чтобы скачок времени не стал timeout.
Host-проверка: обычные 30/180 секунд допускаются, реальные временные отметки
этого прогона и перевод часов назад обнаружены; parser прошёл.
Результат poll-continuity-validation.txt находится в каталоге прогона.
Это проверка host-логики; после исправления длительный device-прогон не повторялся.

Собственные APK/probe/JSON удалены, отсутствие production-пакета проверено.
AVD остановлен после проверки PID/StartTime/имени, маркер удалён.
Логи release около 23.4 МиБ; данных пользователя main*.db не касались.
Следующий шаг — повторить наблюдение на существующем API 36 при непрерывно
работающем компьютере. Автоматически отключать сон/менять питание не стали.
Причина вчерашнего ожидания Ready:true и R5.3 остаются открытыми.

## Диагностика очереди по сохранённым логам — 5 октября 2026

Нового device-прогона не было. Разобран снимок
`.test-tools/runs/20261004-223947-release-smoke/periodic-default-before-forced.log`:
собственное задание namespace androidx.work.systemjobscheduler, uid 10152, id 1
имеет Standby bucket EXEMPTED, Ready:true, пустые Unsatisfied constraints,
Restricted due to:none. BATTERY_NOT_LOW, TIMING_DELAY, DEVICE_NOT_DOZING,
BACKGROUND_NOT_RESTRICTED и WITHIN_QUOTA выполнены. Очередь Pending — None,
Active jobs — N/A, 16 idle contexts; screen ON, thermal status 0,
Stable power/Not low — true. TIMING_DELAY был выполнен примерно за 39 минут
до снимка. Это исключает перечисленные ограничения как видимую причину
в данном снимке, но не описывает все переходы состояния между снимками.

Предположение о задержке из-за CPU batch threshold 3 не подтверждено:
batch_active_bucket_jobs=false, а задание EXEMPTED. В просмотренном
[исходнике AOSP JobSchedulerService](https://android.googlesource.com/platform/frameworks/base/+/4ce707990cdcf0ea0af5a19159ab968db8bede80/apex/jobscheduler/service/java/com/android/server/job/JobSchedulerService.java)
ветка force batching исключает EXEMPTED и при выключенном флаге использует
MIN_READY_NON_ACTIVE_JOBS_COUNT. Исходник служит ориентиром; соответствие
этому commit установленного системного модуля пока не установлено.
Причина удержания задания остаётся неизвестной; ошибку приложения/R8 не объявляем.

PeriodicOnly теперь сохраняет dumpsys jobscheduler/power/deviceidle/battery
после am kill, каждые пять минут ожидания и перед оценкой результата,
включая естественный тайм-аут; также сохраняет build fingerprint и время снимков.
Наборы before/latest/after имеют фиксированные имена, latest перезаписывается;
сбор не меняет настройки и не запускает jobs. Полный logcat после ожидания
и прежний controlled/natural статус сохраняются как раньше.
Проверены синтаксис PowerShell и вызовы новой функции с подставным adb:
15 операций чтения, 12 фиксированных файлов dumpsys, 3 временные отметки.
Результат: `.test-tools/runs/20261005-periodic-diagnostic-validation/validation.txt`.
Это host-проверка, а не новый PASS устройства; приложение не пересобиралось.

Следующий шаг — один прогон PeriodicOnly на существующем API 36 с этой
диагностикой, без изменения batching и ручного job run; перед ним проверить
доступное место. При повторной задержке сопоставить системную сборку,
снимки и logcat, отдельно выяснить доступность причин ожидания JobScheduler.

## Тихий periodic worker в release/R8 — 4 октября 2026

**PASSED_CONTROLLED_PLATFORM_DISPATCH, 10/10 host-фаз:**
`.test-tools/runs/20261004-223947-release-smoke/summary.json`.
Свежий UI bootstrap **3/3**, без failures/errors/skipped:
`.test-tools/runs/20261004-223643-7f4f62e5/summary.json`.
Использован прежний проверенный APK с иконками, SHA-256
`4F5911443EF3D9A18645C2285E1D1BD2F7B26566F51E7448D2A6D39C40B9D056`,
production code 25/2.0.0-rc1, non-debuggable, Upload key, R8 mapping того же архива.
Приложение не пересобиралось; runtime Java/Room/scheduling не менялся.

Новый PeriodicOnly (только API 36) прошёл семь CRUD/JSON-фаз, настоящий reboot
28 → 29, первое минутное уведомление с ACK, затем Home/am kill фонового процесса,
отсутствие PID и stopped=false. Задание SystemJobService/TaskNotificationWorker
сохранилось. До повтора Activity/шторка/probe/instrumentation не запускались,
private DB/preferences не читались. Batching оставался прежним: CPU threshold 3,
max_cpu_only_job_batch_delay_ms=1860000; период/часы не менялись.

**Естественная доставка не подтверждена:** до контрольного снимка прошло
3124.374 секунды от первого уведомления (52 минуты 4 секунды), повтора не было.
Задание оставалось Ready:true, без невыполненных ограничений, не active/pending.
Это наблюдение очереди Android, не доказательство ошибки R8 и не завершённый
тайм-аут 65 минут. Снимки periodic-jobs-observation.log и
periodic-default-before-forced.log сохранены.

Host проверил PID/StartTime/AVD name и единственное собственное задание и выполнил
`cmd jobscheduler run -s -n androidx.work.systemjobscheduler com.vpe_soft.intime.intime 1`.
Флаг -s требует выполненных ограничений. Это контролируемый dispatch через ОС,
не прямой doWork и не естественное решение очереди. Маркер/вывод команды —
periodic-dispatch-control.json и periodic-controlled-run.log. Новый процесс PID 3271
выполнил настоящий WorkManager, `Worker result SUCCESS`, id
c7e1db59-2507-4957-aa53-11974bab32b2.

Первый Notification.when=1791142993523, повтор=1791146144995: **3151.472 секунды
(52 минуты 31 секунда)**. То же уникальное описание, LOW priority (-1), без actions/ACK,
sound/vibrate=null и defaults=0 в Notification, SILENT и groupAlertBehavior=1.
Будущий alarm для единственной просрочки отсутствовал. mSound в dumpsys сохраняет
звук канала; его null не требуется, физический звук не измерялся.
Проверено исполнение release/R8 после смерти процесса, не обычные сроки доставки,
следующий период worker, Doze/OEM, UUID до/после, wasNotified или все private поля.

Raw summary сохранён в summary-runner-before-dispatch-annotation.json; его PASSED
**не является natural pass**. Авторитетный summary.json имеет controlled status,
naturalDispatchValidated=false, periodicPlatformJobDispatched=true и команду.
Runner теперь учитывает такой маркер/Running job в итоговом статусе; классификатор
проверен на реальных файлах прогона и случае без маркера, синтаксис/BOM проверены.
Полный runner после добавления классификатора не повторялся. Будущий natural timeout
обозначается INCONCLUSIVE, отдельно от assertions/ошибок приложения и очистки.

В итоговой диагностике нет ANR/FATAL EXCEPTION; свои APK/probe/JSON удалены,
собственный AVD остановлен, маркер отсутствует. Архив около 34.6 МиБ логических
файлов; снимок опроса перезаписывался. Новых образов/AVD/зависимостей нет.
Unit suite и новый production APK/AAB не запускались/не собирались.
R5.3 целиком открыт: естественная доставка и оставшаяся матрица/Play не подтверждены.
Далее — диагностика ожидания JobScheduler на том же окружении; не менять приложение
или увеличивать время ожидания без новых оснований.

## Launcher/adaptive icons и свежий release — 4 октября 2026

R5.2: манифест переведён на `@mipmap/ic_launcher`/`ic_launcher_round`, без DEV-метки.
Сохранён знак календаря и серо-светлая палитра. Добавлены VectorDrawable fallback
для API 24–25, adaptive background/foreground для API 26+ и monochrome для API 33+.
Новые XML занимают 4264 байта; исходники/описание — ICON_DESIGN_GUIDE.md.
Notification icon и runtime Java/Room/scheduling не менялись.

**Свежая подписанная APK/AAB сборка прошла** с R8/lintVitalRelease.
Архив: `.test-tools/runs/release-icons-20261004-221247/summary.json`.
Основа `a75cf97a3d200d07b756786d44e96ba0c5eedf0f` + незакоммиченные ресурсы;
снимок изменений сохранён в tracked-source.patch и icon-sources/.
APK SHA-256 `4F5911443EF3D9A18645C2285E1D1BD2F7B26566F51E7448D2A6D39C40B9D056`.
AAB SHA-256 `57C3AB5BB0ADA419F68EE146ECFA2A3C641C83C8DB5AB102E98980B78E7C35BA`.
Подписи проверены apksigner/jarsigner, сертификат обоих — подтверждённый Upload key.
Bundletool validate/dump manifest прошли; package/25/2.0.0-rc1, minSdk 24,
target/compileSdk 36, debuggable отсутствует; launcher ссылки и все варианты
packaged resources проверены. Embedded mapping совпал с отдельным mapping.txt,
SHA-256 `7B5DEBF5DCCE8160A40A2F0B0442B722CA9DA511984806B7981587E4DC2E2E23`.

**UI-класс PASSED, 3/3**, без failures/errors/skipped:
`.test-tools/runs/20261004-221441-ea6531d2/summary.json`.
**Свежий release smoke PASSED, 14/14**:
`.test-tools/runs/20261004-221833-release-smoke/summary.json`.
CRUD/ACK, JSON через SAF, перезапуск, boot 26 → 27, реальные минутные уведомления
и ACK из шторки, отказ/восстановление POST_NOTIFICATIONS и канала прошли на
существующем AOSP Intime_Test_API36. Это не новый Doze или release worker тест.

После smoke тот же APK отдельно установлен при отсутствии production-пакета,
снят/осмотрен launcher-api36.png: InTime! с календарём без DEV-метки.
Тематический режим launcher и другие маски/OEM не проверены визуально;
monochrome проверен только как упакованный ресурс. Fallback API 24–25 на устройстве
ещё не проверен, API 24 остаётся отложенным из-за ограничения диска.
Первый диагностический launcher-before-install.png не содержит InTime,
поскольку test APK уже был удалён; он не используется как подтверждение иконки.
Отдельная собственная установка удалена, host PID/StartTime/AVD проверены,
эмулятор остановлен, маркер отсутствует. В сохранённом финальном logcat
нет ANR/FATAL EXCEPTION/Fatal signal. Новых образов/AVD/зависимостей не скачивали.
Архив ресурсов/APK/AAB/mapping около 15.6 МиБ, smoke-отчёт около 38.5 МиБ
логических файлов. Unit suite не запускалась: изменены только launcher-ресурсы.
Релиз не опубликован; Play upgrade/полная матрица остаются открытыми.
Следующий локальный шаг — проверка тихого periodic worker в release/R8 (R5.3).

## Сверка релизной документации R5.4 — 4 октября 2026

README, PLAN, TECH_NOTES, RELEASE, CHANGELOG, PRIVACY_POLICY и черновик
google_play/PLAY_CONSOLE_DECLARATIONS сверены с исходниками, зависимостями,
манифестом свежего AAB и ранее сохранёнными результатами. Исправлены устаревший
targetSdk 35, production/signing-команды, статусы API-матрицы и отметки Done,
не подтверждавшие финальный релиз/иконки/Play-обновление. Changelog теперь Unreleased.

Существенное уточнение privacy: allowBackup=true, собственных backup exclusions
нет; Android может создавать/переносить системные копии, а SAF использует выбранный
пользователем provider, включая облачный. Поэтому обещание «данные никогда
не покидают устройство» удалено. Само приложение не имеет INTERNET в свежем AAB
и не выполняет собственных сетевых загрузок. В декларации отражены дополнительные
WorkManager-разрешения из merged manifest и локальное boot-уведомление.
Ссылки на официальные определения Data safety/Android backup добавлены в документы.

Публичную privacy-страницу web-инструмент прочитать не смог: это ограничение проверки,
не доказательство недоступности сайта. Публикация текста/сверка публичной страницы
и заполненных форм Console остаются открытыми; R5.4 целиком не закрыт.
Код, манифест и артефакты не менялись; сборка и unit/device-тесты не нужны
для этого документационного шага и не запускались. Локальные Markdown-ссылки
и git diff --check проверены. Новых зависимостей/SDK-образов/AVD нет.
Следующий локальный шаг — финальные иконки (R5.2), затем свежая release-проверка.

## Актуальный подписанный production AAB — 4 октября 2026

**SIGNED_AAB_VALIDATED**, исходный коммит `a0c055fd6db948120924602ad3feb55ff4c7b45e`.
Архив: `.test-tools/runs/production-aab-20261004-214440/summary.json`.
Подписанный `intime-25-2.0.0-rc1-upload-signed.aab` — 3449067 байт,
SHA-256 `ABD45D9E2B922B6242E62E6A8682D4025D4175D01E55AACB9856173554D2D1ED`.

Свежая offline-сборка `:app:bundleRelease` завершилась успешно с
`minifyReleaseWithR8`, `lintVitalRelease` и `signReleaseBundle`.
Первый вызов остановился до компиляции из-за передачи версии без кавычек
в PowerShell; исправленный вызов прошёл. Оба журнала сохранены.
`jarsigner -verify` подтвердил подпись; `keytool -printcert -jarfile`
подтвердил Upload key SHA-256
`97B60110A8E894791B4770826A8D214DC5E3A551CD24BD41D646C29F64A1DD26`.
Предупреждения jarsigner о самоподписанном сертификате/цепочке доверия,
отсутствии timestamp и неподписываемых POSIX-атрибутах сохранены в журнале.

Кэшированный bundletool 1.17.2 выполнил `validate` и `dump manifest`
с кодами 0, без скачивания зависимостей. Проверен манифест внутри самого AAB:
`com.vpe_soft.intime.intime`, versionCode 25, versionName 2.0.0-rc1,
minSdk 24, targetSdk/compileSdk 36; debuggable отсутствует (по умолчанию false).
В пакете есть `base/dex/classes.dex`, файлов `.so` нет. Это инвентаризация,
не отдельный запуск в окружении со страницами 16 КБ.
Встроенный `BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map`
совпадает по SHA-256 с сохранённым mapping.txt:
`7B5DEBF5DCCE8160A40A2F0B0442B722CA9DA511984806B7981587E4DC2E2E23`.
В mapping `TaskRepository` переименован в `i1.l`.

Runtime-исходники/конфигурация сборки не отличаются от baseline `7fbae39`
ранее проверенного release APK; BuildConfig содержит текущий Git hash.
Unit/device-тесты на этом шаге не запускались, новый APK не устанавливался.
Весь архив AAB/mapping/отчётов занимает около 12 МиБ логических файлов;
новые SDK-образы/AVD не создавались. AAB не загружался в Play.
Финальная версия/использованные Play коды и обновление установленной v1
через APK с App signing key ещё не проверены; R5.3 целиком остаётся открытым.
Следующий доступный шаг — сверить релизную документацию и декларации с
фактическим поведением (R5.4), сохраняя открытые проверки матрицы/Play.

## Регистрация расписания MainActivity — 4 октября 2026

Новый `ScreenPlatformTest#mainStartup_keepsOnePeriodicReminderAcrossRecreationAndReopening`
**PASSED, 1/1**: `.test-tools/runs/20261004-213659-9ec40a96/summary.json`.
Полный UI-класс **PASSED, 3/3**, 0 failures/errors/skipped:
`.test-tools/runs/20261004-213941-eb0b8a0e/summary.json`.
Использован прежний AOSP Intime_Test_API36 / emulator-5584, 2 ГБ/host GPU/8 CPU.
Новые SDK-образы/AVD не создавались, runtime приложения не менялся.
Unit suite и production APK/AAB на этом шаге не пересобирались/не запускались.

Начальная фикстура — in-memory Room с будущей задачей, POST_NOTIFICATIONS
выдано на API 33+. Прежняя unique work тестового приложения отменяется для изоляции.
Тест открывает настоящий MainActivityV2: запрос ставит его onCreate, а не тест.
Через реальный WorkManager проверены одна активная work ENQUEUED с будущим сроком,
PeriodicityInfo.repeatIntervalMillis=15 минут, BatteryNotLow=true,
NetworkType.NOT_REQUIRED. После ActivityScenario.recreate и закрытия/нового запуска
ActivityScenario UUID и nextScheduleTimeMillis не изменились, активная работа
осталась единственной. Завершённые CANCELLED исторические записи отфильтрованы.
В finally созданный UUID отменён. Подробный русский Javadoc описывает условия,
действия, assertions и границы теста.

Ранее существующие insets/Back тесты также прошли в этом UI-классе. В сохранённом
device-logcat нет ANR/FATAL EXCEPTION/Fatal signal. Эмулятор остановлен после
проверки PID/StartTime/AVD name, маркер владельца удалён. Два каталога отчётов
занимают около 4 МиБ логических файлов; временная копия WorkManager classes.jar,
использованная для сверки API библиотеки, удалена.

Проверена регистрация и KEEP в пределах одного процесса. Доставка notification,
15-минутное ожидание, смерть процесса, Doze, release/R8 и новый тест на остальных
API не покрыты. Предыдущие проверки сохраняют свои отдельные границы.
Следующий шаг — актуальный подписанный production AAB и проверка его метаданных,
без новых образов/AVD; R4.3/R5.3 целиком остаются открытыми.

## Второй период после завершения процесса — 4 октября 2026

**PASSED при описанном ниже условии, 2/2 host-фазы:**
`.test-tools/runs/20261004-210140-periodic-smoke/summary.json`.
UI bootstrap **2/2**: `.test-tools/runs/20261004-205954-76b1ffce/summary.json`.
Локальная регрессия **97/97**: `.test-tools/runs/20261004-212825-488b7ad6/summary.json`.
Тот же существующий AOSP Intime_Test_API36, без новых образов/AVD.
Runtime приложения/production APK/AAB не менялись.

Добавлены PeriodicRecoveryTest (две фазовые @Test с русскими Javadoc) и
scripts/test-periodic-smoke.ps1. Host отказал бы при уже установленных .dev/.dev.test,
установил собственные APK, открыл список/Home. Prepare в пустой файловой Room
создал уже уведомлённую просроченную задачу и настоящий PeriodicWorkRequest:
15 минут/BatteryNotLow/initial delay 10 секунд. Штатная periodic work отменена
для изоляции. Первая тихая отправка/следующий срок проверены; UUID, ID/поля задачи
и firstPost сохранены для второй фазы.

Первая отправка `1791136919666`, вторая notification when `1791138356556`:
интервал около **23 минут 57 секунд**. Вторая фаза сверила системный postTime
не раньше firstPost+15 минут, тихое уведомление без ACK, неизменные
lastAck/nextAlarm/wasNotified, обновлённый LAST_POST и будущий WorkInfo.
Фикстура/UUID удалены, прежний LAST_POST возвращён; собственные APK удалены host.

После prepare host выполнил am kill и подтвердил отсутствие PID/stopped=false.
Логи показывают, что Android завершил первый процесс PID 2475 ещё при окончании
instrumentation; тест не приписывает его смерть исключительно am kill.
До второго уведомления Activity/instrumentation не открывались. В pre-verification
logcat виден новый PID 2697 и запуск SystemJobService для того же UUID
`49b738c0-15f0-45a6-8e42-05f800a0a4f3`; обновление уведомления произошло до verify.

**Условие, ограничивающее результат:** с исходными настройками второй доставки
не было примерно 24 минуты. Job сохранился, наступил его срок и был `Ready: true`.
У ОС min_ready_cpu_only_jobs_count=3 и max_cpu_only_job_batch_delay_ms=1860000
(31 минута). Затем отдельной контрольной командой временно задан threshold=1,
после чего готовая работа выполнилась. Это согласуется с пакетной задержкой ОС;
точный срок при default batching не проверен. Часы/интервал не менялись,
JobScheduler run и doWork вручную не вызывались.

Override до изменения был null, после снятия снова null; эффективный порог 3
подтверждён в jobs-after-config-restoration.log. Summary явно содержит
batchThresholdTemporarilyRelaxed=true и batchConfigOverrideRestored=true.
Успех нельзя интерпретировать как default-batching проверку или точную доставку
каждые 15 минут. Для дальнейших прогонов окно runner увеличено с 25 до 50 минут
(16–60 разрешено) и добавлен опциональный ReduceBatching с возвратом override
в finally. Полный повтор нового флага с самого начала ещё не выполнялся;
его команды изменения/возврата проверены отдельным контролем в этом прогоне.

Неуспешная подготовка сохранена:
- `20261004-205723-6b5d3c1b`: INFRASTRUCTURE_ERROR/0 tests, неоднозначная
  assertEquals(long, Long); исправлена явная longValue, затем UI bootstrap прошёл.
- `20261004-210120-periodic-smoke`: отказ до установки APK, pm path вернул exit 1
  для отсутствующего пакета; проверка заменена на точный pm list packages.

В финальном logcat нет ANR/FATAL EXCEPTION. Эмулятор остановлен после проверки
PID/StartTime/AVD name и восстановления JobScheduler; маркер владельца удалён.
Пять каталогов отчётов занимают около 16 МиБ логических файлов (не общая дельта
Gradle/AVD). Host main*.db пользователя не использовались.
Default batching, регистрация работы MainActivity, shell force-stop, reboot,
Doze, release/R8 и OEM-квоты остаются отдельными проверками.

## Настоящий периодический WorkManager — 4 октября 2026

Новый debug integration
`NotificationDeliveryTest#realPeriodicWorker_postsQuietReminderAndSchedulesNextRun`
**PASSED, 1/1**: `.test-tools/runs/20261004-204057-8bf694bb/summary.json`.
Повтор в полном классе: `.test-tools/runs/20261004-204231-840fbc11/summary.json`,
отчёт **5 тестов, 0 failures/errors/skipped**. Локальная регрессия **97/97**:
`.test-tools/runs/20261004-204508-76be3615/summary.json`.
Использован существующий Intime_Test_API36 (AOSP, 2 ГБ/host GPU/8 CPU),
без нового SDK-образа/AVD. Runtime приложения и production APK/AAB не менялись.

В синтетической in-memory Room создана уже уведомлённая просроченная задача,
LAST_POST задан 16 минутами ранее. Штатная periodic work тестового приложения
отменена для изоляции. Настоящий WorkManager получил PeriodicWorkRequest
с интервалом 15 минут, BatteryNotLow и initial delay 10 секунд; doWork вручную
и TestWorkerBuilder не вызывались. Проверены регистрация UUID в Android
JobScheduler, системное уведомление с описанием, отсутствие ACK/sound/vibrate
и звуковых defaults, неизменные lastAck/nextAlarm/wasNotified, обновление LAST_POST.
После первого исполнения WorkInfo вернулся в ENQUEUED с nextScheduleTimeMillis
не менее чем через 14 минут от начала проверки. В finally отменён созданный UUID,
прежний LAST_POST восстановлен.

Первый запуск `20261004-203734-415bb5c7` — FAILED, 1 тест/1 failure:
новый тест искал jobs в default namespace. На API 34+ WorkManager использует
`androidx.work.systemjobscheduler`; выбор namespace исправлен, затем выполнены
два успешных прогона. Это ошибка тестовой проверки, приложение не менялось.

Полный класс включает условную фазу deniedRuntimePermission с precondition
по Assume; этот прогон не заменяет отдельно контролируемый отзыв разрешения.
В device-logcat нет ANR/FATAL EXCEPTION/Fatal signal. Дополнительная диагностика
сохранена в `device-logcat.log` и `jobscheduler-after.log`. Эмулятор остановлен
после проверки PID/StartTime/AVD name. Четыре каталога отчётов занимают около
4,71 МиБ логических файлов (без оценки общего изменения Gradle/AVD).

Покрыт первый запуск настоящей периодической работы и её перепланирование.
Второй естественный запуск через 15 минут, конкретный исполняющий scheduler
(SystemJobScheduler/GreedyScheduler), регистрация работы MainActivity,
сохранение расписания после смерти процесса, release/R8 worker, Doze и слышимость
на телефоне пока не проверены. Следующий шаг — второй реальный периодический
запуск и сохранение расписания после смерти процесса на существующем AVD.

## Release Doze на API 36 — 4 октября 2026

**PASSED, 11/11 фаз:** `.test-tools/runs/20261004-194701-release-smoke/summary.json`.
UI bootstrap **2/2**: `.test-tools/runs/20261004-193810-32dc4da6/summary.json`.
Использованы существующий Intime_Test_API36 / emulator-5584 (AOSP rev 2,
2048 МБ/host GPU/8 CPU) и прежний production R8 APK SHA-256
`00F8198794F6DC307B52D0FEE5C533DC60B69CC6AAC9A9EB7E827FF88772165D`.
Runtime не менялся; новые SDK-образы/AVD не устанавливались. Unit suite и
production APK/AAB на этом шаге не пересобирались/не запускались.

Режим `-DozeOnly`: семь CRUD/JSON фаз, reboot, доставка в deep idle,
проверка уведомления в шторке после выхода из сна и ACK/process restart.
Boot count `21 → 22`, alarm `1791136059614` восстановился до открытия Activity.
Минутный alarm `1791132624470` сработал при deep state IDLE. Системная диагностика
после доставки: `mForceIdle=true`, `mScreenOn=false`, `mCharging=false`,
`mState=IDLE`; лог receiver, активный notification record собственного пакета
с уникальным описанием и исчезновение alarm проверены без открытия UI.
После выхода из сна ACK из шторки создал alarm `1791132690521`, который
сохранился после force-stop/запуска приложения.

На этом AOSP Doze изначально выключен: runner временно включает только deep idle,
затем возвращает исходное состояние. Дополнительная проверка после прогона:
`doze-state-restored.log` — `mForceIdle=false`, `mDeepEnabled=false`;
`doze-battery-restored.log` — battery simulation снята. Собственные APK/probe/JSON
очищены без ошибок; эмулятор остановлен после проверки PID/StartTime/AVD name.
В итоговых журналах нет ANR/FATAL EXCEPTION.

Неуспешные попытки сохранены отдельно и не считаются pass:
- `20261004-194052-release-smoke`: восемь подготовительных фаз прошли,
  `force-idle deep` отказал: `Unable to go deep idle; not enabled`.
  Это ограничение окружения до проверки доставки; cleanup завершился без ошибок.
- `20261004-194444-release-smoke`: две фазы прошли, потерялся tap перехода
  из списка к деталям после restart. Добавлен Open-ReleaseDetails с подтверждением
  btnAckTask и ограниченным повтором tap только при видимой исходной строке;
  сами действия ACK не повторяются. Приложение не менялось.

Четыре каталога текущей проверки (bootstrap и три smoke) занимают суммарно
около 44 МиБ логических файлов; это размер отчётов, не измерение общего изменения
занятого места с учётом Gradle/AVD. Новый образ не скачивался.
Проверена одна доставка exact-and-allow-while-idle в принудительном Doze.
Естественный вход в Doze, квоты повторной доставки, inexact fallback, периодический
WorkManager, физический телефон/OEM и другие API не проверены. Фазы отказа
разрешения/канала этим режимом пропущены. R4.3/R5.3 целиком остаются открытыми.

## Подготовка API 24 — 4 октября 2026

Runner принимает API 24: выделенные имя `Intime_Test_API24`, порт 5588,
образ `system-images;android-24;default;x86_64`, 2 CPU/2048 МБ/software.
Release runner допускает на API 24 только RebootOnly (CRUD/JSON/reboot);
полный notification smoke отклоняется до установки APK, поскольку API 24
не имеет каналов и требует адаптации старого экрана уведомлений.

ScreenPlatformTest теперь допускает API 24+: AndroidX WindowInsetsCompat,
WindowMetrics на API 30+, Display.getRealSize на API 24–29. Русский комментарий
обновлён. Runtime приложения не менялся.

Свежие проверки подготовки:
- **97/97 local tests PASSED:** `.test-tools/runs/20261004-184704-fd33df83/summary.json`.
- `:app:compileDebugAndroidTestJavaWithJavac` — BUILD SUCCESSFUL.
- **2/2 UI tests PASSED на API 31:** `.test-tools/runs/20261004-184920-8bb87248/summary.json`.
- PowerShell parser, UTF-8 BOM и `git diff --check` проверены.

API 31 остановлен runner. API 24 image/AVD не установлен/не создан и тесты на нём
не запускались: запрос разрешения на расширение списка проектных AVD AGENTS.md
ещё ожидает ответа пользователя. Ветка границ окна API 24–29 и API 24 release
сценарий пока не проверены на устройстве. Успех API 31 не заменяет эту проверку.

Уточнение после ответа: разрешение ограничено дополнительным расходом ≤100 МБ.
Локальный образ API 24 и проектный AVD отсутствуют. В официальном каталоге
Google `https://dl.google.com/android/repository/sys-img/android/sys-img2-3.xml`
для `system-images;android-24;default;x86_64` архив rev 8
`x86_64-24_r08.zip` имеет размер 419261998 байт (около 400 МиБ).
Прочитана только метаинформация в память; образ не скачивался, AVD не создавался.
Лимит не позволяет выполнить запланированную установку; API 24 проверка отложена.

## Повторные reboot на API 31 — 4 октября 2026

**PASSED, 3/3 reboot и 7/7 подготовительных фаз:**
`.test-tools/runs/20261004-154528-release-smoke/summary.json`.
Свежий UI bootstrap **2/2**:
`.test-tools/runs/20261004-154245-4fb8ac49/summary.json`.
Прежний production R8 APK SHA-256
`00F8198794F6DC307B52D0FEE5C533DC60B69CC6AAC9A9EB7E827FF88772165D`,
тот же проектный API 31 Google APIs/2048 МБ/software. Runtime не менялся;
APK/AAB и локальные unit tests на этом шаге не пересобирались/не запускались.

Новый режим `-RebootOnly -RebootRepeats 3 -RebootSettleSeconds 0` проходит
CRUD/ACK/JSON, затем три настоящих reboot без прежней 15-секундной паузы.
Перед повторными reboot снова выполняется force-stop/запуск и проверяется задача;
после reboot Activity не запускается до восстановления alarm.
Boot count: `7 → 8 → 9 → 10`; alarm во всех трёх случаях `1791121563327`.
В каждом post-boot logcat есть `BootReceiver: onReceive` и обработка boot;
состояние пакета до/после сохранено в `reboot-N-package-*.log`, broadcast history
и logcat — в отдельных файлах каждого повтора. ANR/FATAL EXCEPTION не обнаружены,
cleanup errors отсутствуют. Скрипт удалил собственный production APK и probe;
проектный эмулятор остановлен после проверки владельца.

Прежний единичный сбой не воспроизвёлся в этой серии. Три успешных повтора
не доказывают отсутствие редкого сбоя и не подтверждают гипотезу о записи stopped:
изменений приложения для устранения причины нет. Вопрос остаётся открытым;
при повторении runner теперь сохраняет сравнимую диагностику. Эти десять фаз
не являются полным 14-фазным smoke: уведомления/разрешения здесь не проверялись.
Следующий этап матрицы — подготовка проектного API 24; R4.3/R5.3 остаются открытыми.

## Release R8 на API 31 — 4 октября 2026

**PASSED, 14/14 фаз:** `.test-tools/runs/20261004-142752-release-smoke/summary.json`.
UI bootstrap ScreenPlatformTest **2/2**:
`.test-tools/runs/20261004-135317-d3f2f7dd/summary.json`.
Первый bootstrap `20261004-134815-32062307` дал нулевой результат и
INFRASTRUCTURE_ERROR: класс был ограничен API 33+. SDK-фильтр расширен до 31,
выдача POST_NOTIFICATIONS выполняется только на API 33+, русский комментарий обновлён.
Нулевой результат не считается pass.

Проектный Intime_Test_API31 / emulator-5586: Google APIs x86_64 rev 12,
Emulator 37.2.12, 2 CPU, 2048 МБ RAM, software GPU, 1080×1920/density 420.
Использован прежний upload-signed production APK 25/2.0.0-rc1 из
`.test-tools/runs/release-permissions-20261003-225632`; runtime не менялся.
APK SHA-256 `00F8198794F6DC307B52D0FEE5C533DC60B69CC6AAC9A9EB7E827FF88772165D`,
mapping SHA-256 `7B5DEBF5DCCE8160A40A2F0B0442B722CA9DA511984806B7981587E4DC2E2E23`.
Проверены production package/version/upload certificate, non-debuggable и R8 renaming.
APK/AAB и unit suite на этом шаге не пересобирались/не запускались.

Прошли CRUD/ACK, настройки/Back, process restart, JSON через SAF, реальный reboot,
доставка/ACK уведомления, общий запрет/восстановление и отключение/включение канала.
На API 31 runtime POST_NOTIFICATIONS отсутствует: общий доступ переключается
в APP_NOTIFICATION_SETTINGS собственной установки. Dumpsys до снятия запрета
содержит `importance=NONE`, после — строку того же пакета без importance (default).
Переключатель unchecked/checked также проверен. Summary указывает
`notificationBlockMode=settings-switch`, фазы `release-app-notifications-denied/restored`.
Имена части вспомогательных файлов/полей (`permission-denied`, `permission-restored`)
сохранены для совместимости и не означают проверку runtime permission на API 31.

Boot count `5` → `6`, alarm `1791116917004` восстановился до открытия Activity.
В package-логах до/после reboot `stopped=false`. Доставка: `1791113543096`
→ ACK `1791113606499`; общий запрет — `1791113622469`, после снятия
`1791113698650` → ACK `1791113761145`; канал отключён — `1791113772282`,
после включения `1791113850506` → ACK `1791113912610`.
В полных сохранённых журналах до/после reboot нет ANR, FATAL EXCEPTION,
Fatal signal или lowmemorykiller для InTime.

Исправления harness подтверждены итоговым прогоном: имя экспорта очищается через
MOVE_END/DEL и сверяется до Save (Ctrl+A на API 31 вводил лишнюю букву);
перед Activity сворачивается шторка; все проверки доставки/отсутствия требуют
свежий XML с notification_stack_scroller, раскрытие повторяется до 20 секунд.
Главный переключатель приложения проверяется отдельно от канала; в парсере dumpsys
учтено отсутствие default importance. Комментарии описывают эти условия и ограничения.

Неуспешные прогоны сохранены отдельно, каталоги — `.test-tools/runs`:

| Прогон | Завершённые фазы | Причина |
|---|---:|---|
| 20261004-135502-release-smoke | 4 | Экспорт получил лишнюю `a` в имени, ожидаемый путь не найден; собственный ошибочный JSON удалён отдельно |
| 20261004-135701-release-smoke | 10 | appops POST_NOTIFICATION ignore не воспроизвёл общий запрет: уведомление появилось; заменён Settings |
| 20261004-140352-release-smoke | 0 | После предыдущего failure раскрытая шторка перекрыла MainActivity |
| 20261004-140524-release-smoke | 11 | Общий запрет сработал; при включении parser ошибочно требовал явно заданную default importance |
| 20261004-141222-release-smoke | 7 | После reboot прежний alarm не восстановился, вызов BootReceiver приложения в журнале не найден |
| 20261004-141941-release-smoke | 8 | Receiver записал showNotification, но UI-проба читала рабочий стол после потерянного раскрытия шторки |

Причина отсутствия вызова BootReceiver в `141222` **не установлена**.
Добавлены package-логи до/после reboot, при повторном отсутствии alarm — broadcasts;
на API 31 перед reboot выдерживаются 15 секунд после операций с процессом.
Гонка отложенной фиксации состояния PackageManager — гипотеза, не доказательство.
Успешный итоговый прогон не доказывает устранение этой нестабильности: нужна
точечная проверка reboot API 31, затем API 24. R4.3/R5.3 целиком открыты.

Пределы: exact был доступен изначально на API 31, запрет exact/inexact fallback
этим прогоном не проверяются. Чистая синтетическая установка, не Play upgrade;
wasNotified/ID Room не сверяются, автоматическая доставка старой просрочки без ACK,
Doze, тихий worker и boot-уведомления не покрыты. Шторка опрашивается с паузами.
Новая редакция runner/UI-тестов на API 33/35/36 в этом шаге не повторялась.

Отсутствие своего release-пакета, probe, итогового уникального JSON и ошибочно
названного JSON первого прогона проверено отдельно. Проектный AVD остановлен
после сверки PID/start/имени. Личные устройства и main*.db не использовались.
PowerShell syntax/BOM и git diff --check прошли.

## Release R8 на API 33 — 4 октября 2026

**PASSED, 14/14 фаз:** `.test-tools/runs/20261004-110211-release-smoke/summary.json`.
Bootstrap ScreenPlatformTest: **2/2**, `.test-tools/runs/20261004-104827-5718ea78/summary.json`.
Проектный Intime_Test_API33 / emulator-5582: Google APIs x86_64 API 33,
Emulator 37.2.12, 2 CPU, 2048 МБ RAM, software GPU, 1080×1920/density 420.
Runtime/build приложения не изменились: использован тот же upload-signed production
25/2.0.0-rc1 APK из `.test-tools/runs/release-permissions-20261003-225632`.
APK SHA-256 `00F8198794F6DC307B52D0FEE5C533DC60B69CC6AAC9A9EB7E827FF88772165D`,
mapping SHA-256 `7B5DEBF5DCCE8160A40A2F0B0442B722CA9DA511984806B7981587E4DC2E2E23`;
non-debuggable, production package/version/upload certificate и R8 renaming проверены.
Новая сборка APK/AAB и unit suite на этом шаге не запускались.

Прошли CRUD/ACK, настройки/Back, process restart, JSON через системный SAF picker,
реальная перезагрузка, доставка/ACK уведомления, отказ POST_NOTIFICATIONS,
отключение канала и восстановление после каждого запрета через новый UI ACK.
Boot count `4` → `5`; alarm `1791104583686` восстановился до открытия Activity.
Уведомление: срок `1791101217837` → ACK `1791101280914`;
после возврата разрешения `1791101365186` → ACK `1791101428820`;
после включения канала `1791101521687` → ACK `1791101584541`.
В полных сохранённых журналах до/после reboot нет ANR, FATAL EXCEPTION,
Fatal signal или lowmemorykiller для InTime.

Runner расширен до API 33/35/36 с закреплёнными проектными serial и проверками
PID/start/имени/API. На API 33 обнаружены и исправлены ошибки UI-сценария;
фокус, введённый текст и открытие Edit теперь подтверждаются свежим XML.
Закрытие IME выполняет Back лишь при `mInputShown=true`/`mIsInputViewShown=true`,
чтобы скрытая клавиатура не приводила к выходу из формы. Потерянный tap по Edit
повторяется ограниченно, только пока в текущем XML видна кнопка деталей.
Изменения снабжены русскими комментариями об условиях и пределах проверки.

Неуспешные прогоны сохранены и не считаются pass:

- `20261004-105149-release-smoke`: 0 завершённых фаз, Back после ввода вернул
  пустой список вместо закрытия уже скрытой IME; spinner не найден.
- `20261004-105438-release-smoke`: 0 завершённых фаз, поле осталось без фокуса
  и текста после потерянного tap; созданная строка не найдена.
- `20261004-105623-release-smoke`: 8 успешных фаз, включая reboot; переход
  из деталей в Edit не завершился, spinner не найден. После исправления
  весь сценарий повторён успешно в итоговом каталоге выше.

Пределы: чистая синтетическая установка, а не Play upgrade/реальные данные;
wasNotified/ID Room не сверяются, восстановление старой просрочки без ACK и Doze
не проверяются. Снимки шторки опрашиваются с паузами. Google Play store/16 КБ
этим Google APIs образом не проверяются. API 35/36 новой редакцией harness
на этом шаге не повторялись; прежние результаты относятся к прежней редакции.
Следующий шаг — проектный API 31 с адаптацией запрета уведомлений без runtime
POST_NOTIFICATIONS, затем API 24. Полная R4.3/R5.3 остаётся открытой.

Отсутствие собственного release-пакета, probe и уникального JSON на AVD проверено
отдельно. Эмулятор остановлен после сверки PID/start/имени; личные AVD/телефон
и main*.db не использовались. PowerShell syntax/BOM и git diff --check прошли.

## Release R8 на API 35 — 4 октября 2026

Runner `scripts/test-release-smoke.ps1` принимает `-TestApi 35/36`;
выбор ограничен проектными AVD/serial, сверяются PID/start/имя/версия ОС.
Новый APK не собирался: runtime и build-конфигурация после `7fbae39` не изменились.
Использован кандидат 25/2.0.0-rc1 из
`.test-tools/runs/release-permissions-20261003-225632` с теми же APK/mapping SHA-256,
что приведены ниже для API 36. Это чистая синтетическая установка upload-signed
non-debuggable APK с проверенным R8 mapping, а не обновление Play v1.

**PASSED, 14/14 фаз:** `.test-tools/runs/20261004-103427-release-smoke/summary.json`.
CRUD/ACK, настройки/Back, process restart, JSON через SAF, настоящий reboot,
доставка/ACK уведомления, отказ POST_NOTIFICATIONS, отключённый канал и восстановление
после каждого запрета с новым UI ACK прошли. Boot count `1` → `2`, alarm
`1791102895832` восстановлен до запуска Activity. В сохранённых журналах до/после
reboot нет ANR, FATAL EXCEPTION, Fatal signal или lowmemorykiller для InTime.
Ограничения прежние: UI опрашивается с паузами, wasNotified/ID приватной Room
не сверяются, автоматическое восстановление старой просрочки без ACK/Doze не проверяется.

Проверенная конфигурация: Emulator 37.2.12, AOSP API 35 default x86_64 rev 2,
2 CPU, 4096 МБ RAM, 1080×1920/density 420, host GPU.
Bootstrap ScreenPlatformTest: **2/2**, `.test-tools/runs/20261004-103144-4280d465/summary.json`.
После закрепления defaults повтор с короткой командой `test.ps1 -Mode device
-TestApi 35 -Filter com.vpe_soft.intime.intime.activity.ScreenPlatformTest -Offline
-KeepEmulator` на том же AVD: **2/2**, `.test-tools/runs/20261004-104310-84eff499/summary.json`.
В test.ps1 добавлен `-EmulatorRamMb`, фактическая RAM сохраняется в summary/ownership,
смена явного значения у работающего экземпляра отклоняется. API 33/36 defaults RAM
сохранены; текущий шаг повторно их не проверял. Новые сценарии/параметры снабжены
русскими комментариями. Runtime приложения не менялся.

Предшествующие сбои сохранены отдельно:

- Google Play API 35 16 КБ, 2048 МБ/software: bootstrap **2/2**,
  `.test-tools/runs/20261004-101647-7300c086/summary.json`, затем release **FAILED**
  после двух фаз, `.test-tools/runs/20261004-102104-release-smoke/summary.json`.
  Probe не получил свежий XML; logcat прямо фиксирует `lowmemorykiller` для InTime
  PID 8450 с причиной `min watermark is breached even after kill` и массовые убийства
  системных/Google-процессов. Проверка свежести XML не ослаблялась.
- Тот же Play 16 КБ образ с 4096 МБ/host: bootstrap **FAILED**, один записанный failure,
  `.test-tools/runs/20261004-102458-0869d1db/summary.json`.
  XML сообщает `INSTRUMENTATION_ABORTED: System has crashed`, logcat — SIGSEGV
  в system_server и Google-процессах, DeadSystemException и ANR com.android.phone.
  Это не успешно завершённый UI-тест; причина native сбоя не установлена.

Прежний проектный Play 16 КБ профиль сохранён без удаления данных в
`.test-tools/avd/Intime_Test_API35-ps16k-20261004.avd`, INI — рядом с суффиксом `.ini.backup`.
Создан AOSP-профиль с прежним проектным именем; личные AVD/телефон не использовались.
Проверка 16 КБ и Google services остаётся открытой. AAB не пересобран;
полная API-матрица/Play upgrade не закрыты. Следующий шаг — release smoke API 33.
После итогового прогона отсутствие собственного production-пакета, probe и уникального
JSON на AVD проверено отдельно. Эмулятор остановлен после сверки PID/start/имени;
личные main*.db не читались и не изменялись. PowerShell syntax/BOM и git diff --check прошли.

## Release R8: разрешение уведомлений и канал — 3 октября 2026

После исправлений lint и слияния master `7fbae39` собран свежий production APK
25/2.0.0-rc1 командой `:app:assembleRelease -PproductionRelease=true
-PreleaseVersionCode=25 -PreleaseVersionName=2.0.0-rc1 --offline --no-daemon
--no-watch-fs --no-configuration-cache`. R8 и lintVitalRelease прошли.
Каталог сборки: `.test-tools/runs/release-permissions-20261003-225632`;
`build.log`, `intime-25-2.0.0-rc1-r8.apk` и `mapping.txt` сохранены вместе.

- APK SHA-256: `00F8198794F6DC307B52D0FEE5C533DC60B69CC6AAC9A9EB7E827FF88772165D`.
- Mapping SHA-256: `7B5DEBF5DCCE8160A40A2F0B0442B722CA9DA511984806B7981587E4DC2E2E23`.
- Smoke проверил production-пакет, версию, non-debuggable и upload-сертификат.
  Это чистая синтетическая установка, а не обновление Google Play v1.
- Bootstrap ScreenPlatformTest на проектном API 36: **2/2**,
  `.test-tools/runs/20261003-230001-5195661d/summary.json`.
- Итоговый release smoke: **PASSED, 14/14 фаз**,
  `.test-tools/runs/20261003-231028-release-smoke/summary.json`.

К прежним десяти фазам (CRUD/ACK, настройки/Back, process restart, JSON через SAF,
reboot, реальная доставка и ACK из шторки) добавлены четыре сценария с русскими
комментариями об условиях, действиях, проверках и ограничениях:

| Сценарий | Наблюдаемый результат |
|---|---|
| POST_NOTIFICATIONS отозван, диалог заблокирован тестовыми permission flags | `granted=false` подтверждён dumpsys; минутный alarm сработал, receiver записал попытку доставки, уведомление отсутствовало до пяти секунд после срока |
| Разрешение возвращено | Новый UI ACK существующей задачи запустил минутный отсчёт; уведомление доставилось, ACK из шторки перенёс срок |
| DefaultChannelId отключён через Settings | Проверены переключатель и `mImportance=0` только собственного канала; alarm сработал, receiver записал попытку доставки, уведомление отсутствовало |
| Канал включён через Settings | Проверены переключатель и положительная importance; после нового UI ACK уведомление доставилось, ACK из шторки перенёс срок |

Сроки в итоговом summary (epoch millis): запрещённое разрешение — `1791058481505`,
его восстановление — `1791058555265` → ACK `1791058619161`;
отключённый канал — `1791058629140`, его восстановление — `1791058705123`
→ ACK `1791058768401`. Reboot увеличил boot count `18` → `19`; прежний alarm
`1791061857942` восстановился до открытия UI.
Доказательства сохранены в `permission-denied-package.log`,
`permission-denied-logcat.log`, `channel-disabled-logcat.log`,
`channel-false-settings.log`, `channel-true-settings.log` и соответствующих
`*-notifications.log`. В журналах до/после reboot нет ANR/FATAL EXCEPTION.

Предыдущий прогон `.test-tools/runs/20261003-230318-release-smoke/summary.json`
завершился **FAILED после 12 успешных фаз**: сценарий канала ожидал неправильный
resource-id переключателя. По свежему XML исправлен XPath на
`android:id/switch_widget`; затем весь сценарий повторён успешно. Это ошибка
тестового сценария до переключения канала, а не установленный дефект приложения.

Очистка проверена отдельно: собственный production-пакет, probe и уникальный
экспортный JSON на AVD удалены. Проектный Intime_Test_API36 остановлен после
сверки PID/start time/имени. Личный телефон и main*.db не использовались.

Границы: wasNotified/ID в приватной Room отдельно не проверяются; восстановление
доставки проверено с новым ACK, а не для старой просроченной задачи без действия
пользователя. Не покрыты Doze, отдельный общий запрет приложения, полный worker/reboot
overdue сценарий и остальные API. AAB после последних изменений не пересобран;
прежний AAB и прежние smoke-результаты относятся к историческим сборкам.
R4.2, полная R4.3 и R5.3 остаются открытыми. Следующий шаг — release smoke на API 35.

## Слияние локальной master — 3 октября 2026

Выбранная пользователем локальная master `a09fa7e` объединена с chatgpt.
Конфликты разрешены с сохранением Room v2/JSON-контракта, защиты production,
исправлений lint и действующего GitLab CI. Старый DatabaseUtil не восстановлен.
Gradle Wrapper 8.11.1/AGP 8.9.1/JDK 17 и зависимости v2 сохранены вместо
непроверенного перехода на Gradle 9/JDK 25 из master.
Добавлены GitHub workflow и composite SDK setup, задача unit tests исправлена
на `:app:testDebugUnitTest`, JDK/build-tools согласованы с текущей сборкой.
README адаптирован к схеме 6, миллисекундам и текущему формату JSON.
Приняты стабильная настройка configuration-cache, build cache и отключение
welcome banner. Runtime-код и layout после разрешения равны исходной chatgpt.

Проверены `lintDebug + assembleDebug` (успех, 0 lint errors/24 warnings),
97 локальных тестов без failures/errors:
`.test-tools/runs/20261003-224355-e0665dd6/summary.json`.
Логи сборки — в каталоге, указанном `.test-tools/merge-master-run.txt`.
Все три YAML CI-файла разобраны SafeConstructor SnakeYAML 2.4 с запретом
дублирующихся ключей; это синтаксическая проверка, не удалённый запуск workflow.
`git diff --check` и проверка отсутствия конфликтных маркеров прошли.
Device/release smoke не повторялся: runtime/layout слиянием не изменены.

## Исправление GitLab lint — 3 октября 2026

Проверен публичный pipeline
[2909444152](https://gitlab.com/valker/intime/-/pipelines/2909444152):
assembleDebug (16914113930) success, lintDebug (16914113929) failed;
debugTests и instrumentedTests skipped. Лог упавшего job сохранён в
`.test-tools/gitlab-job-16914113929.log`, список jobs —
`.test-tools/pipeline-2909444152-jobs.json`. Это отказ lint до тестовой стадии,
не падение юнит-тестов. Исправление неинтерактивной установки tzdata уже применено.

Локально `-Pci :app:lintDebug` воспроизвёл ровно 9 errors/43 warnings:
три UseAppTint и шесть MissingTranslation. Исправлены app:tint на трёх экранах,
добавлены русские переводы, вынесены тексты формы/деталей и формат Task ID в ресурсы;
макетные заглушки списка перенесены в tools:text. Поле описания получило inputType
и отключение autofill; декоративный разделитель исключён из accessibility.
API 26 notification settings защищены проверкой версии с fallback в настройки
приложения на API 24–25. Статус уведомлений теперь использует
NotificationManagerCompat.areNotificationsEnabled вместо проверки отсутствующего
до API 33 runtime permission и учитывает общий системный запрет.

`lintDebug + assembleDebug` прошли: **0 errors/24 warnings**.
Остались 21 UnusedResources, два NotifyDataSetChanged и один IconMissingDensityFolder;
они не блокируют job. Проверки lint не отключались, baseline/suppressions не добавлены.
Логи до/после и HTML/XML отчёты — `.test-tools/runs/ci-lint-20261003-195140`.
Полный local: 97 тестов, failures/errors = 0,
`.test-tools/runs/20261003-195822-e966db2c/summary.json`.
После изменений ScreenPlatformTest на проектном API 36: 2/2 без ошибок,
`.test-tools/runs/20261003-200013-1f445ef2/summary.json` (границы элементов
четырёх экранов и возврат из настроек). Проектный AVD остановлен после сверки
PID/start/имени; `git diff --check` прошёл.
CI теперь сохраняет полные lint artifacts при failed job (`when: always`).
Нужен новый удалённый pipeline на исправленном коммите; R4.4 остаётся открытым.
API 24–25 на устройстве в этом шаге не проверялись. Прежний production R8 APK
собран до этих UI/API изменений; его исторический smoke не проверяет новый код.

## Release R8: уведомление, ACK и reboot — 3 октября 2026

`scripts/test-release-smoke.ps1` расширен до десяти фаз; первый полный успешный
прогон — `.test-tools/runs/20261003-193908-release-smoke/summary.json`.
Повтор окончательного сценария —
`.test-tools/runs/20261003-194244-release-smoke/summary.json`, PASSED, 10/10 фаз:
boot_count 14 → 15, восстановленный alarm 1791049387558, уведомление по сроку
1791045933442, новый alarm после ACK 1791045997548. Полные журналы до/после reboot
без ANR/FATAL EXCEPTION. После повтора отдельно проверено отсутствие production
пакета и собственного JSON/probe; проектный AVD остановлен со сверкой PID/start/имени.
PowerShell parser, UTF-8 BOM и `git diff --check` прошли.
Использован прежний production APK 25/2.0.0-rc1 (non-debuggable, upload key,
SHA-256 `4D0B7E8AD4B5F35FC493090B629CA7A15E5224CE13658FB8CA15EAEA488733ED`)
и только проектный AOSP API 36 / `emulator-5584`. Runtime-код не менялся.
Bootstrap debug UI: 2/2, `.test-tools/runs/20261003-193136-852adf8a/summary.json`.

- `release-reboot-recovery`: после JSON round-trip задача имеет будущий часовой
  срок; Home, настоящий adb reboot, boot_count 13 → 14. Release BootReceiver
  восстановил тот же alarm 1791049174951 **до открытия Activity**. Это inexact
  fallback без разрешения exact; просрочка во время выключения не моделируется.
- `release-notification-delivery`: только свежей тестовой установке разрешён
  SCHEDULE_EXACT_ALARM через appops. UI меняет период на минуту и выполняет ACK
  для нового отсчёта; Home, ожидание настоящего AlarmManager без изменения времени
  или ручного broadcast. Срок — 1791045712888; в системной шторке появилось
  уникальное описание. `notifications-delivered.log` содержит production
  NotificationRecord, это описание и action Acknowledge/PendingIntent broadcast.
- `release-notification-ack`: нажата action в шторке; уведомление исчезло,
  следующий alarm — 1791045776249, позднее сработавшего. Описание и этот срок
  сохранены после force-stop/нового запуска. Прямой вызов AckReceiver не используется.

Новые фазы имеют подробные русские комментарии. Сохраняются полный logcat до
reboot и после, UI XML, dumpsys alarm и notifications. В первом успешном прогоне
ANR/FATAL EXCEPTION не найдены. Отказ в разрешениях, отключённый канал, Doze,
тихий повтор WorkManager, boot-уведомление о пропущенных задачах и другие API
этим release smoke не покрыты; прежние debug-тесты не заменяют этих проверок.
Обновление Play v1 и реальные данные также остаются открытыми.

Неуспешные диагностические прогоны сохранены: `20261003-193407-release-smoke`
прошёл восемь фаз, но после смены периода минутный срок от старого ACK был уже
в прошлом; фикстура исправлена новым ACK через UI. `20261003-193639-release-smoke`
остановился после двух фаз на открытии деталей; добавлено ожидание оконной
анимации до получения координат tap (probe не ждёт idle из-за часов UI).
Эти прогоны не считаются успешными; подтверждённого crash приложения в них нет.

## Release R8: JSON через системный picker — 3 октября 2026

Расширенный `scripts/test-release-smoke.ps1` прошёл семь фаз на том же
non-debuggable production APK 25/2.0.0-rc1 с upload key, AOSP API 36.
Артефакт не пересобирался: SHA-256 APK и mapping совпадают с предыдущим отчётом.
Успешный прогон: `.test-tools/runs/20261003-192554-release-smoke/summary.json`.
Повтор: `.test-tools/runs/20261003-192710-release-smoke/summary.json`,
PASSED, 7/7 фаз; срок после ACK/импорта — 1791048450538.
Перед ним debug bootstrap ScreenPlatformTest: 2/2,
`.test-tools/runs/20261003-192152-77297397/summary.json`.

Две новые фазы снабжены подробными русскими комментариями:

- `json-export`: после создания/edit/ACK пользовательский UI открывает SAF
  CreateDocument, записывает уникальный JSON в Downloads. Проверяются
  `meta.version = 1`, точный порядок columns, одна строка из восьми значений,
  описание и next_alarm, совпадающий с действующим AlarmManager.
  Локальный файл сохранён в отчёте; SHA-256 первого успешного экспорта —
  `5CB3BC4AC847997B9558CC75F6C4A677A5B3FA19C30E705826876CAD31A62CA4`.
- `json-import-and-restart`: после удаления и пустого списка открывается
  SAF OpenDocument, выбирается собственный экспорт. Описание и прежний срок
  alarm восстанавливаются, затем сохраняются после force-stop/нового запуска.
  В первом успешном прогоне срок после ACK и после импорта — 1791048373487.

Таким образом проверены реальные ContentResolver, release JSON parser и файловая
Room через UI без внутренних тестовых вызовов. После импорта не сравниваются
все восемь полей повторным экспортом; сохранность ID/quant/остальных дат,
неверный JSON, откат, пустой импорт и отмена picker отдельно здесь не проверены.
Будильник использует inexact fallback. Доставка уведомлений, reboot в release,
Play-обновление и реальные данные остаются открытыми.
Отдельный первый прогон `20261003-192434-release-smoke` остановился на неверном
resource-id системной Save: четыре прежние фазы прошли, новый экспорт не завершён.
Идентификатор исправлен по свежему XML на `android:id/button1`; это ошибка harness.
В успешном журнале ANR/FATAL EXCEPTION нет; собственная установка, уникальный
экспорт на устройстве и временные файлы probe удаляются runner.
После повтора отдельно подтверждено отсутствие production-пакета и собственного
JSON на устройстве; проектный AVD остановлен после сверки PID/start/имени.
PowerShell parser, UTF-8 BOM и `git diff --check` прошли; runtime-код не менялся.

## Release APK с R8 — 3 октября 2026

Свежий подписанный production APK 25/2.0.0-rc1 прошёл два прогона по пять
UI smoke-фаз на проектном AOSP API 36, `Intime_Test_API36` / `emulator-5584`.
Это чистая установка с upload key, не обновление APK из Google Play.
APK non-debuggable, minSdk 24, targetSdk 36; сборка выполнила R8 и lintVitalRelease,
сохранённый mapping подтверждает переименование TaskRepository.

- Сборка: `.test-tools/runs/release-r8-20261003-184243/build.log`.
  APK: `intime-25-2.0.0-rc1-r8.apk`, SHA-256
  `4D0B7E8AD4B5F35FC493090B629CA7A15E5224CE13658FB8CA15EAEA488733ED`.
  Соседний `mapping.txt`, SHA-256
  `792ED488768F611173584029EA1A545E4962591F1CCE3284D943389C3D0C90A8`.
  AAB в этом прогоне не пересобирался; прежний SDK 36 AAB описан ниже.
- Первый успешный smoke: `20261003-190419-release-smoke/summary.json`.
  Повтор окончательной версии скрипта: `20261003-190623-release-smoke/summary.json`.
  Оба находятся в `.test-tools/runs`, статус PASSED, пять фаз без ошибок.
- Проверены создание задачи и активный AlarmManager; редактирование и сохранение
  после force-stop/нового запуска; ACK из деталей и перенос срока; версия в настройках
  и системный Back; удаление, пустой список после перезапуска и отсутствие alarm.
  В повторе срок непосредственно перед ACK — 1791047199814, после — 1791047205215.
- Полные журналы `logcat.log` и `window.log` сохранены: ANR и FATAL EXCEPTION не найдены.
  Exact alarm permission не выдавалось: приложение использовало inexact fallback.
  POST_NOTIFICATIONS выдано до запуска. Реальная доставка, ACK из уведомления,
  reboot, JSON импорт/экспорт, историческое обновление и реальные данные этой
  проверкой не покрыты. Матрица R4.3 и R5.3 целиком остаётся открытой.

Добавлен `scripts/test-release-smoke.ps1` с подробными русскими комментариями.
Он проверяет принадлежность AVD, подпись/пакет/код/non-debuggable APK и mapping,
отказывается заменять существующий production-пакет, сохраняет отчёты и удаляет
только свою установку и временные файлы probe. Отдельный shell UIAutomator probe
`scripts/android/ReleaseHierarchyProbe.java` не добавляется в APK приложения;
его служебные JUnit3 вызовы не считаются бизнес-фазами smoke.

При разработке runner были отдельные неуспешные диагностические прогоны:
`184649`, `184731`, `184822`, `185035`, `185610`, `185719`, `185946`, `190134`
(полные имена `20261003-<время>-release-smoke` в `.test-tools/runs`). Исправлены
обработка отсутствующего пакета и успешного stderr PowerShell 5.1, сокращённое имя
production receiver, свежесть XML, framework classpath и переход между окнами.
Стандартный `uiautomator dump` при обновлении часов UI выдавал ошибку idle с exit 0,
оставляя старый XML. Probe удаляет старый файл, ограниченно ждёт свежую иерархию
без idle timeout и требует собственный маркер успеха вместе с результатом JUnit.
Эти отказы harness не являются подтверждёнными дефектами приложения.

Перед release был отдельный debug bootstrap: 2/2 ScreenPlatformTest,
`.test-tools/runs/20261003-184022-8faa20dc/summary.json`; это не результат R8.
Команды и требования повторения — в TESTING.md.
После итогового прогона отдельно проверено отсутствие production-пакета и трёх
временных файлов probe. Проектный эмулятор остановлен после сверки PID, времени
старта и имени AVD; его данные сохранены. PowerShell parser, UTF-8 BOM скрипта
и `git diff --check` проверены. Код приложения на этом шаге не менялся.

## Диагностика окружения API 36 — 3 октября 2026, продолжение

Рабочая на этом хосте конфигурация: AOSP revision 2, Emulator 37.2.12,
8 виртуальных ядер, 2048 МБ RAM, 720×1280/density 280, host GPU (Intel UHD 620),
без загрузочной анимации ОС. Размер интерфейса в dp сохранён относительно
1080×1920/density 420. Это проверенный набор параметров, не доказательство
единственной причины прежних ANR и не гарантия для других хостов.

- Холодный UI-прогон: 2/2 прошли,
  `.test-tools/runs/20261003-182314-8d4e0c78/summary.json`.
- Четыре notification/reboot-фазы прошли; boot_count 7 → 8,
  restored deadline 1791041738964;
  `.test-tools/runs/20261003-182520-notification-smoke/summary.json`.
  После reboot сохранены полный platform-logcat.log и platform-window.log;
  `ANR in` в журнале не найден, системный ANR-диалог не перекрывал UI.
- Полный набор после reboot: 25 записей JUnit, 19 выполненных без ошибок,
  6 пропусков (две reboot-фазы, три upgrade-фазы и отказ в разрешении,
  отдельно выполненный smoke). Оба UI-теста прошли;
  `.test-tools/runs/20261003-182701-bba9c525/summary.json`.
  После набора вновь сохранён журнал без `ANR in`.
- Отрицательная проверка нового параметра GPU: запрос software при работающем
  host экземпляре отклонён до тестов, INFRASTRUCTURE_ERROR, 0 тестов;
  `.test-tools/runs/20261003-182913-6d0bb3c4/summary.json`.
  Это ожидаемый отказ защиты от неверного описания уже работающего AVD.
- Финальный холодный запуск с defaults runner и заново сгенерированным config.ini:
  2/2 UI-теста прошли, deviceApi=36, CPU=8/RAM=2048/GPU=host;
  `.test-tools/runs/20261003-183011-48bed66a/summary.json`.
  Данные AVD сохранялись, старый config.ini архивирован. Новый INI и журналы
  сохранены рядом с результатом; `ANR in` в полном logcat не найден.
  Проектный эмулятор штатно остановлен с проверкой PID/start/имени AVD.

Параметры нового API 36 профиля закреплены в runner; CPU ограничен также числом
логических CPU хоста. API 33/35 сохраняют прежние размеры/ресурсы и software GPU.
На этом шаге local, API 33/35, release R8 и Google Play upgrade не повторялись.

### Неудачные диагностические пробы

На Android Emulator 36.5.10 проверены ещё две конфигурации:

- Google APIs / `-EmulatorGpu host`: аппаратный GLES/Vulkan действительно выбран
  (Intel UHD Graphics 620 в emulator.stdout.log), но System UI и несколько служб
  снова дали ANR. UI: 2 теста, один отказ фокуса;
  `.test-tools/runs/20261003-175203-f7acbbcf/summary.json`.
- AOSP `system-images/android-36/default/x86_64`, revision 2 / software:
  новый профиль с 2 ядрами/2048 МБ, прежним экраном 1080×1920/density 420.
  Первый холодный запуск: оба UI-теста прошли, ANR в сохранённом ActivityManager
  журнале до запуска тестов не обнаружен;
  `.test-tools/runs/20261003-175803-1f0bca24/summary.json`.
  Четыре notification/reboot-фазы прошли, boot_count 1 → 2;
  `.test-tools/runs/20261003-180127-notification-smoke/summary.json`.
  Однако после reboot зарегистрирован ANR System UI (`failed to complete startup`),
  CPU pressure some avg10=73.62; одной смены образа недостаточно.
- Новый контроль boot-window остановил последующий полный запуск до Gradle:
  INFRASTRUCTURE_ERROR, 0 тестов;
  `.test-tools/runs/20261003-180323-8cf6bbaa/summary.json`.
  Это не новый отказ тестов приложения и не успешный прогон.

Google APIs профиль остановлен с проверкой PID/start/имени и сохранён без удаления
данных в `.test-tools/runs/android16-aosp-setup/google-apis-profile.avd`.
Официальный AOSP образ установлен sdkmanager, install exit 0; журнал и метаданные
SDK — в `android16-aosp-setup`. Повторная проверка SDK обнаружила доступный Emulator
37.2.12 вместо установленного 36.5.10; результаты обновления и повторов описаны здесь.

Обновление Emulator завершилось с exit 0 (`emulator-update.log`, source.properties
подтверждает 37.2.12), но само по себе проблему не устранило: AOSP 2 ядра/software
дал 1 отказ из 19 выполненных/6 пропущенных тестов;
`.test-tools/runs/20261003-180734-2c935275/summary.json`.
Проба AOSP 4 ядра/software остановлена до тестов из-за ANR (0 тестов),
`20261003-181114-86e36d34`. Отключение boot animation при 2 ядрах дало 1 отказ UI
из двух (`20261003-181343-3a294192`). Облегчённый вариант 1 ядро/720×1280/software
тоже дал 1 отказ из двух (`20261003-181724-64c4b432`).
Снимок последнего диалога и `systemui-anr.txt` сохранены рядом с отчётом.
Трасса содержит запуск SystemUIService и чтение системных Settings через Binder;
в schedstat главного потока накоплено около 21 секунды ожидания планировщика.
Это данные диагностики, а не доказанная неисправность конкретного драйвера/гипервизора.

Runner теперь позволяет явно выбрать software/host/auto, фиксирует режим и образ
в summary.json, не допускает выдавать повтор работающего AVD за смену графики.
Тег нового AVD читается из source.properties образа. Видимый ANR System UI после
boot классифицируется как ошибка окружения до старта Gradle; boot-window.log сохраняется.

## Android 16: первоначальные платформенные и UI-проверки — 3 октября 2026

Установлен официальный образ `system-images/android-36/google_apis/x86_64`.
Использован только проектный `Intime_Test_API36`, serial `emulator-5584`.
Runner и notification-smoke поддерживают `-TestApi 36`; API проверяется на устройстве.
Добавлен ScreenPlatformTest с двумя тестами и подробными русскими комментариями.
Зависимость androidx.test:core объявлена явно для ActivityScenario.

- Первые UI-прогоны `20261003-171712-e207c63f` и `20261003-172517-d530a96a`:
  по два теста, один отказ навигации. Ожидание RESUMED дополнено ожиданием фокуса
  и диагностикой фактической Activity. `dumpsys window` и снимок подтвердили
  перекрывающий приложение диалог ANR System UI. Проверки не ослаблялись.
- После выбора Wait в системном диалоге: оба UI-теста прошли,
  `.test-tools/runs/20261003-172851-32a033a8/summary.json`.
- Полный debug-набор: 25 записей JUnit, 19 выполненных, failures/errors = 0,
  6 пропущенных: две reboot-фазы, три upgrade-фазы и отказ в POST_NOTIFICATIONS
  (UI-фикстура предварительно выдала разрешение).
  `.test-tools/runs/20261003-173112-fb810891/summary.json`.
- Отдельный notification-smoke прошёл все четыре фазы: принудительный отказ
  в разрешении, реальная доставка/ACK, подготовка и проверка после настоящего reboot.
  boot_count изменился 1 → 2, восстановлен RTC_WAKEUP с deadline 1791038649205.
  `.test-tools/runs/20261003-173349-notification-smoke/summary.json`.
  После reboot вновь появился ANR System UI: успех smoke не подтверждает стабильность UI.
- Диагностический повтор с 4 ядрами/4096 МБ RAM тоже дал ANR System UI и один
  отказ UI-теста из двух (`SettingsActivity focused=false`):
  `.test-tools/runs/20261003-174228-9ba45bff/summary.json`.
  `platform-logcat.log` содержит `failed to complete startup`, CPU pressure
  some avg10=72.12; `systemui.png` показывает системный диалог. Это свидетельства
  сбоя окружения, но точная причина ещё не установлена. Увеличение ресурсов
  не стало исправлением; профиль возвращён к 2 ядрам/2048 МБ.

Проверены границы выбранных элементов списка, добавления, настроек и деталей
относительно system bars/display cutout, системный KEYCODE_BACK и кнопка возврата
к прежнему экземпляру списка. Не проверены свайп/анимация predictive back,
landscape, крупный шрифт, клавиатура и полный UI CRUD-сценарий.
Проверки выполнялись на debug, а не на production APK с R8 или подписью Play.
Local-тесты на этом шаге не повторялись; предыдущие 97 относятся к переходу SDK ниже.

Итог первоначального шага: debug-платформенные сценарии Android 16 выполнены в указанном объёме;
устойчивость окружения после холодного старта/reboot остаётся открытой.
На этом этапе следующий шаг R4.3 — подобрать и проверить устойчивую конфигурацию API 36,
повторить UI после reboot, затем перейти к release smoke.
Личные устройства и пользовательские main*.db не использовались.
Все запущенные на этом шаге проектные эмуляторы остановлены с проверкой владения.

## Переход сборки на SDK 36 — 3 октября 2026

CompileSdk/targetSdk обновлены до 36, AGP до 8.9.1, Gradle Wrapper до 8.11.1.
SDK platform 36 уже установлен; требования совместимости инструментов сверены
по официальной документации (ссылка в RELEASE.md). CI compile SDK и test.ps1
также обновлены. Java/Kotlin, Room-схема и runtime-исходники не менялись.

- Полный local online bootstrap: 97 выполненных, failures/errors/skipped = 0,
  `.test-tools/runs/20261003-164439-1e13c171/summary.json`.
- Первая попытка device offline: INFRASTRUCTURE_ERROR, 0 тестов,
  `.test-tools/runs/20261003-164815-6d1e25b3/summary.json`.
  Gradle сообщил ошибку сериализации configuration cache при разрешении AndroidX;
  вложенная причина — отсутствующие кешированные метаданные junit/runner/room-testing.
  Runner остановил принадлежащий ему AVD. Это не падение тестов приложения.
- Повтор device online на Intime_Test_API35: 23 записи JUnit, 18 выполненных,
  failures/errors = 0, 5 отдельных reboot/upgrade-фаз пропущены,
  `.test-tools/runs/20261003-165057-6490a58a/summary.json`.
  Configuration cache успешно сохранён. Проектный AVD остановлен runner,
  marker emulator.json отсутствует. Это Android 15 с приложением target 36.
- Подписанный production APK/AAB: BUILD SUCCESSFUL, 55 выполненных задач,
  R8 и lintVitalRelease прошли. Каталог:
  `.test-tools/runs/production-api36-20261003-165559`.
  APK: исходный пакет, versionCode 25/versionName 2.0.0-rc1, compile/target SDK 36,
  minSdk 24. apksigner verify и jarsigner verify завершились с кодом 0;
  SHA-256 обоих подписантов совпал с подтверждённым Upload key
  `97b60110a8e894791b4770826a8d214dc5e3a551cd24bd41d646c29f64a1dd26`.
  Сохранены артефакты, hashes.json, release-manifest.xml, mapping.txt и отчёты подписи.

PowerShell Parser и git diff --check прошли, UTF-8 BOM test.ps1 сохранён.
Новых @Test нет: выполнена существующая регрессия. API 33 и отдельные reboot/upgrade
smoke на этом шаге не повторялись. CI изменён, pipeline не запускался.
Android 16/API 36 как ОС ещё не проверен: соответствующего system image нет,
следующий шаг — добавить отдельный проектный AVD и проверить платформенные сценарии,
навигацию и отступы UI (edge-to-edge/predictive back). Нельзя считать проверку
target 36 на API 35 проверкой поведения Android 16.
Release/R8 на устройстве, bundletool-валидация, финальная версия/Play-коды,
APK с app signing key и копия реальных данных остаются открытыми. В Play ничего
не загружено, личные устройства и пользовательские main*.db не использовались.

## Подписанный production-кандидат — 3 октября 2026

Пользователь настроил четыре SIGNING_KEY_* поля в пользовательском Gradle-файле.
Проверка наличия полей не выводила их значения. Сборка :app:assembleRelease и
:app:bundleRelease с productionRelease=true, code 25/name 2.0.0-rc1 и без
unsignedProduction завершилась успешно за 32 секунды, lintVitalRelease прошёл.
R8 использовал актуальные результаты предыдущей сборки (UP-TO-DATE).
Configuration cache отключён. Каталог: `.test-tools/runs/production-signed-20261003-163519`.

- `intime-25-2.0.0-rc1-upload-signed.apk`: apksigner verify завершился с кодом 0,
  подпись v2 проверена, один подписант с SHA-256
  `97b60110a8e894791b4770826a8d214dc5e3a551cd24bd41d646c29f64a1dd26`.
- `intime-25-2.0.0-rc1-upload-signed.aab`: jarsigner verify завершился с кодом 0,
  jar verified; keytool -printcert -jarfile подтвердил тот же публичный SHA-256.
  Предупреждения о самоподписанном сертификате, отсутствии доверенной цепочки/временной
  метки и неподписанных POSIX-атрибутах сохранены в aab-signing.txt.
- aapt: исходный production-пакет, code 25/name 2.0.0-rc1, minSdk 24/targetSdk 35.
  Сохранены badging.txt, hashes.json, mapping.txt и отчёты подписи.

Успешное подписание подтвердило доступ к приватному upload key и работоспособность
локальных credentials. Keystore и пользовательский Gradle-файл не изменялись,
пароли не копировались в проект/отчёты. Код приложения не менялся; local/device-тесты
не повторялись (97 local прошли при подготовке предыдущего кандидата).
APK не устанавливался, AAB не загружался в Play. Upload-подпись APK отличается от
app signing key установленной Play v1: production-обновление ещё не проверено.
Перед загрузкой требуется API 36 и регрессия Android 16; полная bundletool-валидация,
финальная версия, Play-коды и R8 на устройстве остаются открытыми.

## Production-кандидат — 3 октября 2026

Добавлен явный productionRelease в app/build.gradle без изменения варианта по умолчанию.
Неподписанные APK/AAB с R8 собраны задачами :app:assembleRelease/:app:bundleRelease;
lintVitalRelease прошёл. Каталог артефактов/логов:
`.test-tools/runs/production-candidate-20261003`.
aapt подтвердил пакет com.vpe_soft.intime.intime, код 25, имя 2.0.0-rc1,
minSdk 24 и targetSdk 35. AAB содержит BundleConfig.pb, manifest и dex;
jarsigner -verify сообщил jar is unsigned. Полная bundletool-валидация не выполнялась.
Сохранены копии обоих артефактов, hashes.json, badging.txt и mapping.txt.
Код/имя являются предварительными, опубликованные коды Console отдельно не проверены.

Первый запуск отклонён проверкой имени версии из-за передачи параметра без кавычек
в Windows PowerShell; исправленная команда документирована в RELEASE.md.
Затем offline-сборка остановилась из-за отсутствующих lint/упаковочных зависимостей.
Повтор online прошёл: build-online.log, BUILD SUCCESSFUL, 54 задачи.
Эти инфраструктурные попытки не считаются падением тестов приложения.

Две отрицательные проверки конфигурации (--dry-run) подтвердили отказ для code 24
и неподходящего debug-сертификата: reject-code24.log, reject-debug-certificate.log.
Для последней использован стандартный debug.keystore, production-ключ не открывался.
Полная обычная local suite: 97 выполненных, failures/errors/skipped = 0,
`.test-tools/runs/20261003-163043-9122965a/summary.json`.
Сгенерированный debug BuildConfig сохранил .dev/21/1.1.8.

Подписанный production AAB, приватный upload key, APK с подписью Google и release
на устройстве не проверены. Локальные signing credentials ещё не настроены.
Перед загрузкой в Play нужен переход targetSdk 35 → 36 и регрессия Android 16;
проверенный источник требования — RELEASE.md. Эмулятор/личный телефон на этом шаге
не использовались, пользовательские main*.db не читались. R4.2/R5.1/R5.3 остаются открытыми.

## Сверка с Play Console — 3 октября 2026

Пользователь последовательно предоставил SHA-256 App signing key certificate
`4a37905063f6896ec53f3aee47757c0541ce8c5094d12f9bfb21a7348f2ef17c`
и Upload key certificate
`97b60110a8e894791b4770826a8d214dc5e3a551cd24bd41d646c29f64a1dd26`.
Первый совпадает с локальными APK из Play, второй — с публичным сертификатом
vpe_soft.jks/alias intime. Следовательно, применяется Play App Signing,
а предоставленный JKS является ключом загрузки. Прежнее сообщение о самостоятельном
хранении app signing key уточнено этими данными. Источник Console — пользователь;
агент не входил в аккаунт. Новая сборка, доступ к приватному ключу, подписание
и установка production-обновления этим сравнением не проверены. Тесты не запускались:
изменены только документы. R4.2 остаётся открытым.

## Дополнительная диагностика подписи — 3 октября 2026

ApkVerifier SDK build-tools 35.0.0 повторно проверил исходные base и три splits:
isVerified=true, сертификаты v1/v2/v3 одинаковы (`4a379050…f2ef17c`),
SigningCertificateLineage отсутствует во всех четырёх APK. Сертификат предоставленного
JKS (`97b60110…64a1dd26`) среди подписантов не найден.
Перед диагностикой каждый исходный SHA-256 совпал с source.json.
Логи: `.test-tools/diagnostics/signing-lineage-20261003/*.apk.txt`;
диагностический Java-исходник лежит рядом. Сначала source-file launcher выдал ошибку
CRC записи SDK jar при компиляции, хотя выполнил чтение; тот запуск не считается
чистой успешной проверкой. Затем helper скомпилирован отдельно без SDK в compile classpath,
и все четыре проверки с SDK jar в runtime classpath завершились с кодом 0.
SDK jar не изменялся.

Это чтение локальных APK; новые тесты приложения, установка APK, эмулятор и личный
телефон на этом шаге не запускались. Целостность JKS, приватный ключ и Play Console
не проверялись. Настройки аккаунта по одной подписи APK не устанавливаются.
Обновлены инструкции сверки в UPGRADE.md и актуальный статус RELEASE.md.

## Smoke обновления APK — 3 октября 2026

На проектном Intime_Test_API35/emulator-5580 выполнена установка копий фактических
base/splits Google Play 1.1.11/24, переподписанных стандартным debug-ключом, затем
обновление install -r до текущего debug APK с тем же production-пакетом и кодом 25.
Старый launcher создал SQLite main версии 5; фикстура содержала только синтетические
задачи/preferences. Между подготовкой и обновлением данные не очищались.

- Bootstrap HistoricalMigrationPlatformTest: 3 теста прошли,
  `.test-tools/runs/20261003-115027-0b352cb6/summary.json`.
- Итоговый smoke: prepare/verify/restart/alarm — PASSED,
  `.test-tools/runs/20261003-120309-upgrade-smoke-0a344884/summary.json`.
  Проверены UID/firstInstallTime, preferences, восемь старых полей/ID, Room 6,
  wasNotified, ACK репозитория, edit/insert/delete, AUTOINCREMENT и повторный запуск.
  Активный RTC_WAKEUP AlarmReceiver найден с ожидаемым сроком 1791021826194.
- Полный обычный local: 97 выполнены без ошибок/пропусков,
  `.test-tools/runs/20261003-120419-87a70594/summary.json`.
- Полный обычный API 35: 23 записи JUnit, 18 выполнены без ошибок, 5 фаз пропущены
  (две reboot, три upgrade),
  `.test-tools/runs/20261003-120737-3dfddf9c/summary.json`.

Промежуточные smoke-прогоны не являются успешными: 115424-ed854674 остановился
на проверке отсутствующего пакета (pm path возвращает код 1); 115449-35196512 —
на NoClassDefFoundError kotlin.jvm.internal.Intrinsics нового AndroidJUnitRunner
при работе со старым обфусцированным APK; для подготовки введена framework instrumentation.
115945-670a5eea прошёл три JUnit-фазы, но host-проверка alarm отвергла сокращённое
имя компонента .receiver.AlarmReceiver. В dumpsys будильник присутствовал;
распознавание исправлено и подтверждено итоговым полным smoke.
Полные папки этих прогонов имеют суффикс `-upgrade-smoke-<id>`.

Исходные четыре APK повторно сверены с source.json после smoke: хеши не изменились.
Тестовые production/test APK удалены; после регрессии остановлен только AVD этого
прогона с проверкой PID/времени старта/имени, marker emulator.json удалён.
PowerShell Parser и git diff --check прошли; test-upgrade-smoke.ps1 имеет UTF-8 BOM.
Личный телефон и main*.db не использовались. Доставка alarm, reboot, API 33,
production release/R8 и копия реальных данных этим шагом не проверяются.

Пользователь предоставил путь vpe_soft.jks. Без пароля прочитан публичный сертификат
alias intime: SHA-256 `97b60110a8e894791b4770826a8d214dc5e3a551cd24bd41d646c29f64a1dd26`.
Он отличается от сертификата APK из Play `4a37905063f6896ec53f3aee47757c0541ce8c5094d12f9bfb21a7348f2ef17c`.
Целостность JKS и доступ к приватному ключу без пароля не проверялись.
Роль ключа/настройки Play требуется уточнить; production-совместимость не подтверждена.
R4.2 остаётся открытым; команды и дальнейшие условия — TESTING.md и UPGRADE.md.

Проверялась debug-сборка `com.vpe_soft.intime.intime.dev`, target SDK 35,
на отдельных проектных AVD. Личные устройства и пользовательские `main*.db`
из рабочего каталога не использовались. Runtime-код в этом шаге не менялся.

## Подтверждённые результаты

| Сценарий | API 35 | API 33 |
| --- | --- | --- |
| Четыре миграционных теста Room 5 → 6 | Пройдено | Пройдено |
| ACK второй задачи через настоящий PendingIntent | Пройдено | Пройдено |
| AlarmManager доставляет событие, NotificationManager публикует уведомление, ACK переносит срок | Пройдено | Пройдено |
| POST_NOTIFICATIONS запрещён: receiver не публикует и не ставит wasNotified | Пройдено | Пройдено |
| Worker публикует спокойное уведомление без ACK, звука и вибрации в параметрах Notification | Пройдено | Пройдено |
| Настоящий канал IMPORTANCE_NONE: нет публикации и отметки wasNotified | Пройдено | Пройдено |
| Настоящий reboot: увеличение BOOT_COUNT, сохранение задач, сводка BootReceiver | Пройдено | Пройдено |
| Восстановление AlarmReceiver в AlarmManager с тем же точным сроком после reboot | Пройдено | Пройдено |

Обычная suite на каждом API: 11 тестов в отчёте, из них 9 выполнены и 2 reboot-фазы
ожидаемо пропущены. Эти две фазы проверяются отдельным smoke-прогоном, где между
ними выполняется настоящий `adb reboot`. Пропуски не считаются выполненными тестами.
Smoke дополнительно повторяет отказ в разрешении и доставку/ACK отдельными запусками.

## Отчёты

- API 35, обычная suite: `.test-tools/runs/20261002-144015-d1cbdecd/summary.json`.
- API 35, дополнительная доставка/ACK после актуализации теста:
  `.test-tools/runs/20261002-144739-7cb67276/summary.json`.
- API 35, smoke: `.test-tools/runs/20261002-145530-notification-smoke/summary.json`;
  BOOT_COUNT 10 → 11; срок восстановленного будильника `1790942753022` мс Unix epoch.
- API 33, обычная suite: `.test-tools/runs/20261002-150359-e17ebdc0/summary.json`.
- API 33, smoke: `.test-tools/runs/20261002-150945-notification-smoke/summary.json`;
  BOOT_COUNT 1 → 2; срок восстановленного будильника `1790943646320` мс Unix epoch.
- Полная локальная suite после изменения runner: 51 тест, без ошибок;
  `.test-tools/runs/20261002-145958-5c03188c/summary.json`.

Логи smoke содержат результат каждой instrumentation-фазы, состояние разрешения,
BOOT_COUNT и снимки `dumpsys alarm` до/после reboot. Фикстура состоит только из
двух синтетических задач в файловой базе `.dev`-приложения; после проверки удаляются
их ID и восстанавливается прежняя отметка использования.

## Границы проверки

- Доставка проверена с выданным разрешением точных будильников. Время inexact-
  доставки, Doze и ограничения производителей телефонов здесь не проверялись.
- Тест разрешения вызывает обработчик receiver на Android; доставка самого
  будильника проверяется отдельным сценарием с разрешёнными уведомлениями.
- Подтверждена публикация в NotificationManager, а не прочтение в шторке.
  Параметры тишины проверены у опубликованного объекта; физическая слышимость
  и вибрация на телефоне не измерялись.
- Периодический worker вызывается явно в тесте спокойного уведомления. Ожидание
  реального 15-минутного запуска WorkManager здесь не выполнялось.
- Reboot проверен при разрешённых уведомлениях и доступном канале. Комбинации
  reboot с запрещённым разрешением/каналом отдельно не прогонялись.
- Production-обновление, release/R8 и матрица API 24/31 остаются в следующих шагах.

## Подготовка, влияющая на корректность тестов

- Проверка отключённого канала выполняется последней: приложение не может
  повысить его importance обратно обычным повторным createNotificationChannel.
- Отзыв POST_NOTIFICATIONS выполняется до instrumentation, чтобы не завершить
  её процесс отзывом разрешения.
- После установки APK выполняется обычный запуск launcher и уход домой:
  `stopped/notLaunched` иначе блокируют BOOT_COMPLETED, несмотря на instrumentation.
- `sys.boot_completed=1` не подтверждает доставку BOOT_COMPLETED приложению.
  До второй фазы хост ждёт восстановленного будильника, чтобы instrumentation
  не подавила ещё ожидающий системный broadcast.

Команды повторения и ограничения описаны в `TESTING.md`.

## Повторная проверка после R2.1 — 2 октября 2026

После замены отдельных executors общими очередями:

- Полный локальный набор: 55 тестов, 0 ошибок, 0 пропусков.
  Отчёт: `.test-tools/runs/20261002-173715-335e183a/summary.json`.
- API 35: 11 записей в отчёте, из них 8 выполнены и 3 пропущены
  (отказ в разрешении и две фазы reboot запускаются отдельно).
  Отчёт: `.test-tools/runs/20261002-173821-3197ef75/summary.json`.
- Отдельный API 35 smoke: все четыре фазы прошли — отказ в разрешении,
  настоящая доставка alarm/ACK, подготовка reboot, проверка после reboot.
  Счётчик загрузок изменился с 12 на 13; до запуска второй instrumentation
  подтверждено восстановление прежнего срока AlarmReceiver системой.
  Отчёт: `.test-tools/runs/20261002-174128-notification-smoke/summary.json`.

Эти проверки подтверждают обычные сценарии работы новых очередей. Гонки
перепланирования и нагрузочные сценарии edit/ACK/delete/import остаются R2.2/R2.3.
API 33 на этом шаге повторно не запускался; его предыдущие результаты относятся к R1.5.

## Повторная проверка после R2.2 — 2 октября 2026

- Три новых локальных теста конкурирующих перепланирований прошли:
  `.test-tools/runs/20261002-180421-a3d1c478/summary.json`.
  При временном отключении блокировки все три упали, поскольку второй вызов
  не ждал завершения первого:
  `.test-tools/runs/20261002-180545-d5bf132a/summary.json`.
  Блокировка восстановлена перед последующими проверками.
- Полный локальный набор: 58 тестов, без ошибок и пропусков.
  `.test-tools/runs/20261002-180634-ee4a91ef/summary.json`.
- Обычная suite API 35: 8 выполненных тестов, без ошибок, 3 отдельных
  permission/reboot-сценария пропущены для запуска через smoke.
  `.test-tools/runs/20261002-180747-22d34ed8/summary.json`.
- Первый smoke подтвердил запрет уведомлений, доставку/ACK и подготовку reboot,
  но восстановление после reboot не подтвердилось:
  `.test-tools/runs/20261002-181122-notification-smoke/summary.json`.
  В сохранённом `reboot-system.log` зафиксированы native-сбой system_server
  и убийство процесса `.dev` при запуске BootReceiver из-за нехватки памяти
  (`lowmemorykiller`, pid 3821). Счётчик загрузок изменился с 15 на 17.
  Это ограничение данного прогона на AVD; оно не считается успешной reboot-проверкой.
  Отдельная финальная фаза не нашла boot-уведомление и очистила синтетическую
  фикстуру в finally; её журнал — `fixture-cleanup.log` в том же каталоге.
- Повторный API 35 smoke прошёл все четыре фазы, включая восстановление
  того же срока AlarmReceiver до запуска проверяющей instrumentation и boot-уведомление.
  Счётчик загрузок изменился с 17 на 18, синтетическая фикстура очищена.
  `.test-tools/runs/20261002-181801-notification-smoke/summary.json`.

Конкуренция координатора проверена управляемыми локальными паузами; Android-прогоны
подтверждают обычную доставку, ACK и boot. Нагрузочные последовательности операций
и полная проверка goAsync остаются R2.3. API 33 на R2.2 повторно не запускался.

## Проверки R2.3 — 2 октября 2026

- Быстрые операции репозитория: шесть локальных тестов, включая 120 операций
  insert/edit/ACK/delete, import после edit/ACK, ошибку импорта с продолжением очереди,
  ACK/import с одинаковым ID, устаревшую форму после ACK и снимок отправленных данных.
  `.test-tools/runs/20261002-191528-4fa19f77/summary.json`.
- Все три receiver: шесть тестов onReceive/goAsync с PendingResult Robolectric.
  Проверены успех, искусственная ошибка, завершение, откат ACK/alarm и следующая
  успешная операция той же очереди.
  `.test-tools/runs/20261002-191108-1d45ab8c/summary.json`.
- При временном отключении транзакции ACK новый тест конкуренции с импортом упал:
  импорт не ждал завершения ACK. Исправление восстановлено перед полным прогоном.
  `.test-tools/runs/20261002-191248-41016ccf/summary.json`.
- Полный локальный набор после исправлений: 70 тестов, без ошибок и пропусков.
  `.test-tools/runs/20261002-191648-0b16c219/summary.json`.
- Первый Android-прогон остановил один новый тест на проверке типа ошибки
  закрытой базы: ожидался IllegalStateException, фактически Room 2.8 вернул
  SQLException с сообщением `connection is closed`. Ожидание исправлено;
  это падение тестовой проверки до отправки ACK, а не незавершённый broadcast.
  `.test-tools/runs/20261002-191812-0233215f/summary.json`.
- Повторный полный API 35 прогон: 13 записей, 11 выполненных без ошибок и 2 пропуска
  отдельных reboot-фаз. Проверка запрещённого разрешения в этом прогоне выполнилась.
  Все три теста ACK прошли: идентичность действия,
  отсутствующая задача, подтверждённая ошибка закрытой базы и восстановление очереди.
  `.test-tools/runs/20261002-192142-d89bca4a/summary.json`.

Локальный PendingResult моделирует завершение receiver, но не сроки broadcast
Android под произвольной нагрузкой. Сценарии не покрывают убийство процесса ОС
или неограниченное накопление операций в очереди. Reboot и API 33 на R2.3
повторно не запускаются; их предыдущие результаты сохранены выше.

## Локальные проверки R3.1 — 2 октября 2026

- Import/export, включая восемь новых тестов контракта формата: 17 тестов без ошибок.
  `.test-tools/runs/20261002-193420-602cb3ed/summary.json`.
- Новый асинхронный import/Room-тест: девять категорий ошибок сохраняют все поля
  двух задач, wasNotified и исходный объект alarm. Корректная первая строка перед
  ошибочной второй не применяется частично.
  `.test-tools/runs/20261002-193721-e6e7cb72/summary.json`.
- Полный локальный набор: 79 тестов без ошибок и пропусков.
  `.test-tools/runs/20261002-193940-b7326410/summary.json`.

Проверены типы, структура и совместимость JSON, неотрицательные времена,
представимые параметры и уникальные ID. Переполнение дальнейших вычислений
и календарные границы остаются R3.2. Строгий синтаксис RFC не проверяется
отдельно от org.json; схема и правила описаны в BACKUP_FORMAT.md.
Схема Room и Android-обработчики не менялись, поэтому device-тесты на R3.1
не повторялись; ранее подтверждённые Android-сценарии перечислены выше.

## Проверки R3.2 — 2 октября 2026

- Калькулятор: 20 локальных тестов до добавления случая UTC+14.
  `.test-tools/runs/20261002-201840-bb557b8a/summary.json`.
- Import/export: 20 тестов, включая отказ для переполняющегося интервала,
  нулевого результата quant и даты вне поддерживаемого диапазона.
  Room-проверка подтверждает сохранение всех полей двух задач, wasNotified
  и исходного alarm при ошибочной второй строке после корректной первой.
  `.test-tools/runs/20261002-202150-c5e3bbfd/summary.json`.
- Первый полный локальный прогон: 89 тестов без ошибок и пропусков.
  `.test-tools/runs/20261002-202419-acad26bd/summary.json`.
- Полный API 35 прогон: 15 записей, 13 выполнены без ошибок,
  две отдельные reboot-фазы пропущены. Включает новые Calendar/org.json-тесты,
  миграции, реальный ACK и уведомления. Этот прогон предшествует уточнению UTC+14.
  `.test-tools/runs/20261002-202626-acf8396c/summary.json`.
- После уточнения предварительной границы для местного 10000 года в UTC+14:
  полный локальный набор — 90 тестов, без ошибок и пропусков.
  `.test-tools/runs/20261002-203033-7b2957dc/summary.json`.
- Повторный CalendarPlatformTest на API 35 после этого уточнения: оба теста
  прошли, без ошибок и пропусков, включая допустимую минуту в местном 10000 году.
  `.test-tools/runs/20261002-203311-2554fb41/summary.json`.
  Проектный AVD остановлен runner после завершения; личные устройства не использовались.

Новые проверки охватывают предельные int/long, положительную длительность после
quant, точные целые 95% caution, конец месяца, високосные годы, неделю через
Новый год, DST 23/25 часов и конец поддерживаемого UTC-диапазона. Новые тесты
снабжены подробными русскими комментариями. API 33 и настоящий reboot на R3.2
повторно не запускались; расчёты не подтверждают точность доставки alarm до 1 мс.
Откат при ошибке записи Room и полное состояние успешной замены остаются R3.3.

## Проверки R3.3 — 2 октября 2026

- Четыре новых локальных теста ImportTransactionStateTest прошли:
  `.test-tools/runs/20261002-204440-d532d457/summary.json`.
  Проверены ошибка второй вставки, откат полей/wasNotified и прежнего alarm,
  успешный повтор в той же очереди, полная замена с повторным ID,
  ближайший alarm с новыми ID/описанием, очистка и импорт только прошлых сроков.
  Успех/ошибка проверяются в callback на main после выполнения операций.
- При временном удалении общей транзакции один новый тест упал:
  вместо двух старых задач осталось ноль. Остальные три прошли.
  `.test-tools/runs/20261002-204600-a90f286b/summary.json`.
  Это намеренная проверка чувствительности; транзакция восстановлена
  перед итоговым локальным и Android-прогонами. Runtime-код в результате не изменён.
- Полный локальный набор после восстановления: 94 теста, без ошибок и пропусков.
  `.test-tools/runs/20261002-204656-aff18522/summary.json`.
- На API 35 оба новых ImportTransactionPlatformTest прошли без ошибок и пропусков:
  настоящие SQLite/Room откатывают замену при ошибке второй вставки, сохраняют все
  старые поля/wasNotified и допускают повтор. Успешный импорт проверен для всех полей,
  прежнего ID, сброса wasNotified, удаления старых строк и последующей полной очистки.
  `.test-tools/runs/20261002-204803-527b10d1/summary.json`.
  Запуск ограничен этим классом; весь Android-набор, API 33 и reboot на R3.3
  не повторялись. Проектный AVD остановлен runner после завершения.

Новые тесты снабжены подробными русскими комментариями. Ошибка записи создаётся
AFTER INSERT-триггером RAISE(ABORT) для второй строки в базе в памяти. Проверяется
откат удаления и вставки внутри Room-транзакции; ошибки commit, сбой процесса/диска
и отказ AlarmManager после commit не моделируются. Системное расписание и Room
не образуют единую транзакцию. Пользовательские main*.db не открываются.

## Исторические SQLite-миграции в R4.2 — 3 октября 2026

- Добавлена миграция 4 → 5 (quant DEFAULT 1), цепочка 4 → 5 → 6 зарегистрирована
  в общем builder AppDatabase, используемом production singleton и новыми тестами.
  Схема Room остаётся 6; destructive fallback не добавлялся.
- Три новых локальных HistoricalMigrationTest прошли:
  `.test-tools/runs/20261003-001639-aa03828a/summary.json`.
- Полный локальный набор: 97 тестов, без ошибок и пропусков.
  `.test-tools/runs/20261003-001801-6dc65fde/summary.json`.
- Полный API 35: 20 записей JUnit, 18 выполненных без ошибок и две пропущенные
  отдельные reboot-фазы. Все три HistoricalMigrationPlatformTest прошли:
  версии 4/5, повторное открытие/DAO и откат несовместимой схемы на настоящем SQLite.
  Также выполнены прежние миграции, Calendar/JSON, import, реальный ACK и уведомления.
  `.test-tools/runs/20261003-001937-5c899f10/summary.json`.
  Проектный AVD остановлен runner; API 33 и настоящий reboot на этом шаге не повторялись.

Фикстуры создаются обычным SQLite по DDL из `v1.1.2` и `0add531`, без метаданных
Room и без чтения пользовательских файлов. Проверены все старые поля и ID,
новые значения по умолчанию, identity Room, повторное открытие, сохранение
AUTOINCREMENT после удаления ID 500, insert/edit/markNotified/ACK/delete через DAO.
Повреждённая схема версии 5 (wrong_amount вместо amount) должна откатить миграцию,
сохранив старую версию, строки, описание/quant первой задачи и отсутствие wasNotified.
Новые тесты содержат подробные русские комментарии.

Это проверка открытия/миграции синтетических баз, без установки нового APK поверх
Google Play v1, проверки production-сертификата, реальных данных и alarm после обновления.
Версии схемы 1–3 пока не имеют маршрутов. R4.2 целиком остаётся открытым.

Пользователь предоставил ссылку на приложение в Google Play, но APK установленной
версии ещё не получен. Подготовлен scripts/export-installed-apk.ps1 с обязательным
DeviceSerial для чтения путей pm path и копирования всех APK через adb pull.
PowerShell Parser не обнаружил синтаксических ошибок. Скрипт не запускался на
личном телефоне; реальная доступность APK и совместимость splits с AVD не проверены.

## Проверка извлечённых APK Google Play v1 — 3 октября 2026

Пользователь выполнил exporter, комплект сохранён в
`.test-tools/upgrade-apks/20261003-113647-3c8591ca`.
Агент анализировал только локальные APK, без обращения к телефону или его базе.

- Четыре SHA-256 совпали с source.json: base и splits en/ru/xxhdpi.
- aapt: пакет com.vpe_soft.intime.intime, versionName 1.1.11, versionCode 24,
  minSdk 23, targetSdk 36; launcher — прежний MainActivity.
- apksigner verify: все четыре APK прошли с кодом 0 и одинаковым сертификатом
  SHA-256 `4a37905063f6896ec53f3aee47757c0541ce8c5094d12f9bfb21a7348f2ef17c`.
  Проверка base подтверждает подписи v1/v2/v3 и SourceStamp; предупреждения META-INF
  сохранены в журнале и не привели к отказу проверки v2/v3.
- Метаданные и выводы инструментов — в `analysis` рядом с APK. Установка на AVD,
  совместимость подписи новой сборки и обновление приложения этим не проверяются.

Это анализ артефактов, не новый тестовый прогон. Runtime-код не изменялся,
локальные/device-тесты на этом шаге не повторялись. R4.2 остаётся открытым.

Дополнение от пользователя 3 октября: Google Play-релиз собран на основе коммита
`0add5312c29370f4a8c176fa56f8f1f5f2a23fb1`. Его SQLiteOpenHelper использует базу
`main` версии 5; проверенная историческая фикстура версии 5 создана по DDL этого
же коммита. Для данного релиза основной маршрут — 5 → 6. Происхождение подтверждено
владельцем проекта; воспроизводимость APK и фактическая база телефона не проверялись.
