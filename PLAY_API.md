# Доступ к Google Play API для InTime

Обновлено 9 октября 2026 года. Подготовлена локальная проверка доступа;
Ключ предоставлен локальным путём; OAuth и чтение метаданных Play APK 26
подтверждены. Загрузка AAB и публикация через API ещё не проверялись.
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
   Администратор, права рабочей публикации и финансовые права не требуются.
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
upgrade. Upload/commit/publish не реализованы и в этом этапе не выполнялись.
После успешной проверки отдельно добавляем загрузку тестового кандидата и
скачивание Play APK, сохраняя публикацию рабочей версии отдельным действием.
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
