# Intime Release Guide

This document describes the process for building and releasing Intime.

Доступ через Google Play API подготавливается отдельно: [PLAY_API.md](PLAY_API.md).
Локальная read-проверка и 13 host-тестов прошли. После исправления приглашения
OAuth/generatedapks.list версии 26 PASSED, App signing сертификат совпал,
универсальный APK доступен в метаданных. Загрузка AAB/скачивание APK через API
ещё не выполнены. Публикация скриптом не выполняется.

Текущий локальный кандидат — **27/2.0.0-rc3**, 9 октября 2026, с поддержкой
исторической meta backup_version=1 из Play 25. Подписанные APK/AAB собраны с
R8/lint; Upload key, манифесты, bundletool и совпадение mapping проверены.
Семь конфигурационных случаев, свежие local 101/101, API 35 UI 3/3 и release
15/15 прошли, включая исторический SAF-импорт и restart. Артефакты:
`.test-tools/runs/20261009-103501-release27-historical-json/candidate/`.
Результаты и SHA-256 — DEVICE_TEST_RESULTS.md. Сборка не загружена/не опубликована;
до загрузки проверить свободность кода 27 в Console. Следующий шаг — получить
Play APK с App signing key и проверить обновление 25 → 27 на проектном AVD.
Скачанный APK 26/rc2 неизменён и исправления импорта не содержит.

### Получение Play APK 27 для следующей проверки

Runner готов к явно указанным 27/2.0.0-rc3; host/preflight проверки прошли,
но обновление 25 → 27 ещё не выполнялось: APK 27 из Console пока отсутствует.
Следующие действия выполняет владелец Play Console:

1. Проверить, что код 27 ещё не занят. Открыть InTime → Тестирование и выпуск →
   Тестирование → Внутреннее тестирование. Создать выпуск или открыть уже имеющийся
   неопубликованный выпуск для редактирования; существующие выпуски не удалять.
