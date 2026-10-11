# Доступ к Google Play API для InTime

## Выпуск 28 отправлен на проверку — текущий статус

11 октября, 18:26:23 UTC: после настройки прав владельцем validate/commit
успешно выполнены в новом edit. Production: 28/2.0.0, `completed`, 100%
(без userFraction), RU/EN заметки сохранены. Свежая сверка подтвердила хеш AAB,
параметры/заметки production и неизменность остальных треков. Проверочный edit
удалён. Lifecycle API сообщает 28 **IN_REVIEW**, 25 **PUBLISHED**;
сохранение полного rollout не означает, что проверка Google уже завершена.
Отчёт `.test-tools/runs/20261011-play28-production100-retry/summary.json`.
Блокер прав устранён; нижеследующая запись 403 описывает предыдущую попытку.

## Текущее состояние: финальный AAB 28 сохранён

11 октября владелец разрешил production 28/2.0.0 сразу для 100% пользователей.
PUT production принят в собственном edit, но POST edits.validate вернул HTTP 403
PERMISSION_DENIED («The caller does not have permission»). Commit не вызывался,
несохранённый edit удалён; production остаётся 25/PUBLISHED, остальные треки
не изменены, AAB 28 сохранён без назначения на трек. Отчёты:
`.test-tools/runs/20261011-play28-production100/`.
Проверить право «Выпуск рабочей версии, исключение устройств и использование
функции подписания приложений в Google Play» у
`intime-play-api@intime-play-api.iam.gserviceaccount.com` только для InTime.
Ранее описанные права тестирования не разрешают рабочую публикацию;
администратор и права других приложений для этой операции не нужны.
Разрешение владельца на rollout 100% уже получено; после настройки прав
повторить операцию в новом edit с новой сверкой состояния.

