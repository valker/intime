# Play Console Declarations

Source/document review refreshed October11,2026; artifact checks refer to
the archived signed candidate27/2.0.0-rc3 and DEVICE_TEST_RESULTS.md.
The current Console declarations were reviewed using the user-provided CSV,
screenshots and confirmations on October11. Evidence and limits are below;
this is not approval of a future release. Review the final artifact and current Console
questions before submission. App operation is offline; system backup and
user-selected document providers may transfer data (see PRIVACY_POLICY.md).

## Manifest Permissions

Declared permissions:

| Permission | Actual app use (not a submitted Console answer) | Reason |
| --- | --- | --- |
| `POST_NOTIFICATIONS` | Required for app functionality | Intime sends reminder notifications when a task becomes due or overdue. |
| `SCHEDULE_EXACT_ALARM` | Required for app functionality | Intime is a reminder app. Exact alarms are needed so user-created reminders fire at the selected time. |
| `RECEIVE_BOOT_COMPLETED` | Required for app functionality | Intime reschedules local reminders after the device restarts. |

The merged AAB also declares WAKE_LOCK, ACCESS_NETWORK_STATE and
FOREGROUND_SERVICE from WorkManager, plus the application-specific AndroidX
signature permission DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION. The current
worker does not use foreground execution. INTERNET is absent. ACCESS_NETWORK_STATE
alone does not grant network communication. Audit the merged manifest rather
than only the source manifest.

Not declared:

| Permission/API | Play Console answer |
| --- | --- |
| `USE_EXACT_ALARM` | Not used. The app uses `SCHEDULE_EXACT_ALARM` and exposes system settings when exact alarms are unavailable. |
| Calendar permissions | Not used. The app does not read or write the user's calendar. |
| Location permissions | Not used. |
| Contacts permissions | Not used. |
| Photos/videos/audio permissions | Not used. |
| Advertising ID | Not used. |

## Exact Alarms

