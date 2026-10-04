# InTime launcher icons

Обновлено 4 октября 2026. Реализованы векторные launcher icons без DEV-метки.
Сохранены знак календаря и палитра прежней иконки: #595959 на #FFF8FF.
Новые иллюстрации, PNG по плотностям и сторонние графические зависимости не нужны.

## Ресурсы

| Ресурс | Назначение |
| --- | --- |
| drawable/ic_launcher_foreground.xml | Календарь на прозрачном холсте 108×108 dp; также monochrome alpha mask |
| mipmap-anydpi/ic_launcher.xml | Векторный fallback для API 24–25, 48 dp, скруглённый фон |
| mipmap-anydpi/ic_launcher_round.xml | Круглый fallback |
| mipmap-anydpi-v26/ic_launcher.xml и ic_launcher_round.xml | Adaptive foreground/background для API 26–32 |
| mipmap-anydpi-v33/ic_launcher.xml и ic_launcher_round.xml | Adaptive icon с monochrome для API 33+ |
| values/colors.xml | launcher_background и launcher_foreground |

Манифест ссылается на @mipmap/ic_launcher и @mipmap/ic_launcher_round.
Одинаковые ресурсы используются обычной .dev и production-сборкой; имя пакета
по-прежнему различает установки. Старые app_icon*.png и DEV layer-list сохранены
как исходная графика/исторические ресурсы, но launcher на них больше не ссылается.
Notification icon не менялся.

## Геометрия и редактирование

Adaptive foreground не включает собственный фон, маску или тень: маску задаёт
launcher. Значимый рисунок расположен вокруг центра (54,54), в пределах
центральной безопасной области. В legacy-векторе календарь увеличен в 1.5 раза,
чтобы компенсировать отсутствие adaptive crop. Круглая/обычная adaptive иконки
используют один рисунок; результат зависит от маски устройства.

При изменении рисунка синхронизировать foreground и оба legacy fallback.
Monochrome использует прозрачность того же foreground; цвет задаёт launcher.
Тематический режим требует поддержки launcher и включения пользователем.
Исходники иконок — сами VectorDrawable XML, редактируются в репозитории.

Официальная спецификация:
[Android adaptive icons](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive).
Adaptive layers поддерживают векторы; XML adaptive icon применяется с API 26,
monochrome — с API 33. Это не требует переноса приложения на Compose.

## Проверка и ограничения

После изменения нужны свежие release APK/AAB, проверка ссылок в манифесте,
packaged resources, подписи и структуры AAB, визуальный осмотр launcher и
релевантные UI/release smoke-проверки на проектном AVD. Результаты текущего шага
записываются в [DEVICE_TEST_RESULTS.md](DEVICE_TEST_RESULTS.md).
Старая сборка не подтверждает новые ресурсы. API 24 пока не запущен из-за
ограничения диска; наличие fallback не является проверкой на устройстве.

Иконка для карточки Google Play (отдельный графический ресурс магазина),
скриншоты листинга и окончательное оформление Console не входят в launcher XML.
Публикация не выполняется автоматически при изменении этих ресурсов.