2. Загрузить `.test-tools/runs/20261009-103501-release27-historical-json/candidate/intime-27-2.0.0-rc3.aab`.
   Проверить code 27/name 2.0.0-rc3. Сохранить как проект/черновик; внедрение
   и публикация для получения тестового файла сейчас не нужны.
   [Подготовка выпуска](https://support.google.com/googleplay/android-developer/answer/9859348?hl=ru).
3. После обработки открыть Тестирование и выпуск → Последние выпуски и наборы →
   Все версии приложения → 27 → Загрузки. В таблице объектов скачать
   «Подписанный универсальный APK-файл». Это файл с App signing key.
   [Скачивание универсального APK](https://support.google.com/googleplay/android-developer/answer/9844279?hl=ru).
4. Сохранить как `C:/Users/Valentin/Downloads/27.apk`, сообщить путь. Агент проверит
   сертификат и версии до install -r на проектном API 35; личный телефон не нужен.

Доступность скачивания после сохранения конкретного черновика не подтверждена
в этом аккаунте. Если кнопки нет или Console требует публикацию, сначала сообщить
точный экран/сообщение; не публиковать выпуск только ради получения APK.
Прямой Upload-key APK из локального архива не подходит для этого обновления.

9 октября пользователь предоставил подписанные Play APK 25/1.1.12 и
26/2.0.0-rc2; оба прошли локальный preflight с App signing key. Получение APK
больше не блокировало проверку 25 → 26. Обновление 25 → 26 прошло 8/8 фаз на API 35
с синтетическими задачами; реальные данные и полный Play release smoke ещё открыты.
Исторические записи ожидания ниже сохранены; актуальный статус — UPGRADE.md.

> Актуальный статус — в ROADMAP.md, путь обновления v1 — в [UPGRADE.md](UPGRADE.md).
> По умолчанию release имеет пакет `.dev`. Production-кандидат включается явно
> через `-PproductionRelease=true`; его проверка описана ниже.
> Старый APK из Google Play проверен; обновление 24 → 25 прошло с debug-подписью
> и синтетическими данными на API 35. Сертификат JKS отличается от подписи Play APK;
> пользователь предоставил оба сертификата Console: JKS соответствует Upload key,
> а APK — App signing key. Исходные Play APK 25 → 26 проверены на проектном API 35.
> Позднее Play25→27 прошёл на логической копии17 реальных задач из JSON,API35,9/9.
> Исходные SQLite/WAL/preferences телефона не проверялись; исторические
> отметки сборки ниже не подтверждают готовность production-релиза.

## R6.1: Release Signing Configuration

### Исторический локальный кандидат — 26/2.0.0-rc2, 8 октября 2026

Пользователь подтвердил максимум использованных Console-кодов 25. Production
guard теперь требует 26..2100000000; новый подписанный APK/AAB 26/2.0.0-rc2
собран с R8/lint. apksigner/jarsigner, сертификаты Upload key, bundletool,
манифесты и встроенный/внешний mapping прошли. Шесть конфигурационных случаев,
девять APK-метаданных, свежие local 97/97, API 36 UI 3/3 и release 14/14 прошли.
Первый smoke с прежним rc1-селектором сохранён FAILED 3/14; полный повтор
проверил тот же APK с явными code/name, без пересборки.

Архив: .test-tools/runs/20261008-224203-release-version-guard/candidate.
Исходный commit, рабочий diff, хеши, mapping и все попытки — DEVICE_TEST_RESULTS.md.
Это локальный Upload-key кандидат, не опубликованная или подписанная Play
сборка. Рабочий телефон не использовался; R4.2/физический Doze и финальная
production-версия остаются открытыми. Все APK/AAB code 25 ниже исторические.

### Актуальный AAB — 4 октября 2026

После этого кандидата заменены launcher icons и собраны отдельные свежие APK/AAB:
`.test-tools/runs/release-icons-20261004-221247/`. Подписи/Upload key,
bundletool/манифест/mapping прошли, UI 3/3 и release smoke 14/14 на API 36,
иконка осмотрена в launcher. Хеши и границы — DEVICE_TEST_RESULTS.md.
Кандидат ниже сохраняется как исторический артефакт до замены иконок.

Подписанный кандидат **25/2.0.0-rc1** из коммита `a0c055f` собран с R8 и
lintVitalRelease. `jarsigner` подтвердил подпись, сертификат совпал с Upload key,
кэшированный bundletool 1.17.2 успешно выполнил validate/dump manifest без загрузок.
Манифест внутри AAB: исходный package, minSdk 24, targetSdk/compileSdk 36,
debuggable отсутствует. Встроенная карта R8 совпадает с сохранённой mapping.txt.
Отчёт/артефакты: `.test-tools/runs/production-aab-20261004-214440/`;
подробности и SHA-256 — [DEVICE_TEST_RESULTS.md](DEVICE_TEST_RESULTS.md).

Проверенный вызов PowerShell (JDK 17, SDK и локальные signing credentials настроены):

```powershell
./gradlew.bat ':app:bundleRelease' '-PproductionRelease=true' '-PreleaseVersionCode=25' '-PreleaseVersionName=2.0.0-rc1' --offline --no-daemon --no-watch-fs --no-configuration-cache
```

Версию передавать в кавычках. В этом шаге unit/device-тесты не запускались,
runtime не изменялся; ранее проверенный APK и новый AAB — отдельные артефакты.
Новый AAB не опубликован, финальная версия/Play коды не согласованы,
обновление Play v1 и оставшиеся device-проверки открыты. Более ранние записи
ниже описывают состояние на момент соответствующего прогона.

### Production candidate — 3 октября 2026

Обычные сборки остаются `.dev`/21/1.1.8. Production-режим сохраняет исходный пакет
`com.vpe_soft.intime.intime`, требует явных releaseVersionCode (>26) и releaseVersionName.
Разрешены только `:app:assembleRelease` и `:app:bundleRelease`; совмещение с upgradeSmoke
отклоняется. Release использует R8. Код 25 и имя 2.0.0-rc1 — локальный кандидат;
перед загрузкой проверить все использованные коды Console и согласовать финальную версию.
Диагностика рабочего телефона 8 октября показала Play v1 1.1.12/versionCode 25.
Код 25 уже занят и не подходит для новой загрузки в Play:
[правило versionCode](https://developer.android.com/studio/publish/versioning).
Следующий финальный код должен быть выше 25 и не использоваться в Console;
Пользователь подтвердил, что наибольший использованный код Console — 25;
следующий локальный кандидат — 26/2.0.0-rc2. Финальная production-версия ещё
согласуется отдельно. Новые production-сборки с кодом 25 теперь отклоняются.
Команды с кодом 25 ниже описывают исторические сборки и не проходят текущий guard.

Подготовка следующего кандидата после подтверждения свободного кода (актуально 9 октября):

После получения Play APK 26 guard обновлён: коды 25 и 26 отклоняются,
допустимы 27..2100000000. Старые команды ниже с занятыми кодами исторические.

```powershell
./gradlew.bat ':app:assembleRelease' ':app:bundleRelease' '-PproductionRelease=true' '-PreleaseVersionCode=27' '-PreleaseVersionName=2.0.0-rc3' --offline --no-daemon --no-watch-fs --no-configuration-cache
```

Для smoke нового APK передать `-ExpectedVersionCode 27 -ExpectedVersionName 2.0.0-rc3`
вместе с его путём. Значения по умолчанию 25/2.0.0-rc1 оставлены только для
воспроизведения исторических артефактов. Код и имя проверяются в APK до установки,
а имя дополнительно на экране настроек. Это проверка выбранного APK на проектном AVD,
а не самостоятельная сверка его доступности в Play Console.

Неподписанный кандидат для проверки упаковки (PowerShell, JDK 17 и SDK настроены):

```powershell
./gradlew.bat ':app:assembleRelease' ':app:bundleRelease' '-PproductionRelease=true' '-PunsignedProduction=true' '-PreleaseVersionCode=25' '-PreleaseVersionName=2.0.0-rc1' --offline --no-daemon --no-watch-fs --no-configuration-cache
```

`unsignedProduction` игнорирует signing credentials. Для подписанного AAB убрать этот флаг
и предварительно настроить все четыре SIGNING_KEY_* значения в окружении или пользовательском
Gradle-файле. Путь: `C:/Users/Valentin/vpe_soft.jks`, alias `intime`; пароли вводятся локально.
Production-сборка без полного набора параметров подписи завершается ошибкой.
Перед сборкой проверяется целостность keystore и публичный SHA-256 upload-сертификата
`97b60110a8e894791b4770826a8d214dc5e3a551cd24bd41d646c29f64a1dd26`.
Доступность приватного ключа с заданным key password проверяется при подписании артефакта.

Текущая конфигурация имеет compileSdk/targetSdk 36 (обновлена 3 октября),
AGP 8.9.1 и Gradle 8.11.1. Совместимость инструментов проверена по
[документации Android](https://developer.android.com/build/releases/about-agp).
Образ API 36 установлен; debug-проверки на Android 16 описаны в DEVICE_TEST_RESULTS.md.
Они не заменяют проверку release с R8 и полного пользовательского smoke-сценария.
Для загрузки обновления в Play требуется API 36.
Источник: [требования Google Play](https://developer.android.com/google/play/requirements/target-sdk),
проверены 3 октября 2026. Подпись upload key не делает локальный APK совместимым
обновлением Play v1; нужен APK с app signing key через Play Console (UPGRADE.md).
Сборка кандидата не публикует приложение.

Неподписанная упаковка 3 октября прошла (R8 и lintVitalRelease), артефакты сохранены
в `.test-tools/runs/production-candidate-20261003`. Полная local suite — 97 тестов;
подписанные APK/AAB тоже собраны 3 октября, подпись и upload-сертификат проверены:
`.test-tools/runs/production-signed-20261003-163519`. Настройки приватного ключа работают.
Этот исторический кандидат имел targetSdk 35; после обновления SDK нужны свежие артефакты.
Последующая проверка свежего release APK на API 36 описана ниже.

После перехода на SDK 36 свежие подписанные APK/AAB собраны и проверены:
`.test-tools/runs/production-api36-20261003-165559` (compile/target 36, minSdk 24,
upload-сертификат совпал). 97 local и 18 device-тестов прошли; device выполнялся
на API 35, пять отдельных фаз пропущены. На отдельном AVD Android 16 также прошли
19 debug-тестов и четыре notification/reboot-фазы; ограничения окружения и UI
указаны в отчёте. Эти артефакты ещё не загружались в Play.
Для локальной debug-регрессии закреплён проектный AOSP API 36 на Emulator 37.2.12
с аппаратной графикой; уведомления/reboot и полный набор после reboot прошли.
Это проверка Android API без служб Google, а не release R8 или обновления через Play.
Подробные результаты и границы — DEVICE_TEST_RESULTS.md.

Свежий production APK 25/2.0.0-rc1 с R8 и lintVitalRelease собран отдельно:
`.test-tools/runs/release-r8-20261003-184243` (APK и mapping одной сборки).
Два чистых UI smoke-прогона на AOSP API 36 прошли все пять фаз:
CRUD, ACK из деталей и перенос срока, настройки/Back, сохранение после
перезапуска процесса, постановка/отмена inexact alarm. APK non-debuggable,
подписан upload key; это не проверка обновления Play v1. AAB в этом шаге
не пересобирался. Команда `scripts/test-release-smoke.ps1 -ApkPath <APK>`
и требования — TESTING.md, хеши/логи/границы — DEVICE_TEST_RESULTS.md.
Тот же APK затем прошёл расширенный smoke из семи фаз с JSON export/import
через системный SAF picker, удалением и восстановлением задачи/alarm после
перезапуска процесса. Подробные границы JSON-проверки — DEVICE_TEST_RESULTS.md.
В следующем расширении того же smoke десять фаз прошли на API 36 с настоящим
reboot (тот же alarm восстановлен до открытия UI), доставкой минутного уведомления
через AlarmManager и ACK из шторки с новым сроком/перезапуском процесса.
Для доставки exact разрешено через appops только тестовой установке;
reboot проверял inexact fallback. Отказы разрешений, отключённый канал и
остальные платформы на этом этапе ещё требовали release-проверок;
этот результат не закрывает полную smoke-матрицу или готовность к публикации.

После исправлений lint и слияния локальной master собран новый production APK
25/2.0.0-rc1: `.test-tools/runs/release-permissions-20261003-225632`
(APK и mapping одной сборки; R8 и lintVitalRelease прошли).
Полный smoke на AOSP API 36 прошёл **14/14 фаз**:
`.test-tools/runs/20261003-231028-release-smoke/summary.json`.
Добавлены отказ POST_NOTIFICATIONS и отключение канала через системные настройки:
receiver срабатывает, alarm исчезает, уведомление не появляется до пяти секунд после срока.
После снятия каждого запрета новый UI ACK существующей задачи начинает отсчёт;
уведомление доставляется, ACK из шторки переносит срок.
Это не проверка wasNotified или автоматической доставки старой просроченной задачи
без нового ACK. Проверены чистая установка, upload key и non-debuggable APK;
обновление Play v1, остальные API и Doze остаются открытыми. AAB после последних
изменений не пересобран; прежний AAB является историческим артефактом.
Хеши, частичный неуспешный прогон и границы проверки — DEVICE_TEST_RESULTS.md.

4 октября тот же APK (без изменений runtime) прошёл **14/14 фаз на AOSP API 35**:
`.test-tools/runs/20261004-103427-release-smoke/summary.json`.
Runner принимает `-TestApi 35/36` и сверяет выбранные serial/AVD/API.
Проверенный API 35 профиль: Emulator 37.2.12, AOSP default x86_64 rev 2,
2 CPU, 4 ГБ RAM, 1080×1920/density 420, host GPU; он закреплён в test.ps1.
Прежний Google Play 16 КБ профиль сохранён отдельно после lowmemorykiller
и native падения system_server; успешность на 16 КБ этим AOSP-прогоном не подтверждена.
Оставшиеся API 24/31/33, Doze, обновление Play и новый AAB ещё требуют проверки.

Затем 4 октября тот же APK прошёл **14/14 фаз на Google APIs API 33**:
`.test-tools/runs/20261004-110211-release-smoke/summary.json`.
Bootstrap UI-тестов также прошёл 2/2. Runner теперь принимает `-TestApi 33/35/36`;
на API 33 исправлены действия harness: ожидание фокуса/текста и открытия Edit,
Back для скрытия IME выполняется только при её показе. Runtime APK не менялся.
API 35/36 новой редакцией runner в этом шаге не повторялись.
Следующие платформы — API 31/24; Doze, 16 КБ, Play upgrade и новый AAB открыты.

API 31 затем прошёл **14/14 фаз** на том же APK:
`.test-tools/runs/20261004-142752-release-smoke/summary.json`, UI bootstrap 2/2.
Runner принимает API 31/33/35/36; на Android 12 общий запрет/восстановление
проверены через системный переключатель приложения, канал — отдельно.
Шторка подтверждается свежим XML, имя экспорта сверяется до Save.
Один предыдущий reboot не восстановил alarm; вызов BootReceiver не найден,
причина не установлена. Добавлены package-логи до/после reboot и 15 секунд
ожидания на API 31 перед reboot; это не доказанное исправление причины.
Exact-доступ на API 31 был доступен изначально: его запрет/inexact fallback
этим прогоном не проверен. API 33/35/36 новой редакцией runner не повторялись.
Точечный повтор API 31 без 15-секундной паузы прошёл 3/3 reboot и семь
подготовительных фаз (`20261004-154528-release-smoke`), UI bootstrap 2/2.
Причина прежнего единичного сбоя не установлена; три успешных повтора не являются
доказательством исправления. Runner сохраняет package/broadcast/logcat каждого
повтора. API 24, редкий сбой reboot API 31, Doze, 16 КБ, Play upgrade и новый AAB открыты.

Точечный Doze smoke на существующем AOSP API 36 прошёл 11/11
(`20261004-194701-release-smoke`), UI bootstrap 2/2. Реальный exact alarm
доставил уведомление при подтверждённом IDLE и выключенном экране; после выхода
из сна ACK/перезапуск сохранили новый срок. Изначально отключённый deep idle
временно включён и восстановлен, battery simulation снята. Это одна принудительная
Doze-доставка, не квоты повторных alarm, естественный вход в Doze или inexact
fallback. Новый SDK-образ/AVD не устанавливался; production APK/AAB не пересобирались.

Debug integration на API 36 проверяет настоящий PeriodicWorkRequest/WorkManager:
первое исполнение, тихое уведомление без ACK и будущий срок следующего запуска.
Новый тест прошёл отдельно (`20261004-204057-8bf694bb`) и в классе доставки
(`20261004-204231-840fbc11`, 5 тестов по отчёту, без ошибок). Это не release/R8
проверка worker и не ожидание второго 15-минутного запуска; эти пункты остаются открытыми.

Дополнительные debug-фазы `20261004-210140-periodic-smoke` прошли 2/2:
повтор спустя 23 минуты 57 секунд после завершения прежнего процесса, с сохранением
UUID/данных задачи и дальнейшим периодическим расписанием. Условие прогона:
временно уменьшен пакетный порог одиночных CPU jobs с 3 до 1 после ожидания;
исходный override и эффективный порог восстановлены. Период/часы не ускорялись,
job run вручную не вызывался. Это не default-batching и не release/R8 worker;
срок обычной доставки на устройстве/OEM, Doze и регистрация MainActivity открыты.

Регистрация работы MainActivityV2 отдельно проверена в debug integration на API 36:
одна активная work, 15 минут/BatteryNotLow/без сети; recreate и новое открытие
Activity сохраняют UUID и срок (KEEP). Новый тест прошёл отдельно
(`20261004-213659-9ec40a96`), UI-класс 3/3 (`20261004-213941-eb0b8a0e`).
Это не release/R8 доставка, смерть процесса или проверка остальных API.

### Create a Keystore (first time only)

For a new application only, if you don't have a keystore yet, create one.
For an update to existing v1, use its compatible signing key; a newly generated
unrelated key cannot update installed APKs. With Play App Signing, distinguish
the upload key from the app signing key (see UPGRADE.md).

```bash
keytool -genkey -v -keystore intime-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias intime
```

This will prompt you for:
- Keystore password
- Key password
- Organization information

**Save the keystore file securely** - you'll need it for all future releases.

### Configure Signing Properties

Set signing credentials via one of these methods:

#### Option 1: Environment Variables (CI/CD)

```bash
export SIGNING_KEY_STORE_PATH="/path/to/intime-release.jks"
export SIGNING_KEY_STORE_PASSWORD="your_keystore_password"
export SIGNING_KEY_ALIAS="intime"
export SIGNING_KEY_PASSWORD="your_key_password"
```

#### Option 2: gradle.properties (Local Development)

Use user-level `~/.gradle/gradle.properties` outside the repository (on Windows,
`C:\Users\<user>\.gradle\gradle.properties`). Do not put passwords in the tracked
project `gradle.properties`:

```properties
SIGNING_KEY_STORE_PATH=path/to/intime-release.jks
SIGNING_KEY_STORE_PASSWORD=your_keystore_password
SIGNING_KEY_ALIAS=intime
SIGNING_KEY_PASSWORD=your_key_password
```

**⚠️ WARNING:** Do NOT commit gradle.properties with passwords to version control!

### Build Signed Production APK/AAB

Default release builds use `.dev`. For the production candidate, use explicit
parameters in PowerShell (JDK 17, SDK and user signing properties configured):

```powershell
./gradlew.bat ':app:assembleRelease' ':app:bundleRelease' '-PproductionRelease=true' '-PreleaseVersionCode=25' '-PreleaseVersionName=2.0.0-rc1' --offline --no-daemon --no-watch-fs --no-configuration-cache
```

Outputs: `app/build/outputs/apk/release/app-release.apk` and
`app/build/outputs/bundle/release/app-release.aab`. Archive the exact artifacts,
SHA-256, source commit, logs and mapping.txt together before another build.
Code 25/name 2.0.0-rc1 refer to historical local artifacts; new production builds
reject that code. The user confirmed Console maximum 25; next candidate is
26/2.0.0-rc2, with the final production version still to be agreed. Local APKs carry the Upload key;
they cannot update the differently signed Play installation.

## R6.2 / R6.7: Release Verification

The uploaded candidate must be tied to its own verification records. See
[DEVICE_TEST_RESULTS.md](DEVICE_TEST_RESULTS.md) for exact artifact hashes,
run identifiers, initial conditions and coverage limits.

| Check | Verified locally | Remaining |
| --- | --- | --- |
| Signed AAB | October 4: R8/lint, Upload key, bundletool validate and actual manifest; embedded mapping matches | Play-generated APKs/installation update; final version |
| API 24 | Runner/test support prepared | No device run: new image exceeds authorized 100 MB disk budget |
| API 31 | Upload-signed APK smoke 14/14, three reboot repeats, two natural periodic repeats (11/11), single inexact forced deep Doze (12/12) and two deliveries (14/14) | Earlier isolated reboot failure unexplained; quotas, natural Doze/OEM and full matrix open |
| API 33 | Upload-signed APK smoke 14/14, two natural periodic repeats (11/11), single inexact forced deep Doze (12/12) and two deliveries (14/14) | PowerManager idle not separately measured in two-delivery run; quotas, natural Doze/OEM, system ANR stability and Play upgrade |
| API 35 | Upload-signed APK smoke 14/14, two natural periodic repeats (11/11), single inexact forced deep Doze (12/12) and two deliveries (14/14, host GPU/Vulkan disabled); synthetic debug upgrade separately | Quotas, natural Doze/OEM, long-term system ANR stability and real production upgrade/data |
| API 36 | Upload-signed APK smoke 14/14, forced exact deep Doze, two natural periodic repeats (11/11), ordinary inexact 11/11, single inexact forced deep Doze 12/12 and two deliveries (14/14, host GPU/Vulkan disabled) | OEM inexact Doze, natural Doze/quotas, long-term system ANR stability and remaining matrix |
| WorkManager | Debug execution/KEEP; October 6 release/R8 two natural quiet repeats after process exit on API 31, 33, 35 and 36, each 11/11; earlier controlled run kept separately | Timing guarantees, other API/OEM and Play upgrade coverage open |
| 16 KB pages | Fresh AAB contains no native `.so` files | Separate device/platform verification not performed |
| CI | Configuration and local checks recorded | Successful new remote pipelines not confirmed |

Use the release-smoke runner and commands in [TESTING.md](TESTING.md), only on
the dedicated project AVDs. Do not install or clear a personal device implicitly.
The October 6 release periodic result is PASSED, 10/10, without manual job run
or live jobscheduler dumps during the wait. Installed framework bytecode showed
that textual scheduler dumps can recompute TimeController readiness; those dumps
are now limited to before/after the observation. The subsequent two-repeat run
passed 11/11 with two worker SUCCESS entries and quiet notification gaps of
21m40s from the first alarm and another 26m15s, without intervening scheduler dumps.
This does not promise a fixed 15-minute delivery time.
The API 35 extension also passed 11/11 on the unchanged APK: quiet gaps 21m14s
from the alarm and another 26m15s, two SUCCESS entries for the same worker id.
The observation was continuous, without intervening UI or scheduler dumps;
the dedicated AVD was stopped after cleanup. Ordinary Doze/OEM and other API
periodic delivery remain outside these two active-emulator checks.
API 33 subsequently passed 11/11 with quiet gaps 29m31s and another 19m15s,
three SUCCESS entries for the same worker id (the early execution did not update
the notification). The runner now parses API 33's single when and hex fields;
LOW/no ACK/null sound-vibrate/zero defaults/group suppression remain required.
The successful run used the existing Google APIs AVD with host GPU/4 GB after
a separate Google Search/Launcher ANR under software GPU/2 GB. Failed attempts
are preserved; this does not prove the system ANR is fixed. See DEVICE_TEST_RESULTS.md.
API 31 also passed 11/11, with quiet gaps 26m36s and another 15m00s,
three SUCCESS entries for the same worker id (the early execution did not update
the notification). Reboot restored the alarm; the earlier isolated reboot failure
remains unexplained. The two-repeat matrix is now verified on active dedicated
AVDs API 31/33/35/36, without dispatch or scheduling changes. Ordinary Doze/OEM,
inexact deep Doze on other/OEM devices, older APIs and Play upgrade remain open.
October 6 InexactOnly on API 36 passed 11/11: own exact appop denied, positive
alarm window +44s719ms and actual receiver/ACK notification 44.781 seconds after
the deadline. Notification ACK and restart retained denial and the new inexact
alarm. UI bootstrap passed 3/3 on host GPU/4 GB/2 CPU; earlier system ANR failures
are preserved. This confirms one ordinary fallback delivery, not Doze or quotas.
The subsequent InexactOnly + DozeOnly run passed 12/12 on the same API 36/APK:
positive window +44s864ms, own receiver/ACK notification 44.941 seconds after the
deadline, IDLE at every poll and after delivery. Power settings were restored;
ACK/restart kept denial and the new inexact alarm. UI bootstrap passed 3/3.
This confirms one forced deep Doze fallback delivery, not natural entry or quotas.
The October 6 batch extended that same scenario to existing API 35/33/31:
each passed 12/12 and UI bootstrap 3/3, with notification delays 44.913/44.574/
44.927 seconds, IDLE checks, actual receiver/ACK and a retained new inexact alarm.
The first API 33 attempt failed before Doze because the SAF filename lacked focus;
a bounded targeted tap retry fixed the runner, and the full repeat passed.
The initial failure is preserved; app code/APK are unchanged. All AVDs were stopped.
The single forced inexact delivery matrix is now confirmed on API 31/33/35/36.
The earlier October 4 periodic result uses PASSED_CONTROLLED_PLATFORM_DISPATCH, not a natural
delivery pass. The scheduler remained Ready:true; host then dispatched its ready
job with -s. The APK's WorkManager returned SUCCESS and posted the quiet repeat.
No batching threshold, interval or clock changes were made; no direct doWork call.
Do not infer a current pass from older logs or treat a packaging check as a device
check. Preserve R8 mapping for crash decoding; a renamed class alone does not
prove all runtime paths work.

## R6.3: App Icons

**Implemented October 4:** vector calendar artwork retains the existing palette,
without the DEV marker. Manifest uses mipmap icons: legacy fallback API 24–25,
adaptive API 26+ and monochrome API 33+. The fresh signed APK/AAB, packaged
resources, UI 3/3 and release smoke 14/14 passed on API 36; its launcher icon
was visually checked. See [ICON_DESIGN_GUIDE.md](ICON_DESIGN_GUIDE.md).
Themed launcher mode, other masks/OEM and legacy API 24–25 appearance remain
unverified on device. Play listing artwork is separate from these launcher XMLs.

## R6.4: Privacy Policy

The local [PRIVACY_POLICY.md](PRIVACY_POLICY.md) draft was updated October 4.
The app performs no own network uploads or cloud sync. It permits Android system
backup, and user-selected SAF providers may be cloud services. The policy states
these limits and explains that JSON exports are unencrypted.

Before release, publish matching text at a public policy URL and verify app/store
links. The currently referenced `https://valker.github.io/intime_privacy_policy.html`
was not readable through the web tool in this review; availability and consistency
remain unverified. Repository edits do not update that site or Console.
Google requires a publicly accessible privacy policy URL; see the
[User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311).

## R6.5: Play Console Declarations

The [declaration draft](google_play/PLAY_CONSOLE_DECLARATIONS.md) was reviewed
against the merged signed AAB, source flows and official documentation October 4.
It includes WorkManager permissions and the system-backup/SAF caveats.
Final Data safety answers, content forms and Play acceptance are unverified;
a file in the repository is not a submitted Console declaration.

## R6.6: Changelog and Release Notes

[CHANGELOG.md](CHANGELOG.md) describes an unreleased target version. Final date,
version and user-facing release notes require approval after the remaining checks.
Do not state automatic migration of all v1 installations or exact 15-minute
background delivery. The real-data/Play update remains R4.2.

## Release Checklist

- [x] Local Upload key configured and candidate signature verified.
- [x] Candidate AAB structure, manifest and mapping checked.
- [x] Bounded upload-signed APK smoke on API 31/33/35/36 documented.
- [x] Local privacy/declaration/changelog drafts reviewed against current behavior.
- [x] Launcher/adaptive icons replace DEV artwork (R5.2), API 36 visual check recorded.
Two prearranged inexact deliveries without leaving forced deep IDLE were checked
across existing API 31/33/35/36 on 6–7 October. API 31/33 passed 14/14 host phases
and fresh UI bootstrap 3/3 each; API 35/36 could not complete because of SystemUI
ANRs during boot/reboot. This is partial coverage, separate from earlier single
delivery passes. Both deliveries, automatic rescheduling, and subsequent ACK /
restart were verified on 31/33. Specific quota enforcement remains unverified;
PowerManager idle=true was measured on 31, but not separately on 33. No application
code or APK changed. See DEVICE_TEST_RESULTS.md for retained evidence and failures.
On 7 October, after explicit user consent, only dedicated API 36/35 AVD data
was reset. Fresh UI tests passed 3/3 each, but both release retries again failed
after seven phases: SystemUI failed to complete startup during reboot. Neither
retry reached the two-delivery check. Reset did not fix the infrastructure.
A subsequent sequential retry with host GPU and Vulkan disabled passed 14/14
on both API 36/35, with fresh UI tests 3/3 each and no detected ANR. This completes
the observed two-delivery matrix across API 31/33/35/36. It does not establish
the sole cause of earlier ANRs, long-term emulator stability, or quota enforcement.
Fresh idle-policy diagnostics then ran sequentially across all four existing
AVDs, with UI tests 3/3 each. API 35/36 had active AlarmManager idle-until in two
snapshots; API 31/33 had controller IDLE and PowerManager idle=true but no
idle-until. Observed compat quota/window settings were 7/hour and 1/9 minutes,
respectively; none of these diagnostics exhausted a quota. Earlier delivery
passes remain valid observations, but API 31/33 idle-quota coverage is unconfirmed.
An opt-in controlled maintenance cycle then restored idle-until on existing
API 31/33 without changing quotas; API 35/36 skipped the cycle because their
policy was already ready. Fresh UI tests passed 3/3 each; all four diagnostic
results were IDLE_POLICY_READY. The diagnostic restores power afterwards and
does not establish application alarm deferral or natural Doze. The next step
is integrating bounded preparation before the release delivery wait and
checking second-alarm deferral at 1/9 minutes on API 31/33. API 35/36 require
a separate scenario exhausting the observed 7/hour limit.
The bounded preparation is now integrated as opt-in DozeQuota for API 31/33.
Both passed 15/15 release phases and fresh UI tests 3/3 each. Own compat history,
restricted second alarm with positive device_idle deferral, and independent
second delivery after 539.983/540.018 seconds establish the observed 1/9-minute
quota on these forced Doze AVDs. ACK/restart and cleanup passed. API 33 first had
two retained infrastructure failures (SystemUI, then Google launcher/search ANR);
the complete retry passed with 4 CPU instead of 2, unchanged 4 GB/host GPU/Vulkan
disabled, without data reset. This does not establish lasting emulator stability.
API 35/36 quota saturation completed on 7–8 October with opt-in DozeQuotaSeven:
fresh UI 3/3 and release/R8 21/21 each, using the same signed candidate APK.
Seven own history entries inside the measured 7/hour window and positive
device_idle deferral matching oldest-entry expiry preceded the eighth dispatch.
Eight own package dispatches and eight own receiver callbacks were verified
independently of retained history length (8 on API 35, 7 on API 36).
The first Notification.when to eighth callback gaps were 3599.998/3600.002 seconds
(2-second tolerance). API 35 used the natural worker summary's contentIntent,
then eighth-task details ACK; API 36 used the direct notification ACK action.
Future inexact scheduling, exact denial, process restart and cleanup passed.
The runner now waits for the real list after contentIntent, dismisses the ordinary
dedicated-AVD keyguard without changing lock settings, and bounds list scrolling.
Retained failed/interrupted attempts are not promoted to PASS; the nighttime
host polling gap required a complete retry. The final batch temporarily requested
system wakefulness and released that request in finally without changing power settings.
Details and the checked hourly-quota-confirmed-matrix.json are in DEVICE_TEST_RESULTS.md.
Natural Doze, physical OEM, Play-compatible upgrade and disk-limited API 24 remain open.
On 8 October a read-only natural-deep-Doze preflight covered dedicated API
31/33/35/36 with fresh UI 3/3 each (API 36 after a retained focus-wait failure).
Every controller requires a motion sensor but reports null; deep/light are
enabled on 31/33 and disabled on 35/36. NATURAL_DEEP_UNAVAILABLE is an environment
assessment, not an application test pass. No natural-entry or delivery coverage
is claimed, no power/sensor settings were changed, and owned AVDs were stopped.
Nine parser fixtures passed. Physical-device prerequisites and candidate
verification require separately agreed personal-device use; the procedure is
in TESTING.md. Natural light idle on 31/33 remains a separate unchecked scenario.
The agreed read-only phone diagnostic subsequently completed on RMX3636/API 35:
deep/light enabled, significant-motion sensor present, force idle false,
controller ACTIVE with USB charging. Installed Play v1 is 1.1.12/code 25;
no app/data/settings were changed. Natural-entry, delivery and candidate APK
identity remain unvalidated. This phone snapshot does not verify candidate v2.
Existing images were reused; owned emulators were stopped after cleanup.

API candidate27 update (9 October): user explicitly authorized commit and
possible automatic review. Upload-only commit succeeded; Play APK27/rc3 was
downloaded, metadata/App signing verified, DEX matched archived AAB exactly.
Post-commit tracks retain production25, beta26 and the existing internal draft;
27 is not assigned to any track. Fresh API35 UI3/3 and Play25→27 upgrade9/9
phases passed with synthetic tasks; cleanup completed and owned AVD stopped.
Personal phone/data were untouched. Real-data/OEM/natural Doze remain open.
Previous HTTP400/404 and approval rejection are retained as historical evidence,
not current blockers. See PLAY_API.md/DEVICE_TEST_RESULTS.md. Code27 is used;
do not upload a different payload under it. Earlier host auth13/13 + candidate8/8.

- [ ] Remaining device coverage and release worker verified (R4.3/R5.3).
- [x] R4.2: Play-compatible25→27 upgrade verified on a separate logical copy
  of17 real tasks from the user-provided JSON, API35,9/9 phases. All original
  fields/IDs/dates preserved; source unchanged, cleanup complete. Fresh UI3/3
  and host4/4. Original phone SQLite/WAL/preferences and delivery/OEM/reboot
  are outside this coverage; see UPGRADE.md/DEVICE_TEST_RESULTS.md.
- [ ] Public privacy page and actual Console declarations verified (R5.4).
- [ ] Final production version and unused Play versionCode confirmed (R5.1).
- [ ] Fresh final artifacts, mapping and hashes archived after all changes.
- [ ] Final release notes, tag and staged rollout prepared (R5.5).

No publication, tag, Console submission or remote pipeline run is implied by
these checked local items.