Сверка11 октября: Console показывает обработанную декларацию
`android.permission.USE_EXACT_ALARM` (изменение17 июня2024). Проверка
пользовательского Play25/1.1.12 через aapt подтверждает это разрешение;
SCHEDULE_EXACT_ALARM в25 ограничено maxSdkVersion32. Кандидат27/rc3 имеет
только SCHEDULE_EXACT_ALARM без этого ограничения. Различие связано с разными
артефактами, не доказывает ошибку Console. Последующий скриншот формы подтверждает
выбранное обоснование «Установка будильников»; «Календарь» не выбран.
Декларацию не удаляем/не заменяем автоматически при сохранённой
старой Play-версии; при финальном выпуске сверить артефакты активных треков и
требования Console. Проверка APK не устанавливала приложение на телефон.
Выводы aapt: `.test-tools/runs/20261011-console-permissions-review`.
Различия разрешений описаны в
[Android alarm documentation](https://developer.android.com/develop/background-work/services/alarms).

Runtime use case of candidate27: **Local task reminders using SCHEDULE_EXACT_ALARM**.
This is not a claim of eligibility for USE_EXACT_ALARM: Google restricts that
permission to qualifying core alarm/timer/calendar functionality. The screenshot
records the existing answer for Play25; it does not independently prove policy
eligibility. The candidate removes USE_EXACT_ALARM. Before the final rollout,
review retained old artifacts and the actual declaration requested by Console.
[Google Play exact-alarm permission policy](https://support.google.com/googleplay/android-developer/answer/16558241?hl=en).

Suggested declaration text:

```text
Intime is an offline reminder app. Users create tasks with reminder intervals,
and the app schedules a local alarm for the next due task. Exact alarms are
needed so reminders appear at the intended time. If exact alarms are unavailable,
the app shows a settings action and falls back to less precise scheduling.
```

Implementation notes for reviewer context:

- The app checks exact-alarm availability before scheduling exact alarms.
- The Settings screen explains exact-alarm status and opens the Android system
  exact-alarm settings page.
- Alarms are scheduled locally with `AlarmManager`; there is no server component.

## Notifications

Use case: **Task/reminder notifications**

Suggested declaration text:

```text
Intime uses notifications only to remind users about their own local tasks.
Notifications may include an acknowledge action for a newly overdue task. The app
does not use notifications for ads, promotions, account messages, or marketing.
```

Android 13+ runtime permission behavior:

- The app requests `POST_NOTIFICATIONS` at runtime.
- If notification permission is denied, the app shows an in-app warning.
- Denied notification permission does not mark tasks as notified.

## Boot Completed

Use case: **Restore reminders after restart**

Suggested declaration text:

```text
Intime listens for device boot completion to reschedule user-created reminders
and may show a local summary of tasks missed while the device was off. It does
not request foreground execution or perform network work in this receiver.
```

## Data Safety

Draft answers for app-operated data flows; final review remains open:

| Question | Answer |
| --- | --- |
| Does the app collect or share any required user data types? | No |
| Is all user data collected by the app encrypted in transit? | The app has no network upload flow; review applicability in the current form. Do not claim encryption for plain JSON exports. |
| Can users request that data is deleted? | Not applicable for server-side deletion: data is stored locally and can be deleted in the app or by uninstalling/clearing app data. |
| Does the app share data with third parties? | No |
| Does the app use analytics, advertising, or tracking SDKs? | No |

Privacy policy content: `PRIVACY_POLICY.md`. Before release, publish the matching
text at an accessible public URL and check the app/store links. The resources
currently contain https://valker.github.io/intime_privacy_policy.html; its
availability/content was checked by direct HTTP GET on October11 (HTTP200).
The published text is old: it describes developer log collection, cookies and
third-party services, whereas the current application has no own upload flow
or INTERNET permission. After explicit user approval the reviewed replacement
`google_play/intime_privacy_policy.html` was published in site commit c69e859;
Pages deployment38125867804 succeeded. The permanent URL returned HTTP200 and
the full page text matched the prepared HTML after CRLF/LF normalization.
The supplied Data safety export was reviewed October11 (see below); other
Console answers and submission status remain unverified. The previous policy was historical
text, not evidence that the current app performs those data flows.

Important consistency note: task titles, reminder intervals, and backup files are
user data stored locally on the device. Local processing alone is not data
collection under the linked Data safety definitions. The manifest permits
Android system backup and SAF can use a cloud provider selected by the user;
the policy explains both. Confirm applicable disclosures for the final build,
its dependencies and these transfers in the current Console form.

Official references rechecked October11,2026:
[Data safety definitions](https://support.google.com/googleplay/android-developer/answer/10787469),
[User Data/privacy policy requirements](https://support.google.com/googleplay/android-developer/answer/10144311),
[Android system backup](https://developer.android.com/identity/data/autobackup).
The repository review does not establish compliance or Play approval.

## Сверка владельцем аккаунта: R5.4

Ожидаемое поведение ниже проверено по исходникам и сохранённому merged manifest
кандидата27/2.0.0-rc3. Data safety CSV предоставлен и проверен11 октября;
Остальные подтверждения пользователя и скриншоты перечислены в таблице. Названия
разделов могут отличаться по языку и обновлениям интерфейса; ненужные формы
не создавайте только из-за списка разрешений Android.

Откройте InTime в Play Console → Правила и программы → Контент приложения.
Проверьте обе вкладки: «Требуются действия» и «Обработано».

| Раздел | С чем сверить | Фактический статус |
| --- | --- | --- |
| Политика конфиденциальности | URL https://valker.github.io/intime_privacy_policy.html показывает согласованный текст | Страница опубликована/проверена; пользователь подтвердил совпадение URL в Console |
| Реклама | Приложение не содержит рекламы и рекламных SDK | Пользователь подтвердил ответ «Нет» в Console |
| Доступ к приложению | Все функции доступны без регистрации, пароля и подписки | Пользователь подтвердил ответ «Все функции доступны напрямую» в Console |
| Рекламный идентификатор | В текущем APK нет AD_ID и рекламного SDK | Скриншот Console: рекламный идентификатор не используется; соответствует текущей сборке |
| Выполнение операций в точное время | Play25 содержит USE_EXACT_ALARM, кандидат27 использует SCHEDULE_EXACT_ALARM | Скриншот формы: выбрана «Установка будильников»; ответ зафиксирован для прежней версии, пригодность USE не утверждается |
| Безопасность данных | Нет собственных сетевых загрузок/аналитики; Room локально, Android backup разрешён, JSON/SAF выбирает пользователь | CSV: главный ответ false согласуется; статус отправки неизвестен |
| Целевая аудитория | Фактические выбранные возрастные группы должен подтвердить владелец; исходники не определяют аудиторию магазина | Скриншот пользователя: 13–15, 16–17, от18 лет; «Ваше приложение не привлекает детей» |
| Возрастные ограничения | Категория Productivity сама по себе не является возрастным рейтингом | На скриншоте присвоенные рейтинги, включая ESRB Everyone, PEGI3, USK0; дата изменения6 января2019 |
| Приложения для здоровья | Текущее назначение — универсальные периодические задачи, без специализированных функций здоровья | Скриншот Console: функций, связанных со здоровьем, нет |
| Финансовые функции | В приложении нет финансовых сервисов | Скриншот Console: «В моем приложении нет финансовых функций» |
| Приложения государственных учреждений | Приложение не является сервисом государственных органов | Скриншот Console: приложение не связано с органами государственной власти |
| Статус заполнения | Проверить вкладку «Требуются действия» | Скриншот Console: «Все декларации заполнены», список пуст |

Для проверки Data safety без изменений скачайте существующие ответы:
«Безопасность данных» → «Начать»/«Настроить» → «Экспортировать в виде CSV-файла»
справа вверху. Сохраните CSV локально и передайте путь агенту. Это экспорт,
а не импорт или отправка декларации. Не используйте шаблон вместо текущих
ответов: он не подтверждает настройки аккаунта.

Для остальных разделов достаточно сообщить текущий URL политики, ответ о рекламе,
статус доступа, выбранные возрастные группы, полученный рейтинг и перечень
пунктов «Требуются действия». Не включайте пароли и данные сервисного аккаунта.
Менять/отправлять формы следует после сверки конкретных расхождений.

Скриншот пользователя подтверждает статус вкладки «Требуются действия»:
«Все декларации заполнены», список пуст. Это отсутствие незаполненных деклараций
на показанном экране, а не подтверждение правильности каждого ответа или
одобрения будущего релиза. Раскрытые ответы health/finance/government и
advertising ID затем также проверены по скриншотам, см. таблицу. Обоснование
USE_EXACT_ALARM также прочитано: «Установка будильников». Текущая сверка R5.4
завершена в объёме этих подтверждений. Формы не менялись; принятие финального
релиза, полный повтор анкеты рейтингов и соответствие старой версии требованиям
USE_EXACT_ALARM не выводятся из статуса «Обработано». При финальном выпуске
повторно сверить активные артефакты и необходимые Console декларации.

Официальные инструкции:
[Контент приложения](https://support.google.com/googleplay/android-developer/answer/9859455?hl=ru),
[экспорт Data safety CSV](https://support.google.com/googleplay/android-developer/answer/10787469?hl=ru).
В Publisher API есть [applications.dataSafety](https://developers.google.com/android-publisher/api-ref/rest/v3/applications/dataSafety),
но это POST для записи CSV, а не метод чтения существующих ответов. На этом
этапе его не вызываем; никакие декларации/выпуски через API не менялись.

### Результат проверки CSV11 октября

Пользователь предоставил `C:/Users/Valentin/Downloads/data_safety_export.csv`.
Проверены все782 строки и пять столбцов. Заполнена только строка
`PSL_DATA_COLLECTION_COLLECTS_PERSONAL_DATA`: `false`; остальные781 значение
пусты. Ответ «Нет» согласуется с проверенной текущей реализацией: локальное
хранение/обработка, нет собственных upload/analytics flows и INTERNET.
Выбранный пользователем SAF-provider и Android backup отдельно описаны в policy;
не называем сохранение локальных задач отсутствием пользовательских данных.

Пустые поля не трактуются как false, ошибочные ответы или подтверждённые ответы
«Нет»: вопросы могут быть условными. В том числе `PSL_ACM_NONE` не заполнен;
если Console показывает обязательный вопрос о способах создания аккаунта,
для текущего приложения соответствует вариант «В приложении нельзя создать
аккаунт». Если вопрос скрыт, только из-за пустой CSV-строки его не исправляем.

Не обнаружено заполненных ответов, противоречащих текущему поведению.
CSV не содержит package/version, статуса отправки/одобрения, URL policy,
рекламы, доступа, возрастных групп и рейтинга; эти пункты не закрыты.
Исходный CSV не изменялся, импорт/POST/отправка формы не выполнялись.
Сводка и SHA исходника: `.test-tools/runs/20261011-data-safety-csv-review/summary.json`.

## Ads

Answer: **No ads**

The app does not contain ad SDKs and does not use Advertising ID.

## App Access

Answer: **All functionality is available without special access**

The app does not require accounts, credentials, subscriptions, region-specific
access, organization membership, or reviewer login.

## Content Rating

Recommended category: **Utility / Productivity**

Пользователь предоставил скриншот раскрытого раздела Console: видны присвоенные
рейтинги, включая ESRB Everyone, PEGI3 и USK0; дата последнего изменения
6 января2019 года. Зафиксировано наличие рейтингов, но сами ответы анкеты
и соответствие её ответов будущему финальному артефакту этим экраном не проверены.

Suggested answers:

- No violence, fear, sexual content, gambling, controlled substances, or user
  generated public content.
- No location sharing.
- No purchases.
- No browser or unrestricted web access.

## Target Audience

Recommended answer: **General audience / not designed for children**

The app is a productivity utility for personal task reminders and is not designed
or marketed specifically for children.

Фактические настройки Console подтверждены скриншотом пользователя: целевые
группы13–15,16–17 и от18 лет, указано «Ваше приложение не привлекает детей».
Группы младше13 на этом экране не перечислены. Это выбранная целевая аудитория,
а не полученный возрастной рейтинг и не подтверждение одобрения Google.

## Store Listing Privacy Notes

Short privacy summary for the listing:

```text
Intime works offline without accounts, ads, analytics or its own cloud sync.
Android system backup may include app data. You choose the storage provider
when importing or exporting tasks.
```