11 октября 2026, 14:20:53 UTC: по явному разрешению пользователя выполнены
сверка свободности кода, upload точного AAB 28/2.0.0, validate и commit.
Новое чтение bundles подтвердило SHA-256
`9FF56ACCAF07E4AD4BC10931495B59250CDF0E9758E02B4A071D5AF600962199`.
Полные объекты tracks до/после совпали: production 25, beta 26, internal 23
и прежний пустой draft. Код 28 занят, но не назначен на трек; rollout не выполнялся.
Проверочный edit удалён. Отчёт `.test-tools/runs/20261011-play28-upload/summary.json`.
Одноразовый helper использует существующий Node без новых зависимостей и
не выводит ключ/OAuth token. Старый prepare-play-candidate.ps1 остаётся привязан
к кандидату 27; он не является командой подготовки или публикации 28.
Commit использовал защиту `ERROR_IF_IN_REVIEW` из
[официального API](https://developers.google.com/android-publisher/api-ref/rest/v3/edits/commit).
Ниже описаны предыдущие сверки и настройка доступа; финальный rollout требует
отдельного согласования.

11 октября выполнена свежая сверка версий для финального выпуска 2.0.0:
GET bundles/apks/tracks во временном edit, без upload/commit/track update.
Максимум сохранённых кодов — 27; выбран 28, отсутствующий в снимке. Production 25,
beta 26, internal 23 и прежний пустой draft сохранены; 27 не назначен на трек.
Свой edit удалён. Код не резервируется: повторить сверку перед upload.
Отчёт `.test-tools/runs/20261011-final-version-preflight/summary.json`.

Обновлено 11 октября 2026 года. Ключ предоставлен локальным путём;
OAuth, чтение метаданных и загрузка AAB 27 без назначения на трек подтверждены.
Публикация выпуска через API ещё не выполнялась.
Это credentials Google Cloud/Play, отдельные от Android Upload/App signing ключей.
`vpe_soft.jks` не является ключом авторизации Google API.

## Что уже подготовлено

- `scripts/test-play-api.ps1` — запуск в Windows PowerShell 5.1, отчёты под
  `.test-tools/runs`, существующий встроенный Node.js 24 без установки библиотек.
- `scripts/play-api.mjs` — стандартная криптобиблиотека Node создаёт RS256 JWT,
  выполняются только OAuth POST и `generatedapks.list` GET точного production
  package `com.vpe_soft.intime.intime`; по умолчанию проверяется уже загруженная 26.
- JSON-ключ должен находиться вне репозитория (проверяется разрешённый реальный
  путь), токены/ключи не записываются в stdout, отчёт или командную строку.
  Адреса Google фиксированы, HTTP redirects отключены; серверные тела ошибок
  не включаются в отчёт. OAuth scope androidpublisher не ограничивает отдельные
  действия: реальные права ограничиваются настройками аккаунта в Play Console.
- Проверяется наличие одной группы APK с подтверждённым App signing SHA-256
  `4a37905063f6896ec53f3aee47757c0541ce8c5094d12f9bfb21a7348f2ef17c`.
  Отдельно выводится наличие universal APK; метаданные не заменяют apksigner
  verify скачанного файла. Скрипт не скачивает APK и не создаёт edits/выпуски.

## Настройка владельцем аккаунта

1. Открыть [Google Cloud Console](https://console.cloud.google.com/), выбрать
   существующий проект или создать `intime-play-api` через переключатель проектов.
2. В выбранном проекте открыть «API и сервисы → Библиотека», найти
   **Google Play Android Developer API** и включить его. Поиск также может
   показывать название Google Play Developer API.
3. Открыть «IAM и администрирование → Сервисные аккаунты → Создать сервисный
   аккаунт». Имя: `intime-play-api`. Скопировать сформированный email вида
   `intime-play-api@PROJECT_ID.iam.gserviceaccount.com`. Для этой интеграции
   роли Editor/Owner на ресурсы Cloud сервисному аккаунту не требуются;
   необязательные шаги выдачи Cloud-ролей/доступа другим пользователям пропустить.
   [Официальная настройка API](https://developers.google.com/android-publisher/getting_started).
4. В Play Console открыть «Пользователи и разрешения → Пригласить новых
   пользователей», вставить этот email. На вкладке разрешений приложений добавить
   только **InTime! — periodic tasks**, package `com.vpe_soft.intime.intime`.
   Первоначально предоставить «Просмотр информации о приложении (только чтение)».
   Для будущей загрузки AAB потребуется отдельно «Выпуск версий для тестирования
   приложения» — это право также разрешает выпуск тестовых версий, не только
   загрузку. Сейчас оно не нужно для проверки локальной обвязки; достаточность
   права чтения для generatedapks.list будет подтверждена реальным вызовом.
   Для первоначальных read/upload шагов администратор, права рабочей публикации
   и финансовые права не требовались. Для разрешённого 11 октября production
   rollout требуется дополнительно право выпуска рабочей версии, см. статус выше.
   Сохранить/отправить приглашение.
   [Описание прав](https://support.google.com/googleplay/android-developer/answer/9844686?hl=ru).
5. Вернуться в Cloud к созданному сервисному аккаунту. Открыть «Ключи →
   Добавить ключ → Создать новый ключ», выбрать **JSON** и скачать файл.
   [Создание JSON-ключа](https://docs.cloud.google.com/iam/docs/keys-create-delete).
   Если создание запрещено политикой организации, сообщить текст ограничения;
   не ослаблять организационные политики автоматически. Возможен отдельный
   вариант OAuth или федерации, текущий runner рассчитан на JSON service account.
6. Создать локальную папку `C:\Users\Valentin\.config\intime` и переместить
   скачанный файл под именем `play-service-account.json`. Не оставлять его в
   репозитории, не прикладывать к чату и не вставлять содержимое в сообщения.
   Агенту достаточно сообщения «готово» или пути, если файл расположен иначе.

Сервисный аккаунт не имеет личного почтового ящика; в Play приглашении используется
его email. Привязка developer account к Cloud project отдельным старым меню
API Access больше не обязательна; настройка производится через Cloud API и
права пользователя Play согласно инструкции Google.

## Проверка после настройки

Предоставленный файл: `C:\Users\Valentin\Downloads\intime-play-api-b725a9a8d7bc.json`.
Сохранён только его путь в ignored `.test-tools/play-api-local.json`; содержимое
ключа не копируется в проект. Если `KeyFile` не передан явно, wrapper использует
эту локальную конфигурацию, затем (при отсутствии конфигурации) стандартный путь
профиля. Явный параметр имеет приоритет. При перемещении файла обновить путь.

Первый реальный OAuth обмен 9 октября прошёл, GET метаданных APK 26 дал PLAY_HTTP_403.
Повторная безопасная диагностика классифицировала ответ как PERMISSION_DENIED,
без сохранения исходного тела/ключа/token. Email сервисного аккаунта:
`intime-play-api@intime-play-api.iam.gserviceaccount.com`.
После исправления приглашения пользователем Play вернул HTTP 200. Обнаружена
неверная привязка сравнения SHA-256 к записи без разделителей: настоящий API
выдал 32 байта через двоеточия. Добавлена строгая нормализация двух точных форм,
публичный сертификат совпал с подтверждённым App signing key. Полный local Node
13/13 прошёл; штатный реальный повтор PASSED, universalApkAvailable=true.
Отчёт: `.test-tools/runs/20261009-135341-play-api-498fd865/summary.json`.
Это подтверждает только чтение данной версии, не права upload/commit/publish.
Первоначальные отказы сохранены. Ранние отчёты:
`.test-tools/runs/20261009-134328-play-api-07ed0f5d/`.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-play-api.ps1
```

При другом пути:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-play-api.ps1 -KeyFile 'C:/путь/вне/проекта/play-service-account.json' -VersionCode 26
```

`NodePath` можно переопределить существующей установкой Node 24. Автоматическая
установка зависимостей отсутствует. OAuth token остаётся в памяти процесса и
не кешируется. Два HTTPS-запроса: token exchange и получение метаданных APK.
Описание endpoint: [generatedapks.list](https://developers.google.com/android-publisher/api-ref/rest/v3/generatedapks/list),
протокол: [OAuth service account](https://developers.google.com/identity/protocols/oauth2/service-account).

Типичные коды отказа:

| Код | Дальнейшее действие |
| --- | --- |
| CREDENTIAL_FILE_MISSING_OR_UNREADABLE | Уточнить путь и доступ к локальному файлу |
| CREDENTIALS_MUST_BE_OUTSIDE_REPOSITORY | Переместить credentials вне C:\Projects\intime |
| OAUTH_HTTP_400/401 | Проверить ключ, сервисный аккаунт и часы Windows; не отправлять ключ в чат |
| PLAY_HTTP_403 | Проверить включённый API, приглашение, права именно на InTime; учесть применение изменённых разрешений |
| PLAY_HTTP_404 | Проверить наличие загруженного AAB с указанным code; эта ошибка не доказывает доступ к отсутствующей версии |
| NETWORK_OR_TIMEOUT | Проверить сеть/доступ к двум фиксированным Google endpoints |
| EXPECTED_APP_SIGNING_CERTIFICATE_MISSING_OR_AMBIGUOUS | Проверить signing metadata; не принимать Upload key как App signing |

Успешное чтение не подтверждает право загрузки/публикации, получение APK или
upgrade. На этапе проверки доступа upload/commit/publish не выполнялись.
Результаты следующего этапа загрузки кандидата приведены ниже; публикация
рабочей версии остаётся отдельным действием.
Отозвать доступ можно удалением пользователя в Play и ключа в Cloud.

## Проверки локальной интеграции

```powershell
& 'C:/Users/Valentin/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node.exe' scripts/play-api.test.mjs
```

`node:test` запускается в одном процессе, без дочерних процессов. Все 13 тестов
имеют подробные русские комментарии; временные RSA/EC ключи генерируются только
в памяти. Реальные credentials, Google API, телефон, эмуляторы и базы не читаются.
Тесты проверяют RS256/claims, фиксированные HTTP методы/адреса, секреты в summary,
ошибки credentials/token/network/permissions, параметры code и signing metadata.
Проверка пути является синтетической; реальную настройку пользователя она не заменяет.

Свежий результат: `.test-tools/runs/20261009-124603-play-api-setup/` (фактический
каталог также указан в DEVICE_TEST_RESULTS.md). Первое стандартное `node --test`
остановилось на spawn EPERM до выполнения тестов. Первый single-process запуск:
11/12, неверно сформированный внешний путь в тестовой фикстуре исправлен (тестовый
root имел завершающий разделитель); свежий полный повтор 12/12, без пропусков.
Windows PS runner без ключа дал ожидаемый FAILED/CREDENTIAL_FILE_MISSING_OR_UNREADABLE
до обращения к сети. Последующие 403 и отказ сравнения сертификата сохранены;
после исправлений новые локальные 13/13 и настоящий API PASSED подтверждены
в DEVICE_TEST_RESULTS.md. Скачивание APK и его apksigner проверка в этот шаг
не входили.

## Загрузка кандидата 27 — 9 октября 2026

Добавлены `scripts/prepare-play-candidate.ps1`, `.mjs` и `.test.mjs`.
Они работают только с архивным AAB 27 / 2.0.0-rc3, SHA256
`b8f0a7e09e1ae4e1742972df769a3134c654a0943890e11b4f78234d07c62fb9`.
Перед upload проверяется локальный hash, после — versionCode и hash Google.
Существующий AAB принимается только при точном совпадении; upload не имеет
автоматических повторов. Checkpoint фиксирует edit ID и факт загрузки.
Скрипт не изменяет треки, не вызывает commit и не публикует выпуск.
Это узкий инструмент для данного архивного кандидата, не общий release runner.

Первый реальный upload прошёл, Google подтвердил payload. Edit:
`12907835182854021575`, expiryTimeSeconds=`1791550902`.
Отчёт: `.test-tools/runs/20261009-140141-play27-37e5577e/summary.json`.
Итоговый FAILED относится к последующему GET generatedApks (404), а не к upload.
Повторы только download также дали 404; последний:
`.test-tools/runs/20261009-140914-play27-3ed3830a/summary.json`.
На момент этих отказов APK не был получен. После разрешения пользователя
сохранение и download завершились успешно, см. продолжение ниже.
Версию 27 следует считать использованной; не пересобирать другой payload с этим code.

Read-only проверка собственного edit: production=25 completed, beta=26 completed,
alpha пустой, internal содержит прежний пустой draft и completed=23,
пользовательский трек `Intime! alfa version` пустой. Никакой track update не выполнялся.
Проверка `edits.validate` прошла. Контролируемое сохранение только неназначенного
AAB с `changesNotSentForReview=true` и `ERROR_IF_IN_REVIEW` вернуло HTTP 400:
`Changes are sent for review automatically. The query parameter changesNotSentForReview must not be set.`
Отчёты `validation.json`, `save-upload.json`, `commit-error.json` находятся рядом
с исходным upload summary. На этом шаге commit не был выполнен. Попытка запустить сохранение без
первого параметра отклонена автоматической проверкой разрешений до запуска процесса:
возможна автоматическая отправка на рассмотрение. Пользователь затем явно
разрешил этот побочный эффект. Снятие параметра не является гарантией отсутствия review.
Одноразовый подготовленный `save-upload.mjs` в каталоге upload повторно сверяет
треки и AAB, требует validate и отказывает при изменениях на рассмотрении;
не назначает 27 на трек. После разрешения он выполнен успешно; повторять commit
этого edit не следует.
Изменения Console могут инвалидировать edit; тогда сначала заново сверить состояние,
не повторять загрузку вслепую и не затрагивать существующий internal draft.

После разрешённого сохранения готовность APK проверяется отдельно:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/prepare-play-candidate.ps1 -Mode download
```

Этот режим не создаёт edit и не повторяет upload. APK ограничен 10 МиБ до записи,
redirect запрещён; API metadata требует известный App signing certificate.
После получения runner запускает `verify-play-apk.ps1` (code/name, подпись,
non-debuggable). Для доказательства происхождения дополнительно сравнить DEX
с архивным AAB, затем проверять 25→27 только на выделенном API35 AVD.
Телефон и пользовательские базы не использовались, SDK/AVD не скачивались.

Свежие host-проверки: auth 13/13 и candidate 8/8, без пропусков:
`.test-tools/runs/20261009-140119-play27-api-host/`.
Candidate-тесты проверяют точный payload, повторное использование, отказы HTTP,
фиксацию успешного upload при ошибочном ответе, выбор подписи, URL download,
лимит размера и ZIP magic. Это mock API, не покрытие реального commit или
apksigner/upgrade. Все новые тесты имеют подробные русские комментарии.
Android runtime в этом этапе не менялся; JVM/device suite не перезапускались.

### Продолжение после разрешения пользователя

9 октября пользователь явно разрешил сохранение и возможный review. После
повторной сверки треков, SHA256 AAB и validate commit с
`changesInReviewBehavior=ERROR_IF_IN_REVIEW` прошёл: UPLOAD_SAVED/committed=true.
Итог: `.test-tools/runs/20261009-140141-play27-37e5577e/save-upload.json`.
Первоначальный upload summary сохранён как исторический checkpoint, его
committed=false не описывает последующее сохранение. Ошибка параметра сохранена
в commit-error.json. API не предоставляет здесь доказательства отсутствия review.

Download: `.test-tools/runs/20261009-151639-play27-af6bca99/summary.json`, APK_VERIFIED,
3 150 384 байта. APK SHA256:
`7662d7d4321fecde08437880ba18113899b447eae061939b9092d29fc7f7633c`.
Отдельная проверка подписи/версии/non-debuggable:
`.test-tools/runs/20261009-151642-play-apk-e55b1fb2/summary.json`, PASSED.
DEX provenance в каталоге download: PASSED; classes.dex APK и AAB совпал побайтно,
SHA256 `1385bfa68ca3649610eb5232f211fab1e553daaf5ae979c09226140d9d660388`.
Сравнение DEX не является сравнением ресурсов или всего APK.

Read-only snapshot после commit (`tracks-after.json` в каталоге download)
подтверждает прежние production25/beta26/internal23 + пустой draft; alpha и
пользовательский трек пустые. 27 не назначен ни на один трек, track update
не выполнялся. Snapshot создан в отдельном временном edit без commit.

После получения APK выполнены свежие API35 UI3/3 и Play25→27 upgrade9/9 фаз,
cleanup=true, собственный AVD остановлен. Результаты:
`.test-tools/runs/20261009-151741-94be13f8/summary.json` и
`.test-tools/runs/20261009-152119-play-upgrade-147be141/summary.json`.
Это синтетические задачи старого UI; отдельная реальная пользовательская база
не проверялась. Телефон не использовался. Подробное покрытие и ограничения
приведены в UPGRADE.md/DEVICE_TEST_RESULTS.md; на этом этапе R4.2 оставался открыт.
Позднее пользователь предоставил JSON25 с17 задачами; отдельный логический
copy-upgrade на API35 прошёл9/9 (UI3/3,host4/4). R4.2 закрыт в объёме копии
задач,исходные SQLite/WAL/preferences телефона не проверялись. Отчёт:
`.test-tools/runs/20261009-154653-real-copy-upgrade-638aba16/summary.json`.
