# Travel — мобильное приложение для планирования поездок

Android-приложение на **Java** для создания поездок, ведения плана визитов, подбора мест, прогноза погоды и персональных рекомендаций. Данные пользователя и поездок хранятся в **Firebase**; каталог мест и геопоиск — через **2GIS API**; погода — **Open-Meteo**.

## Возможности

- **Авторизация** — регистрация и вход через Firebase Authentication (email/пароль)
- **Поездки** — создание, просмотр, детали направления, план мест
- **План визитов** — дата и время посещения, напоминание **за 1 час** до визита (локальные уведомления)
- **Погода** — краткий прогноз на экране поездки и детальный на 3 дня (Open-Meteo)
- **Интересы** — теги для персональной подборки мест (2GIS)
- **Рекомендации** — вкладка «Для вас»: места по истории поездок и тегам, добавление в план выбранной поездки
- **Карта и избранное** — обзор на карте, сохранённые места
- **Профиль** — настройка интересов, выход из аккаунта

## Технологии

| Категория | Стек |
|-----------|------|
| Язык / SDK | Java 17, `minSdk` 23, `compileSdk` / `targetSdk` 36 |
| UI | AndroidX, Material, View Binding, RecyclerView |
| Backend | Firebase Auth, Firebase Realtime Database |
| Локальная БД | Room (SQLite) — профиль, избранное |
| Сеть | OkHttp, Gson |
| Внешние API | 2GIS, Open-Meteo |
| Сборка | Gradle (Kotlin DSL), Version Catalog (`libs.versions.toml`) |
| Тесты | JUnit 4 (`TravelFeaturesUnitTest`) |

Архитектура **многослойная**: экраны (Activity / Fragment) → вспомогательные классы (`utils`) → Firebase SDK и HTTP-репозитории. Отдельный слой **ViewModel** в проекте не выделен.

## Требования

- [Android Studio](https://developer.android.com/studio) (рекомендуется последняя стабильная версия)
- JDK **17**
- Проект **Firebase** с включёнными **Authentication** и **Realtime Database**
- Ключ **2GIS API** (указан в `AndroidManifest.xml` как `dgis.apikey`)

## Быстрый старт

1. Клонируйте репозиторий и откройте папку проекта в Android Studio.
2. Положите файл **`app/google-services.json`** из консоли Firebase (если файла нет — скачайте в настройках Android-приложения Firebase).
3. При необходимости замените ключ 2GIS в `app/src/main/AndroidManifest.xml`:

   ```xml
   <meta-data
       android:name="dgis.apikey"
       android:value="ВАШ_КЛЮЧ" />
   ```

4. Синхронизируйте Gradle (**File → Sync Project with Gradle Files**).
5. Запустите на эмуляторе или устройстве: **Run** → модуль `app`.

При первом запуске без сессии откроется **AuthActivity**; после входа — **MainActivity** с нижней навигацией.

## Сборка и тесты

```bash
# Windows
gradlew.bat assembleDebug
gradlew.bat testDebugUnitTest

# Linux / macOS
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

Unit-тесты: `app/src/test/java/com/example/myapplication/TravelFeaturesUnitTest.java` (10 тестов вспомогательной логики дат, рекомендаций, погоды).

## Структура проекта

```
MyApplication2/
├── app/
│   ├── build.gradle.kts          # зависимости и параметры модуля app
│   ├── google-services.json      # конфигурация Firebase (не коммитить публично без необходимости)
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/myapplication/
│       │   ├── MainActivity, *Fragment, *Activity   # UI
│       │   ├── adapter/                             # RecyclerView
│       │   ├── model/                               # Trip, EntertainmentPlace, …
│       │   ├── utils/                               # Firebase, 2GIS, погода, напоминания
│       │   ├── db/                                  # Room
│       │   └── receiver/                            # VisitReminderReceiver
│       └── res/layout, res/values
├── gradle/libs.versions.toml
├── build.gradle.kts
└── settings.gradle.kts
```

## Разрешения

В манифесте объявлены: **INTERNET**, **ACCESS_NETWORK_STATE**, геолокация (**FINE** / **COARSE**), **POST_NOTIFICATIONS** (Android 13+ для напоминаний о визитах).

## Основные экраны

| Компонент | Назначение |
|-----------|------------|
| `MainActivity` | Точка входа (launcher), вкладки: Обзор, Для вас, Карта, Избранное, Профиль |
| `AuthActivity` / `RegisterActivity` | Вход и регистрация |
| `CreateTripActivity` | Новая поездка |
| `TripDetailActivity` | Детали поездки, погода, интересы |
| `PlanActivity` | План визитов |
| `InterestTagsActivity` | Теги интересов |
| `WeatherForecastActivity` | Прогноз на 3 дня |

## Документация в репозитории

В корне проекта также есть материалы по учебной практике (описание Firebase, ER-диаграммы, SQL-задания и т.п.): `FIREBASE_*.md`, `DATABASE_*.md`, `ASSIGNMENT_*.md`.

## Лицензия

Учебный / практический проект. Уточните условия использования у автора репозитория.
