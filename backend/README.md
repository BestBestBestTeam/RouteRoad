# RouteRoad Backend

Java Backend системы диспетчеризации встреч по доставке банковских карт. Отвечает за REST API, бизнес-данные, управление заявками, представителями и офисами, а также проверку и активацию маршрутов, рассчитанных Python.

## Технологии

- **Java 25 LTS**
- **Spring Boot 4.1.2**
- **Maven 3.10.0** через Maven Wrapper
- **Spring WebMVC** — REST API
- **Spring Data JPA + Hibernate ORM** — работа с базой данных
- **PostgreSQL JDBC Driver** — подключение к PostgreSQL
- **Jakarta Bean Validation** — валидация данных
- **Flyway** — управление миграциями
- **PostgreSQL 18.6** — база данных в Docker

Версии зависимостей управляются Spring Boot BOM.

## Запуск проекта

### Требования

- JDK 25 с корректно настроенным `JAVA_HOME`
- Docker Desktop или Docker Engine с Docker Compose
- Доступ к Maven Central при первоначальной загрузке зависимостей

Все команды выполняются из каталога `backend`.

### 1. Настройка переменных окружения

```bash
cp .env.example .env
```

Пример содержимого:

```dotenv
DB_PORT=5434
DB_URL=jdbc:postgresql://localhost:5434/routeroad
DB_USERNAME=routeroad
DB_PASSWORD=password
```

Замените пароль на собственный. Локальный `.env` не хранится в Git.

Spring Boot не загружает `.env` автоматически: переменные нужно передать процессу через терминал или Run Configuration IntelliJ IDEA.

### 2. Запуск PostgreSQL

```bash
docker compose up -d
docker compose ps
```

Docker Compose использует `compose.yaml` и запускает контейнер `routeroad-postgres` с PostgreSQL 18.6.

- База данных: `routeroad`
- Внутренний порт: `5432`
- Локальный порт: задаётся `DB_PORT`, по умолчанию `5432`
- Текущая локальная конфигурация: `5434`
- Docker Volume: `routeroad_postgres_data`
- Точка монтирования: `/var/lib/postgresql`

Состояние `healthy` означает, что PostgreSQL принимает подключения.

Проверка через `psql`:

```bash
docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

```sql
SELECT current_database(), current_user, version();
```

Остановить контейнер без удаления данных:

```bash
docker compose stop
```

Не используйте `docker compose down -v`, если требуется сохранить данные.

**Важно:** изменение учётных данных в `.env` не изменяет пользователя и пароль уже инициализированной базы.

### 3. Сборка и запуск Spring Boot

```bash
./mvnw clean verify
```

Запуск с профилем `local`:

```bash
(
  set -a
  . ./.env
  set +a
  ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
)
```

В IntelliJ IDEA можно указать переменные `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` в **Run → Edit Configurations → Environment variables**, а в аргументах программы:

```text
--spring.profiles.active=local
```

Для запуска собранного JAR:

```bash
java -jar target/routeroad-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

При запуске JAR переменные подключения также должны быть заданы в окружении.

### 4. Проверка API

```bash
curl -i http://localhost:8080/api/health
```

Ожидаемый ответ:

```http
HTTP/1.1 200
Content-Type: application/json

{"status":"UP"}
```

`GET /api/health` проверяет доступность HTTP-приложения. Он не проверяет соединение с PostgreSQL или Python.

## Конфигурация

| Файл | Назначение |
|---|---|
| `application.yml` | Базовая конфигурация приложения |
| `application-local.yml` | PostgreSQL, Hibernate и Flyway для локальной разработки |
| `application-test.yml` | Заготовка для будущих тестов с PostgreSQL |
| `application-context-smoke.yml` | Тестовая конфигурация для проверки Spring-контекста без БД |
| `.env.example` | Шаблон переменных окружения |
| `compose.yaml` | Конфигурация PostgreSQL в Docker |

В профилях `local` и `test` настроены:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  flyway:
    enabled: true
```

Hibernate проверяет соответствие JPA-сущностей схеме, но не изменяет её. Структурой БД управляет Flyway.

Основное приложение требует настроенного подключения к PostgreSQL. Временное отключение DataSource существует только в тестовом профиле `context-smoke`, который не попадает в исполняемый JAR.

## Миграции Flyway

Миграции расположены в:

```text
src/main/resources/db/migration/
├── V1__create_initial_schema.sql
├── V2__extend_schema_for_final_api_contract.sql
└── V3__allow_offline_action_conflict.sql
```

V1 создаёт семь предметных таблиц:

| Таблица | Назначение | Владелец записи |
|---|---|---|
| `office` | Банковские офисы | Java |
| `courier` | Представители, смены, доступность | Java |
| `service_order` | Заявки клиентов | Java |
| `planning_run` | Запуски алгоритма планирования | Python |
| `route` | Маршруты представителей | Python, активация — Java |
| `route_stop` | Точки маршрутов | Python |
| `assignment` | История назначений заявок | Python |

Также создаётся sequence `planning_run_plan_version_seq`.

### Миграция V2

V2 сверена с окончательным контрактом «RouteRoad: сущности и API для Java» от 09.11.2026 и **успешно применена на локальной PostgreSQL 18.6**. При запуске с `local` Hibernate `ddl-auto=validate` подтвердил соответствие всех 13 JPA-сущностей обновлённой схеме; `GET /api/health` вернул HTTP 200. После применения V2 история содержала V1 и V2; расширение актуального списка бизнес-событий выполняет отдельная V3, описанная ниже.

Схема PostgreSQL содержит 13 предметных таблиц. Новые таблицы: `app_user`, `business_event`, `alert`, `alert_order`, `api_command`, `scenario_state`. `assignment` использует PK `(order_id, plan_version)` без отдельного UUID; `courier.availability_date` отсутствует, поскольку MVP содержит один рабочий день сценария. `api_command.error` допускает JSON-объект либо SQL NULL; проверка обязательных полей объекта относится к будущему обработчику команд.

Python должен записывать назначения без `assignment.id` и передавать обязательный `planning_run.metrics` с четырьмя целочисленными метриками. Актуальность маршрутов по-прежнему меняет только Java.

Уникальность `api_command` задаётся по `(actor_id, idempotency_key)` через `NULLS NOT DISTINCT`, включая системного автора и без привязки к поколению сценария. Команды не ссылаются FK на единственную строку `scenario_state`, чтобы сохраняться после смены сценария. Уникальный индекс по константе допускает не более одной строки состояния; её первоначальное создание относится к будущей реализации сценариев.

### Миграция V3 — этап 11.2

`V3__allow_offline_action_conflict.sql` заменяет только `business_event_type_check`, добавляя
`OFFLINE_ACTION_CONFLICT` к прежним 12 допустимым типам. Все 13 значений перечислены явно;
неизвестные строки по-прежнему отклоняются с SQLSTATE 23514. ALTER TABLE выполняется стандартным Flyway
в транзакции с проверкой существующих строк. Данные событий, тип колонки, остальные ограничения,
таблицы, индексы и последовательности не изменяются. V1/V2 сохраняют исходные контрольные суммы.
После применения V3 повторный запуск проверяет checksum и сообщает `No migration necessary`.

PostgreSQL-тесты сохраняют офлайн-конфликт через BusinessEventRepository, читают полный HistoryEntry
и проверяют все 13 типов, исторического Actor, reason, description, changes, nullable-ссылки и неизвестный тип.
Live HTTP-тест читает `GET /api/history?type=sync_conflict` через Tomcat и cookie-сессию диспетчера.
Публичный DTO, семь UI-категорий, авторизация и будущая POST /sync не меняются.

### Порядок применения

При запуске Spring Boot с профилем `local` Flyway:

1. Подключается к PostgreSQL.
2. Проверяет историю `flyway_schema_history`.
3. Проверяет контрольные суммы применённых миграций.
4. Выполняет новые миграции по порядку.
5. Записывает результат в историю.

Миграции выполняются до инициализации JPA EntityManagerFactory.

Проверка истории:

```sql
SELECT installed_rank, version, description, script, checksum, success
FROM public.flyway_schema_history
ORDER BY installed_rank;
```

**Правила:**

- Не изменять уже применённые миграции.
- Изменения схемы оформлять новыми файлами `V2__...sql`, `V3__...sql` и далее.
- При ошибке checksum сначала выяснять причину.
- `flyway repair` применять только после проверки и согласования.
- Не использовать Hibernate `ddl-auto=update` для изменения схемы.

### Основные решения схемы

- UUID для идентификаторов, `snake_case` для имён.
- `timestamptz` для времени, `date` для календарных дат.
- Координаты WGS84 с проверкой допустимых диапазонов.
- `PRIMARY KEY`, `FOREIGN KEY`, `UNIQUE`, `CHECK` и индексы для целостности данных.
- `planning_run.plan_version` генерируется PostgreSQL sequence.
- `planning_run.request_id` уникален для идемпотентности.
- `assignment` хранит историю назначений и не содержит `assignment.version`.
- `source_order_version` сохраняет версию заявки на момент расчёта.
- Python создаёт маршруты с `is_active = false`; Java активирует их после проверки.
- У представителя может быть только один активный маршрут на дату.
- Исторические маршруты сохраняются.
- Удаление связанных записей каскадом не предусмотрено.

Права отдельной роли PostgreSQL для Python пока не настроены.

## Архитектура

Используется **package-by-feature** — организация классов по функциональным модулям.

Целевая структура:

```text
com.routeroad
├── RouteRoadApplication
├── orders
├── couriers
├── offices
├── planning
├── execution
├── simulation
└── shared
    ├── api
    ├── config
    └── error
```

Функциональные пакеты создаются по мере реализации, без пустых классов и заглушек.

| Модуль | Ответственность |
|---|---|
| `orders` | Заявки и их жизненный цикл |
| `couriers` | Представители, смены, доступность и координаты |
| `offices` | Банковские офисы |
| `planning` | Маршруты, назначения, взаимодействие с Python и активация планов |
| `execution` | Выполнение маршрутов и изменение статусов встреч |
| `simulation` | Модельное время и сценарии симуляции |
| `shared` | Общие технические компоненты |

### Слои функционального модуля

```text
orders/
├── api/
├── application/
├── domain/
└── infrastructure/
```

| Слой | Назначение |
|---|---|
| `api` | REST-контроллеры и DTO |
| `application` | Сценарии использования и управление транзакциями |
| `domain` | Бизнес-модели и правила |
| `infrastructure` | JPA, репозитории, внешние интеграции |

Правила зависимостей:

- Контроллеры обращаются к `application`, а не напрямую к репозиториям.
- `domain` не зависит от HTTP, JPA и инфраструктурных реализаций.
- `application` работает с инфраструктурой через контракты.
- Межмодульное взаимодействие происходит через опубликованные контракты `application`.
- Циклические зависимости между модулями не допускаются.
- `shared` не содержит бизнес-логику отдельных модулей.

Java отвечает за проверку и активацию результатов Python. Python читает общую PostgreSQL и записывает данные планирования согласно согласованному контракту.

### JPA-модель хранения

Реализованы 13 сущностей в `com.routeroad.<модуль>.infrastructure`:

| Модуль | Сущности |
|---|---|
| `offices` | `OfficeEntity` |
| `couriers` | `CourierEntity` |
| `orders` | `ServiceOrderEntity` |
| `planning` | `PlanningRunEntity`, `RouteEntity`, `RouteStopEntity`, `AssignmentEntity` |
| `execution` | `BusinessEventEntity`, `AlertEntity`, `AlertOrderEntity` |
| `shared` | `AppUserEntity`, `ApiCommandEntity` |
| `simulation` | `ScenarioStateEntity` |

Перечисления находятся в `domain` соответствующих модулей. Составные ключи — `RouteStopId`, `AssignmentId`, `AlertOrderId`; lowercase-перечисления отображаются явными converters. `AssignmentEntity.planningRun` использует `@MapsId("planVersion")`, остальные межмодульные ссылки остаются скалярными UUID.

JSONB отображается через `@JdbcTypeCode(SqlTypes.JSON)`: метрики — типизированный `PlanningMetrics` с ключами snake_case, объекты событий/запросов/конфигурации — `Map<String, Object>`, произвольный результат и ошибка команды — Jackson `JsonNode`. Метрики и история планирования читаются без изменения; `BusinessEventEntity` также помечена `@Immutable`. Времена передаются явно: business_event.created_at — реальное время создания Java-события, occurred_at — модельное; времена результатов Python остаются модельными. Автоматические временные аннотации не используются.

Hibernate работает с `ddl-auto=validate`: проверяет mapping, а схемой по-прежнему управляет Flyway. UUID передаются явно; `ServiceOrderEntity.version` управляется Hibernate через `@Version`. Записи запусков расчёта, точек маршрутов и назначений помечены `@Immutable`; у маршрута для обновления доступен только `is_active`. Межмодульные внешние ключи отображаются скалярными UUID.

Версии хранятся как `Long`/`bigint`; в JSON HTTP API и обмена Java/Python все версии передаются десятичными строками, что предстоит обеспечить в DTO и JSON payload. Генератор передаёт начальную версию заявки `1`, тогда как обычный `persist` с null-версией Hibernate инициализирует её с `0`; SQL default из V1 также равен `0`. При реализации импорта сценария нужно выбрать явный путь создания: например, INSERT с версией генератора `1` в общей транзакции загрузки и последующее чтение managed-сущности. Дальнейшими UPDATE и увеличением `@Version` управляет Hibernate. Изменять `version` вручную у managed-сущности нельзя; текущий конструктор не реализует импорт сценария.

### Репозитории

Реализованы 13 Spring Data JPA репозиториев в `infrastructure` соответствующих модулей. Для основных данных Java используются `JpaRepository`; `BusinessEventRepository` предоставляет создание и чтение событий без удаления. `PlanningRunRepository`, `RouteStopRepository` и `AssignmentRepository` имеют только методы чтения через Spring Data `Repository`, без общего базового интерфейса.

`RouteRepository` предоставляет чтение и единственный изменяющий запрос `setActive(id, active)`. Он обновляет только `is_active`, требует транзакции вызывающего кода, выполняет flush и очищает persistence context. Атомарное переключение маршрутов, проверки сценария, версий и отказов относятся к будущему application-сервису. Ограничение методов репозитория не заменяет правила владения данными и права PostgreSQL.

Остановки выбираются по `id.routeId` с сортировкой по `id.sequence`; назначения — по полям составного `AssignmentId`. История назначений не является выборкой текущего назначения: для GET /workday и GET /orders используется общий SQL-снимок с подтверждением через PLAN_ACTIVATED и порядком created_at. Поиск команды по автору и ключу поддерживает `actorId=null` для системы и не ограничивается текущим сценарием.

При назначенном UUID без `@Version` стандартный `save()` выбирает `merge()` и возвращает managed-копию; вызывающий код должен использовать возвращённый объект. Новая заявка с null-версией определяется как новая и сохраняется через `persist()`. Стратегия импорта с начальной версией `1` ещё не реализована. Транзакции бизнес-операций определяются будущими application-сервисами; LAZY-связи читаются внутри транзакции при сохранённом `open-in-view=false`.

Реализованы авторизация, профиль, сводка рабочего дня, список и детальная карточка заявок; остальные бизнес-сервисы и CRUD API пока не реализованы.

## Авторизация и профиль

Модуль `auth` разделён на `api`, `application` и `infrastructure`. `AccountStore` — контракт доступа к данным; адаптер использует существующие репозитории. HTTP возвращает только DTO, без JPA-сущностей и паролей.

| Ручка | Доступ и результат |
|---|---|
| `POST /api/auth/login` | JSON `login`, `password`; 200 `UserView` |
| `GET /api/me` | Текущая сессия; 200 `UserView`, без входа 401 |
| `PATCH /api/me` | Только `name` длиной 2–64; 200 `UserView` |
| `POST /api/auth/logout` | POST с CSRF; 204, завершение сессии |
| `POST /api/auth/register` | Диспетчер, CSRF и `X-Scenario-ID`; 201 `RegisteredUser` |

Неизвестные JSON-поля отклоняются с 400. Роль регистрации всегда `representative`, роль сессии берётся из БД. Пароли хешируются стандартным PBKDF2-HMAC-SHA256 Spring Security (310 000 итераций, случайная соль, формат `{pbkdf2@SpringSecurity_v5_8}`). Пароль обязателен, ограничен 1024 символами; логин — 256. Ошибки возвращаются как `{"error":{"code":"...","message":"...","details":{}}}`, без HTML и перенаправлений. Несуществующий представитель даёт 404; дубликат логина/привязки — 409.

Сессия хранится на сервере; `JSESSIONID` передаётся только cookie с `HttpOnly`, `SameSite=Lax`. При входе меняются ID сессии и CSRF-токен, credentials удаляются из SecurityContext. Сессии в памяти не переживают рестарт. Для production включите профиль `prod`: он устанавливает `Secure=true`; HTTPS должен завершаться на доверенном сервере/прокси. В другом профиле Secure можно включить через `ROUTEROAD_SESSION_COOKIE_SECURE=true`. OSIV остаётся выключенным.

CSRF защищает все изменяющие запросы, включая login/logout. Перед первым входом клиент делает `GET /api/me`: ответ 401 содержит исходный токен в заголовке `X-CSRF-Token` и устанавливает анонимную cookie-сессию. Затем клиент отправляет cookie и этот заголовок при входе. После входа новый токен берётся из `csrf_token` в `UserView`; прежний токен недействителен. У GET `/api/me` после входа токен также находится в `UserView`. После logout повторите начальный GET перед новым входом. Отдельная HTTP-ручка получения CSRF не добавлена.

Login требует `Origin` с точным origin сервера (схема, хост и порт). Дополнительные доверенные origin задаются списком через `ROUTEROAD_SECURITY_ALLOWED_ORIGINS`; отсутствующий, `null` или посторонний Origin даёт 403. Симулятор должен передавать Origin и соблюдать тот же протокол cookie/CSRF. Для MVP предполагается общий origin фронтенда и API; CORS не включён. При внешнем HTTPS-прокси нужно согласовать доверенную обработку forwarded-заголовков или явно настроить внешний origin.

Регистрация не использует `Idempotency-Key` и не переключает сессию диспетчера. Обязательный `X-Scenario-ID` сравнивается с существующим singleton-сценарием под `PESSIMISTIC_READ` до завершения транзакции регистрации. Отсутствующий/невалидный заголовок даёт 400; старое поколение или отсутствие сценария — 409 `SCENARIO_RESET`. Сценарий и представители автоматически не создаются. Диспетчер может войти до загрузки сценария; регистрация доступна после будущей загрузки сценария.

### Первый диспетчер

Инициализация выключена по умолчанию. Для управляемой подготовки окружения явно передайте через защищённое окружение процесса:

- `ROUTEROAD_BOOTSTRAP_DISPATCHER_ENABLED=true`;
- `ROUTEROAD_BOOTSTRAP_DISPATCHER_LOGIN`;
- `ROUTEROAD_BOOTSTRAP_DISPATCHER_PASSWORD`;
- `ROUTEROAD_BOOTSTRAP_DISPATCHER_NAME` (2–64 символа).

Не помещайте секреты в Git или параметры командной строки. После подготовки отключите механизм и уберите пароль из окружения обычного запуска. Существующий диспетчер с тем же логином сохраняет прежние пароль и имя; логин представителя не может быть преобразован в диспетчера. При отсутствующих обязательных настройках первый запуск завершается ошибкой. Подготовка не создаёт `scenario_state` и не загружает сценарий.

## Сводка рабочего дня

`GET /api/workday?date=YYYY-MM-DD` доступен только диспетчеру: без сессии — 401, представитель — 403. GET не требует CSRF. При отсутствии `date` используется `scenario_state.service_date`, включая случай перехода вычисленных часов через полночь: автоматическая смена рабочего дня в MVP не реализуется. Пустой или некорректный `date` возвращает 400.

Модуль `workday` содержит `api` (контроллер, `WorkdayView`, `WorkdayCounts`), `application` (`WorkdayService`, контракт `WorkdayReader`) и `infrastructure` (JPA-адаптер существующих репозиториев). Общий DTO `Clock` находится в `shared.api`, `OfficeView` — в `offices.api`. JPA-сущности в HTTP не возвращаются; зависимости и схема БД не изменены.

Ответ `WorkdayView` содержит ровно `{date, counts, has_routes, algorithm, offices, clock}`. Поле `algorithm`
читается из `scenario_state.config.algorithm` через `JpaScenarioReader` → `ScenarioClockQuery` из того же снимка сценария,
что и Clock. Допустимы строго `greedy` и `ortools`; отсутствие ключа даёт `greedy` без записи значения в БД.
Явный null, неизвестная/пустая строка, неверный регистр или JSON другого типа считаются ошибкой серверной конфигурации:
`500 INTERNAL_ERROR` в существующем безопасном формате ошибки. Проверка выполняется только WorkdayService;
контракты остальных GET не меняются. История планов и маршруты для определения algorithm не используются.

Сводка читается в одной транзакции `readOnly=true`, `REPEATABLE_READ`. Числа заявок вычисляются PostgreSQL-агрегатом без загрузки заявок в память:

- `orders_total`: уникальные заявки с текущим `service_date=date` либо событием `ORDER_RESCHEDULED`, у которого `order_id` соответствует заявке, а `payload.from_date=date`. Повторные события переноса не увеличивают счётчик.
- `orders_assigned`: заявки текущей даты с последним подтверждённым решением и ненулевыми `courier_id`/`route_id`. Сам флаг активности маршрута не переписывает решение; наличие активных маршрутов отражается отдельно в has_routes. Перенесённые с дня заявки в нём имеют результат RESCHEDULED и сюда не входят. Выполненные заявки с сохранённым назначением могут входить одновременно в assigned и completed.
- `orders_completed`: заявки текущей даты со статусом COMPLETED; выполнение перенесённой заявки в следующем дне не изменяет этот счётчик исходного дня.
- `couriers_total`: все представители текущего однодневного сценария, включая недоступных; отдельной истории состава команды по датам в MVP нет.
- `open_alerts`: алерты выбранной даты со статусом open.

Решение допускается к выбору только при наличии `PLAN_ACTIVATED` для той же даты и версии, с ID заявки в `payload.order_ids`. Последним считается событие с наиболее поздним **реальным** `business_event.created_at`. `occurred_at` остаётся модельным временем; `source_order_version` служит для будущей проверки исходных данных, а `plan_version` идентифицирует расчёт. Эти поля не используются для порядка активации. В выборке участвуют неназначенные решения: более позднее подтверждённое решение с NULL-ссылками заменяет прежнее назначение. Частичные активации заменяют только перечисленные заявки. MAX(plan_version) не используется.

**Ограничение MVP:** совпадение created_at не позволяет установить фактическую хронологию. UUID события используется только для повторяемого выбора при равных временах, без гарантии порядка активации. Будущий сервис активации должен последовательно обрабатывать активации одного сценария и записывать событие в общей транзакции с изменениями. Дополнительные маркеры порядка не добавлены.

`has_routes` означает наличие маршрута с `is_active=true` и подтверждением его запуска через PLAN_ACTIVATED. Разные подтверждённые версии активных маршрутов допустимы. Версия решения не обязана совпадать с версией маршрута, на который оно ссылается. Офисы возвращаются из текущего сценария, в порядке name/id.

`Clock.now = model_anchor_at + (real_now - real_anchor_at) * speed`; при speed=0 возвращается model_anchor_at. Реальное время берётся из внедряемого `java.time.Clock`, результат возвращается как ISO timestamp с зоной UTC. `revision` сериализуется десятичной строкой; счётчики — int. GET ничего не сохраняет.

**Временное решение до согласования с фронтендом:** если scenario_state отсутствует, возвращается HTTP 409 `{"error":{"code":"SCENARIO_NOT_INITIALIZED","message":"Сценарий ещё не подготовлен","details":{}}}`. Фиктивный сценарий или Clock не создаётся. Существующий пустой сценарий возвращает 200 с нулевыми счётчиками.

`WorkdayServiceTests`, `WorkdayHttpTests` и `WorkdayPostgresTests` проверяют часы, JSON, права доступа, переносы, частичные планы, подтверждённые/неподтверждённые назначения и пустой сценарий. PostgreSQL-фикстуры откатываются; тесты используют прежний флаг `ROUTEROAD_REPOSITORY_TESTS=true`.

## Список заявок

`GET /api/orders` доступен только dispatcher (без сессии 401, представитель 403), CSRF для GET не требуется. Параметры: необязательные date/search/status/courier_id/assigned, page от 1, page_size 1–100 (по умолчанию 10). Дата по умолчанию и Clock вычисляются общим `simulation.application.ScenarioClockQuery`, который используется также сводкой. Отсутствующий сценарий даёт прежний 409 SCENARIO_NOT_INITIALIZED.

В `orders` реализованы OrderController → OrderQueryService → OrderReader → JpaOrderReader и DTO OrderView/AssignmentView. Page<T> и Actor находятся в shared.api, Clock переиспользуется. JPA-сущности не сериализуются; version, plan_version, source_order_version и revision передаются строками. Actor берётся из выбранного PLAN_ACTIVATED и app_user, для actor_id=NULL возвращается `{id:null,name:"Система",role:"system"}`. courier_name берётся из courier; reason_text передаётся без пересборки.

`planning.infrastructure.ConfirmedOrderSnapshotSql` — единое правило переноса и назначения для списка/карточки заявок, счётчиков workday и представителей; `OrderReadSql` использует этот общий фрагмент. Перенесённая заявка остаётся в исходном дне с day_status=RESCHEDULED; её текущие status и service_date не подменяются. Повторные события переноса не создают дубликаты. Новая заявка имеет assignment=null; подтверждённое неназначенное решение возвращается отдельным объектом с причиной и NULL-ссылками.

Поиск без учёта регистра работает по number, client_name, address, district, product. `%`, `_` и `!` трактуются как литералы. status фильтрует day_status. assigned=true выбирает подтверждённое назначение представителю на выбранный день; assigned=false включает новые, неназначенные и перенесённые с этого дня заявки. courier_id использует того же текущего представителя. Все фильтры применяются в PostgreSQL до LIMIT/OFFSET; total считается по всей фильтрованной выборке. Сортировка number/id стабильна на одном наборе данных. Список и total читаются в общей readOnly/REPEATABLE_READ-транзакции.

Для диспетчера available_actions содержит только REASSIGN, когда выбран текущий день сценария, заявка относится к нему и не начата/не завершена/не отменена, а для неё существует pending-элемент открытого алерта этого дня с `clock.now < deadline_at`. Отказы обрабатываются через соответствующие открытые алерты. Для исторического дня, закрытого элемента, IN_PROGRESS, COMPLETED, CANCELLED и RESCHEDULED действий нет. Действия представителя не выдаются. Доступность конкретного получателя, версии и выполнимость будущий POST обязан проверять повторно; POST на этом этапе не реализован.

Тесты OrdersHttpTests и OrdersPostgresTests проверяют фильтры, пагинацию, переносы, версии, actor, действия, права и совпадение счётчиков с workday. Есть регрессия: при одинаковых source_order_version/occurred_at побеждает более поздний created_at, даже если более ранний расчёт имеет большую plan_version.

## Детальная карточка заявки

`GET /api/orders/{id}?date=YYYY-MM-DD` возвращает `{order:OrderView,alerts:AlertView[],clock:Clock}`. UUID ищется непосредственно через PK, без чтения страницы списка. Общие SQL-фрагменты и mapper сохраняют одинаковые day_status/assignment в списке и карточке; для dispatcher совпадают и действия. Дата по умолчанию — модельный день сценария. Некорректные UUID/date дают 400, отсутствующая заявка/заявка вне выбранного дня — 404, отсутствующий сценарий — прежний 409 SCENARIO_NOT_INITIALIZED.

Dispatcher видит текущий и исходный день переноса. Representative при каждом запросе проверяется по серверному login и текущим app_user.role/courier_id. Владелец определяется по последнему подтверждённому назначению на **текущую service_order.service_date**, независимо от переданного date. Чужая заявка, прежний исполнитель после переназначения, подтверждённое неназначенное решение и исторический день дают 403 без данных заявки и алертов. Клиентский courier_id не является доказательством прав; неподтверждённый Python-план не меняет владельца. После отказа, пока назначение сохранено, карточку читать можно; отмена также не отнимает чтение у текущего исполнителя, но исключает действия.

Действия представителя: ASSIGNED → EN_ROUTE → ARRIVED → IN_PROGRESS → COMPLETED. REPORT_DELAY доступен в ASSIGNED/EN_ROUTE/ARRIVED/IN_PROGRESS; REFUSE_ORDER — в ASSIGNED/EN_ROUTE/ARRIVED. ORDER_REFUSED для пары заявка/представитель блокирует все дальнейшие действия. После COURIER_REFUSED выбранного дня у IN_PROGRESS сохраняются COMPLETED и REPORT_DELAY, остальные встречи блокируются. В другом модельном дне действия не предлагаются. is_available относится к новым назначениям и сам по себе не отменяет сохранённое назначение. Обработчики POST не реализованы.

Полный AlertView включает поля alert, order_ids, orders с отдельными Resolution, title, impact, общую resolution и available_actions. Dispatcher получает все связанные алерты выбранной даты целиком. Representative получает только scope=order с его courier_id, без каких-либо других элементов alert_order. Группы исключаются полностью: их свободные reason_text/resolution_reason могут раскрывать другие заявки даже при фильтрации массива. Действия алертов для представителя пусты. Dispatcher у открытого алерта с допустимой pending-заявкой текущего дня получает AUTO_REASSIGN; REASSIGN добавляется только до модельного дедлайна.

Resolution строится из resolved_at/by/reason и app_user; NULL resolved_by означает системного автора. При отсутствии сохранённой resolution_reason источник остаётся null, а обязательная строка Resolution.reason возвращается пустой, без вымышленного объяснения. title/impact отражают kind/scope и фактические количества элементов/pending-решений. GET не создаёт алерты и не меняет их статусы.

Авторизация предшествует чтению алертов. Карточка собирается в readOnly/REPEATABLE_READ-транзакции. OrderDetailHttpTests, OrderDetailPostgresTests и RepresentativeOrderActionsTests проверяют согласованные правила, полный ответ, приватность и отсутствие изменений БД. Новых таблиц, миграций и POST-ручек нет.

## Список представителей

`GET /api/couriers` доступен только dispatcher: без сессии 401, представитель 403; CSRF для GET не требуется. Параметры: date, search, is_available, has_open_alerts, workload=assigned|unassigned, page от 1 и page_size 1–100 (по умолчанию 10). Дата по умолчанию и Clock берутся через ScenarioClockQuery; отсутствие сценария даёт 409 SCENARIO_NOT_INITIALIZED.

Цепочка `CourierController → CourierQueryService → CourierReader → JpaCourierReader → CourierRepository` возвращает Page<CourierView>. DTO содержит id, name, office_id, shift_start, shift_end, position (lat/lon/at либо null), is_available, total_orders, completed_orders, open_alert_count, available_actions. Общие Page/Clock переиспользуются, Position находится в shared.api. revision передаётся строкой. Для диспетчера available_actions пуст: REFUSE_WORKDAY относится к действиям представителя.

total_orders включает все текущие подтверждённые назначения выбранного дня, в том числе COMPLETED/CANCELLED и необычные NEW/UNASSIGNED с сохранённым назначением. completed_orders — их подмножество COMPLETED. workload=assigned означает наличие ASSIGNED/EN_ROUTE/ARRIVED/IN_PROGRESS; unassigned — отсутствие таких встреч. Перенесённые с выбранного дня заявки и неподтверждённые планы не учитываются. Доступность независима от нагрузки и относится к текущему сценарию; отдельной истории доступности по датам нет.

open_alert_count считает открытые заголовки alert выбранного дня с courier_id данного представителя. Группа считается один раз; NULL-courier алерты не привязываются через заявки, переназначение встречи не переносит алерт новому исполнителю. has_open_alerts проверяет этот счётчик.

Поиск — буквальная подстрока name без учёта регистра, с экранированием %, _ и !. Фильтры, агрегаты, total и LIMIT/OFFSET выполняются в PostgreSQL; сортировка name/id стабильна на одном наборе данных. Чтение состояния сценария, total и страницы использует readOnly/REPEATABLE_READ. Дополнительных запросов для каждого представителя нет. Порядок подтверждённых решений общий с заявками и сводкой: PLAN_ACTIVATED.created_at; ограничение MVP при одинаковом created_at сохраняется.

CouriersHttpTests и CouriersPostgresTests проверяют полный JSON, безопасность, фильтры, пагинацию, частичные перерасчёты, отказавшегося представителя, атрибуцию групповых алертов, совпадение счётчиков с orders/workday и отсутствие изменений БД при GET. PostgreSQL-фикстуры откатываются; запуск через ROUTEROAD_REPOSITORY_TESTS=true.

## Расписание представителя

`GET /api/couriers/{id}/schedule?date=YYYY-MM-DD` реализует предоставленный окончательный контракт Frontend ↔ Java от 09.11.2026. Ответ — `ScheduleView {date, courier, route, orders, offices, clock}`. Dispatcher читает любого существующего представителя; representative — только свой `app_user.courier_id`, полученный по login серверной сессии. Проверка владельца выполняется до чтения представителя, сценария и подробностей. Без сессии — 401, чужой ID — 403, отсутствующий представитель для dispatcher — 404, некорректные UUID/date — 400. GET не требует CSRF. При отсутствии `scenario_state` — 409 `SCENARIO_NOT_INITIALIZED`.

Цепочка: `CourierController → CourierScheduleService → CourierScheduleReader / OrderReader → JpaCourierScheduleReader / JpaOrderReader → существующие репозитории`. Сервис владеет единой `readOnly / REPEATABLE_READ`-транзакцией, включая аккаунт, часы, назначения, маршрут, остановки и офисы. OSIV выключен; JPA-сущности наружу не возвращаются.

`CourierRepository.findById` загружает представителя. `RouteRepository.findConfirmedActive` выбирает маршрут по `courier_id`, выбранному `plan_date`, `is_active=true` и существованию `PLAN_ACTIVATED` той же даты с соответствующей строковой `payload.plan_version`. Глобальная максимальная версия не используется: активные маршруты разных представителей могут принадлежать разным запускам. Без подтверждённого активного маршрута возвращается `route=null`, а реальные подтверждённые назначения сохраняются в `orders`.

`OrderReader.findSchedule` использует `ConfirmedOrderSnapshotSql.COURIER_SNAPSHOT`: кандидаты ограничиваются выбранной датой и назначениями данного представителя **до** разрешения текущего решения. Само разрешение переиспользует общий SQL списков/карточки/сводки: только `PLAN_ACTIVATED`, членство заявки в `payload.order_ids`, последняя активация по реальному `business_event.created_at`, включая явное неназначенное решение. Затем исключаются заявки, актуально назначенные другому представителю или никому. При одинаковом времени сохраняется общий детерминированный UUID tie-breaker; он не устанавливает бизнес-хронологию. Refusal-флаги читаются в том же SQL-запросе, без запроса на каждую заявку. `OrderView` и `AssignmentView`, включая автора подтверждения, формируются общим mapper. Массив заявок стабильно сортируется по `number/id`; порядок посещения задаётся исключительно `route.stops`.

`RouteStopRepository.findById_RouteIdOrderById_SequenceAsc` читает существующие составные ключи `(route_id, sequence)` и сохраняет рассчитанные Python порядок, ETA, расстояния и нарушения окон. Новый `RouteStop` содержит ровно девять полей контракта, без дополнительных координат. `RouteView` содержит девять полей `route`, `stops`, `geometry`, `geometry_kind`. Java строит `LineString` по связанным `OrderView`/`OfficeView` в порядке остановок; пары — `[lon, lat]`, `geometry_kind=straight`. Время сериализуется с зоной UTC (`Z`), все Long-версии — JSON-строками. Дорожный источник не подключается.

Офисы загружаются одним `findAllById`: домашний офис представителя и все OFFICE-остановки, без посторонних офисов; порядок — `name/id`. `CourierView` переиспользуется целиком: счётчики назначений/завершений согласованы с `GET /orders` и `GET /couriers` выбранного дня, доступность относится к текущему сценарию. Открытые алерты считаются только по собственным `courier_id/date`; сами алерты не входят в ответ.

Для dispatcher действия представителя отсутствуют, а OrderView сохраняет допустимый REASSIGN, согласованный со списком и карточкой заявки; действия самого CourierView пусты. Для representative действия заявок вычисляет общий `RepresentativeOrderActions`: учитываются статус, текущий день, отказ от заявки, отказ на день и ожидание решения конкретного pending-элемента открытого алерта; `IN_PROGRESS` после отказа на день сохраняет `COMPLETED/REPORT_DELAY`. `REFUSE_WORKDAY` выдаётся только своему доступному представителю в текущем дне без сохранённого отказа на этот день. Новых POST-обработчиков и переходов статусов нет.

Допустимы пустое расписание, маршрут без ORDER-остановок и назначения без активного маршрута. Частичный перерасчёт может сохранить неизменённые назначения с прежними ссылками на маршрут: равенство версий назначения/маршрута и равенство сохранённого `assignment.route_id` новому маршруту не требуются; проверяется фактическое членство заявки в текущих назначениях представителя. Завершённые/отменённые заявки могут отсутствовать в перестроенном маршруте. Остальные назначения при существующем маршруте обязаны иметь ORDER-остановки. Чужая/неназначенная ORDER-остановка, дубликат заявки, отсутствие живой остановки или некорректная первая остановка — ошибка согласованности данных (`500 INTERNAL_ERROR` без частичного расписания и внутренних подробностей). Исторические остановки не исправляются и данные чужой заявки для заполнения геометрии не читаются.

Согласованное уточнение контракта MVP: `RouteView.geometry` и `MapRoute.geometry` имеют тип `Geometry | null`. При двух и более валидных точках общий `RouteViewAssembler` строит GeoJSON `LineString` в порядке `[lon, lat]`. При менее чем двух точках возвращается `geometry=null`; сам подтверждённый маршрут и все реальные `stops` сохраняются. Единственная точка не дублируется. Невалидные координаты (нечисловые/бесконечные значения, выход за WGS84) не попадают в геометрию; порядок оставшихся валидных точек сохраняется. Отсутствующая связь OFFICE/ORDER или противоречие текущим назначениям остаются ошибкой данных и не заменяются координатами.

Проверки: `CourierScheduleServiceTests`, `CourierScheduleHttpTests`, `CourierScheduleRepositoryPostgresTests`, `CourierSchedulePostgresTests`, `CourierScheduleLiveHttpPostgresTests`. Проверка со 109 назначениями контролирует отсутствие N+1 и лимита страницы в расписании. Большинство PostgreSQL-фикстур откатывается. Реальный HTTP-тест поднимает Tomcat на случайном порту, временно коммитит проверенные свободные ID, удаляет их в `finally` и сравнивает контрольные суммы всех 13 таблиц, Flyway и состояние sequence с исходными. При существующем сценарии/занятых тестовых ID этот тест пропускается без очистки чужих данных.

## Общая карта рабочего дня

`GET /api/workday/map?date=YYYY-MM-DD` доступен только dispatcher. Представитель получает 403, анонимный пользователь — 401; GET не требует CSRF. Параметр только `date`, без серверной пагинации карты. Некорректная дата даёт 400 `INVALID_REQUEST`, отсутствие сценария — 409 `SCENARIO_NOT_INITIALIZED`. Дата по умолчанию — текущий день из `ScenarioClockQuery`, часы вычисляются существующим механизмом.

Цепочка: `WorkdayController → WorkdayMapService → WorkdayMapReader → JpaWorkdayMapReader`. Сервис владеет одной `readOnly / REPEATABLE_READ`-транзакцией для всех частей ответа. Полный JSON содержит ровно следующие поля:

```text
MapView {
  orders: MapOrder[] {id, number, address, client_name, lat, lon, day_status},
  couriers: MapCourier[] {id, name, position: Position|null},
  offices: OfficeView[] {id, name, address, lat, lon},
  routes: MapRoute[] {
    id, courier_id, plan_version: string,
    stops: RouteStop[] {
      route_id, sequence, type, order_id: UUID|null, office_id: UUID|null,
      arrival_eta, departure_eta, distance_from_prev_m, window_violation_s
    },
    geometry: {type: "LineString", coordinates: [lon, lat][]}|null,
    geometry_kind: "straight"
  },
  alerts: MapAlert[] {id, order_ids: UUID[], courier_id: UUID|null},
  clock: Clock {scenario_id, now, speed, revision: string}
}
```

`MapView` не содержит `date`, счётчиков, `assignment`, флага доступности, офиса представителя или действий: их нет в контрактной проекции. Координаты заявки обязательны и обеспечены существующими `NOT NULL`/CHECK PostgreSQL; заявка с отсутствующими координатами не может быть сохранена. Позиция представителя берётся из сохранённой тройки `position_lat/lon/at`, при отсутствии измерения остаётся `null`. Общий `Position.fromNullable` используется также списком представителей и расписанием; позиция не вычисляется по маршруту и не заменяется офисом.

`OrderReader.findDay` использует общий `ConfirmedOrderSnapshotSql.SNAPSHOT` и mapper `JpaOrderReader`, без LIMIT/OFFSET. Он включает заявки выбранного дня и перенесённые с него заявки из истории с `day_status=RESCHEDULED`, как `/orders`. Выбор текущего назначения остаётся общим: только `PLAN_ACTIVATED` с членством в `order_ids`, последнее решение по `business_event.created_at`, включая явное снятие назначения. Неподтверждённый Python-план не создаёт действующее назначение. Неназначенные заявки остаются маркерами; подтверждённый владелец используется для проверки ORDER-остановок, но не добавляется новым полем в `MapOrder`.

Представители и офисы читаются из текущих таблиц сценария, как в `/couriers` и `/workday`, без исключения недоступных представителей и людей без маршрутов. В MVP нет отдельного `scenario_id` в этих таблицах. Сортировка заявок — `number/id`, представителей и офисов — `name/id`. Все офисы сценария возвращаются через существующий `WorkdayReader.findOffices`, а преобразование в `OfficeView` переиспользуется сводкой.

Подтверждённые маршруты выбираются одним пакетным запросом `RouteRepository.findAllConfirmedActive`. Общий `ConfirmedRouteSql` задаёт один и тот же предикат `date + is_active + PLAN_ACTIVATED` для карты, `/schedule` и признака `has_routes` в `/workday`. Максимальная глобальная версия не используется. Все остановки выбранных маршрутов читаются одним запросом `RouteStopRepository.findById_RouteIdInOrderById_RouteIdAscId_SequenceAsc`, затем группируются без изменения последовательности. Общий `RouteViewAssembler` проверяет текущие назначения и строит геометрию для обеих ручек. При `geometry=null` маршрут остаётся в `routes`; при отсутствии подтверждённых активных маршрутов `routes=[]`.

Открытые алерты выбранного дня проецируются через один `LEFT JOIN alert_order`, затем группируются по заголовку. Сохраняются все `order_ids` открытого `AlertView`, включая уже решённые элементы ещё открытой группы; закрытые заголовки и другие дни исключаются. При отсутствии связей список пустой. Причины, авторы, внутренние детали и действия алертов в карту не добавляются. Сортировка — ID алерта/заявки.

Количество SQL-запросов не зависит от количества представителей, маршрутов, остановок, заявок и алертов. При непустой выборке маршрутов выполняются семь запросов, включая сценарий; дополнительных запросов на маршрут/заявку нет. История целиком не загружается. OSIV, LAZY-связи, V1/V2 и схема не изменены.

Тесты: `WorkdayMapServiceTests`, `WorkdayMapHttpTests`, `WorkdayMapPostgresTests`, `WorkdayMapLiveHttpPostgresTests`, `RouteGeometryTests` и обновлённые регрессии `CourierSchedulePostgresTests`. Проверяются 0/1/2+ остановки с сохранением маршрута, недостаточные/невалидные координаты в общем сборщике, обязательность координат в PostgreSQL, переносы, подтверждённое снятие назначения, частичный перерасчёт, авторизация, согласованность всех GET, а также постоянное число запросов при росте до 15 представителей, 150 заявок, 14 маршрутов и 14 открытых алертов. Реальные TCP/HTTP-тесты используют Tomcat и PostgreSQL, удаляют временно сохранённые фикстуры в `finally` и сравнивают все 13 предметных таблиц, Flyway и sequence с исходным состоянием.

## Список алертов диспетчера

`GET /api/alerts` возвращает существующий `Page<AlertView>`: `{items, total, page, page_size, clock}`. Единственная новая HTTP-ручка этапа; команды обработки алертов не реализованы. Доступ только dispatcher: anonymous получает 401, representative — 403, GET не требует CSRF. Ошибки имеют существующий формат `{error:{code,message,details}}`.

Все параметры необязательны: `date` в формате `YYYY-MM-DD` (по умолчанию день сценария), `search`, `status=open|resolved`, `severity=warning|critical`, `page` от 1 (по умолчанию 1), `page_size` 1–100 (по умолчанию 10). Без status/severity возвращаются все состояния/важности. Неверные значения дают 400 `INVALID_REQUEST`; отсутствие сценария — 409 `SCENARIO_NOT_INITIALIZED`. Clock берётся из общего `ScenarioClockQuery`, `revision` передаётся строкой. Дополнительных фильтров kind/scope/courier_id/order_id нет.

Согласованная сортировка заголовков: `alert.created_at DESC, alert.id ASC`. Поиск — буквальная подстрока без учёта регистра по `alert.reason_code`, `alert.reason_text`, `courier.name` через сохранённый `alert.courier_id` и `service_order.number/client_name/address` через `alert_order`. Общий `TextSearch` убирает внешние пробелы, превращает пустой поиск в отсутствие фильтра и экранирует `%`, `_`, `!`. Имена текущих исполнителей заявок не подменяют исходного представителя алерта. Причины резолюций, title/impact и дополнительные поля не входят в согласованный поиск.

Цепочка: `AlertController → AlertQueryService → AlertReader → JpaAlertReader → AlertRepository`. Сервис охватывает clock, COUNT, страницу и состав групп одной `readOnly / REPEATABLE_READ`-транзакцией. `AlertReadSql` применяет все фильтры к уникальным заголовкам; совпадения связанных заявок проверяются через `EXISTS`. Поэтому несколько совпавших заявок не размножают строки COUNT/страницы. После COUNT выбирается страница UUID с SQL LIMIT/OFFSET, затем один `OrderAlertSql.BATCH` загружает все элементы выбранных групп и авторов резолюций. Порядок результата восстанавливается по выбранным UUID независимо от порядка пакетного запроса. На пустой странице состав групп не читается. История/все алерты не загружаются в Java для фильтрации.

`AlertView` не изменён. Общий `AlertViewAssembler` используется новым списком и `JpaOrderAlerts` карточки заявки. `OrderAlertSql.COLUMNS/JOINS` переиспользуются обоими чтениями. Заголовок имеет собственные status/resolution, элементы `orders` — свои status/resolution. Совпадение одного элемента включает всю группу, в том числе resolved-элементы и их резолюции. Один заголовок — один элемент списка. LEFT JOIN сохраняет и существующие заголовки без связей: `order_ids=[]`, `orders=[]`, без фиктивной заявки. Заголовок не закрывается автоматически после чтения resolved-элементов.

Сохраняются значения `kind=delay|refusal|conflict`, `scope=order|courier_day`, `severity=warning|critical`, общий `status=open|resolved` и статус элемента `pending|resolved`. `title` использует прежние подписи вида/области; `impact` — фактические количества всех и pending-заявок. `reason_text` возвращается без пересборки; NULL запрещён существующей схемой, пустая строка сохраняется. Nullable `resolution_reason` заголовка остаётся NULL; обязательный строковый `Resolution.reason` при отсутствии текста остаётся пустым, как в карточке. NULL resolved_by означает Actor системы; остальные авторы берутся из app_user. Общая и индивидуальные резолюции не подменяют друг друга.

`available_actions` использует общий `ReassignmentActions`: выбран текущий день сценария, заголовок open и есть хотя бы один pending-элемент, заявка которого относится к этому дню и имеет NEW/ASSIGNED/UNASSIGNED/EN_ROUTE/ARRIVED. При выполнении этих условий AUTO_REASSIGN остаётся доступным; REASSIGN выдаётся только при `clock.now < deadline_at`. При равенстве или после дедлайна ручного действия нет. IN_PROGRESS, COMPLETED, CANCELLED, перенесённые заявки и resolved-элементы не создают действий. В представительской карточке сохраняются ограничения видимости и пустые действия алертов.

При переназначении заявки `alert.courier_id` остаётся исходной связью. Список открытых алертов согласован с `workday.open_alerts`, `couriers.open_alert_count` и `workday/map.alerts`; NULL-courier заголовки не приписываются текущим исполнителям через заявки. Состав AlertView совпадает с dispatcher-карточкой заявки.

Тесты: `AlertViewAssemblerTests`, `AlertQueryServiceTests`, `AlertsHttpTests`, `AlertBatchPostgresTests`, `AlertsPostgresTests`, `AlertsLiveHttpPostgresTests`. Проверены полный JSON, все поисковые поля и спецсимволы, целые группы при совпадении одного/нескольких элементов, сортировка и пагинация, права, исторические даты, резолюции, действия, смена исполнителя и согласованность GET. Замер при росте с 10 до 90 заголовков и добавлении 960 элементов групп сохраняет четыре SQL-запроса, включая сценарий. PostgreSQL-фикстуры откатываются; реальные HTTP-тесты удаляют временно сохранённые записи в finally и сравнивают 13 таблиц, Flyway и sequence с исходным состоянием.

## Тестирование

```bash
./mvnw clean verify
```

Сейчас реализованы:

- `RouteRoadApplicationTests` — проверка загрузки Spring-контекста.
- `ConfigurationProfilesTests` — проверка конфигурации и изоляции профилей.
- `HistoricalRepositoryContractTests` — типы ключей и ограничение публичных операций истории.
- `RepositoriesPostgresTests` — создание 13 репозиториев, запросы, пагинация, составные ключи, порядок остановок и изменение только активности маршрута.

Обычный `./mvnw clean verify` работает без PostgreSQL; проверки PostgreSQL включаются явно. Проверки списка заявок и сводки запускаются в том же режиме PostgreSQL; результаты текущего прогона приведены в отчёте задачи. Тесты авторизации проверяют полную цепочку Spring Security, JSON-ошибки, Origin, CSRF и жизненный цикл сессии. `AuthenticationPostgresTests` проверяет регистрацию, хеширование, профиль и инициализацию диспетчера на реальной БД с откатом.

Для явного включения PostgreSQL-тестов нужна запущенная локальная БД с применёнными V1/V2 и пустыми предметными таблицами:

```bash
(
  set -a
  . ./.env
  set +a
  ROUTEROAD_REPOSITORY_TESTS=true ./mvnw clean verify
)
```

Фикстуры `repository-fixtures.sql` загружаются через `@Sql` внутри тестовой транзакции и откатываются через `@Rollback`; sequence не используется. Данные автоматически не очищаются. При существующей записи `scenario_state` PostgreSQL-тесты пропускаются. H2 и Testcontainers не подключены; существующая заготовка профиля `test` сохранена.

## История рабочего дня — этап 10

`GET /api/history` → `HistoryController` → `HistoryQueryService` → `HistoryReader` → `JpaHistoryReader`.
Доступ — dispatcher; anonymous получает 401, representative — 403, GET не требует CSRF.
Ответ — общий `Page<HistoryEntry>` с `items`, `total`, `page`, `page_size`, `clock`.

| Query | Семантика |
|---|---|
| `date` | Необязательная YYYY-MM-DD; по умолчанию `scenario_state.service_date` |
| `search` | Буквальная подстрока без учёта регистра в отображаемых title, description, reason; TextSearch экранирует `%`, `_`, `!` |
| `type` | Одна из семи категорий UI ниже |
| `actor_role` | `dispatcher`, `representative`, `system`; роль того же Actor, который возвращается в ответе |
| `order_id` | UUID: прямая ссылка события либо элемент корректного массива UUID-строк `payload.order_ids` |
| `page`, `page_size` | С 1; по умолчанию 1/10; размер 1–100 |

`HistoryEntry` содержит строго `{id,type,created_at,actor,order_id,alert_id,title,description,reason,changes}`.
Время возвращается как сохранённый `created_at` в ISO 8601 с зоной; `occurred_at` его не заменяет и в DTO не входит.
Сортировка: `created_at DESC, id ASC`, независимо от модельного occurred_at и версий плана.
`order_id` и `alert_id` nullable; для группового события не придумывается одна заявка.
`alert_id` берётся только из корректного UUID в payload. Некорректный/отсутствующий changes даёт `[]`;
валидный Change содержит field и оба JSON-значения before/after, включая null. Известные Long-версии
(version, plan_version, source_order_version, base_version, revision) внутри changes сериализуются строками без потери точности.

| Внутренний тип | Категория UI |
|---|---|
| DATA_LOADED, SCENARIO_RESET | data_loaded |
| PLAN_ACTIVATED | routes_built |
| STATUS_CHANGED, ORDER_RESCHEDULED, COURIER_DELAYED | status_changed |
| ASSIGNMENT_CHANGED | assignment_changed |
| ORDER_REFUSED, COURIER_REFUSED, ALERT_CREATED | alert_created |
| ALERT_ORDER_RESOLVED, ALERT_RESOLVED | alert_resolved |
| OFFLINE_ACTION_CONFLICT | sync_conflict |

Эта классификация — группировка UI, а не утверждение о буквальном эффекте события.
Заголовки Java описывают внутреннее событие: отказ, задержку, решение одной заявки алерта, сброс сценария.
`HistoryPresentation` — единственный словарь категорий и заголовков; из него генерируется SQL-проекция для поиска.
Description формируется единственным SQL-выражением из строкового `payload.description`, иначе из сохранённого reason.
Для офлайн-конфликта добавляются сохранённый reason_code (либо строковый payload.reason) и корректные UUID-ссылки
client_action_ids/command_ids. Сырые payload и запрос команды в API не возвращаются. Reason всегда берётся из business_event.reason.
Текущее состояние заявки, назначения и имя представителя историю не переписывают.

При actor_id=null возвращается `{id:null,name:"Система",role:"system"}`.
Исторический снимок `payload.actor` используется только при корректных id/name/role и совпадении id с actor_id;
иначе имя/роль читаются из app_user. Такой запасной источник содержит **текущие метаданные профиля**, а не гарантированное историческое имя.

Reader выполняет два параметризованных SQL-запроса (COUNT и LIMIT/OFFSET) с одной общей проекцией/фильтрами.
JOIN только с app_user, без размножения групповых событий. Валидируется весь массив order_ids, ошибочные массивы игнорируются.
Все фильтры применяются до пагинации. Вместе с чтением сценария — три запроса независимо от размера страницы;
ответ собирается в одной readOnly / REPEATABLE_READ-транзакции. OSIV и LAZY не изменены.
При отсутствии сценария — 409 SCENARIO_NOT_INITIALIZED; неверные параметры — 400 INVALID_REQUEST.

**Синхронизация схемы (этап 11.2):** V3 расширяет `business_event_type_check` до всех 13 значений,
включая OFFLINE_ACTION_CONFLICT. Сохранённый конфликт читается существующей инфраструктурой как sync_conflict;
проверка прежнего запрета заменена успешным сохранением и проверкой отклонения неизвестного типа.

**Условие изоляции сценариев и тестовый план будущего reset:** business_event не имеет scenario_id;
GET не фильтрует по выдуманному payload.scenario_id и не считает дату/время доказательством принадлежности поколению.
Читается текущий активный набор предметных данных. Reset/загрузка пока не реализованы.
При реализации reset необходимо атомарно удалить business_event прежнего поколения вместе с предметными данными,
сохранив api_command. Приёмочный тест reset: создать старые события, выполнить сброс, загрузить новый сценарий
(в том числе с той же service_date), проверить отсутствие старых ID в history и сохранность ключей/результатов api_command.
Отдельный GET-тест фиксирует, что произвольные payload.scenario_id не дают ложной изоляции.

## Действия и HTTP-ошибки — этап 11.3

`OrderReader.ActionContext` содержит ограничения отказов и awaitingDecision для конкретной заявки.
Карточка получает их одной существующей выборкой, расписание — в общей пакетной выборке заявок.
Открытый алерт без pending-элемента этой заявки не блокирует её. При ARRIVED и ожидающем решения элементе
не выдаётся IN_PROGRESS; REPORT_DELAY/REFUSE_ORDER сохраняются согласно прежним ограничениям.
Уже начатая встреча сохраняет COMPLETED/REPORT_DELAY, в том числе после отказа на день.
Разрешение элемента либо закрытие алерта снимает эту блокировку; подтверждённая передача отзывает доступ прежнего исполнителя.

Общий `ReassignmentReadSql` задаёт дневную допустимость статуса и область open/pending.
В срез заявки добавлен максимальный deadline_at среди её открытых pending-алертов.
Это означает существование хотя бы одного ещё допустимого алерта, а не изменение правила выбора подтверждённого плана.
`ReassignmentActions` сравнивает этот срок с единственным Clock.now из ScenarioClockQuery.
У каждого AlertView проверяется собственный deadline_at: просроченный заголовок не получает REASSIGN даже при наличии другого алерта заявки.
Clock.now передаётся Service → Reader → общий сборщик; системное Instant.now не используется.
Пауза speed=0 сохраняет модельный срок независимо от хода реального времени.
Список, карточка и расписание диспетчера согласованно возвращают OrderView.available_actions.
Будущие POST обязаны независимо повторять проверки при выполнении и перед активацией; команды здесь не реализованы.

Logout проверяет исходную Authentication в success handler: обе вошедшие роли получают 204 с завершением сессии,
аноним с корректным CSRF — 401. Отсутствующий/неверный CSRF по-прежнему обрабатывается Spring Security как 403;
Origin и остальные правила доступа не меняются.

Подтверждённые стандартные MVC-ошибки имеют безопасный ApiError: неподдерживаемый метод — 405 METHOD_NOT_ALLOWED
с заголовками Spring (включая Allow); отсутствующий ресурс — 404 NOT_FOUND; неподдерживаемый Content-Type — 415 UNSUPPORTED_MEDIA_TYPE.
Неприемлемый Accept сохраняет стандартный 406. Ошибки параметров остаются 400, доступа 401/403, конфликты 409;
неожиданные внутренние исключения остаются 500 INTERNAL_ERROR без текста исключения и секретов.

## Текущее состояние

**Реализовано:**

- Spring Boot-проект и Maven Wrapper.
- Базовая вертикальная архитектура.
- PostgreSQL 18.6 через Docker Compose.
- JDBC-подключение и настройки Hibernate.
- Успешно применённые миграции Flyway V1, V2 и V3.
- 13 предметных таблиц и ограничения целостности.
- 13 JPA-сущностей, проверенных на PostgreSQL с `ddl-auto=validate`.
- 13 репозиториев и откатываемые PostgreSQL-проверки запросов.
- `GET /api/health`.
- Серверная авторизация, регистрация представителей и профиль пользователя.
- `GET /api/workday` — сводка дня для диспетчера, модельные часы и подтверждённые назначения.
- `GET /api/orders` — фильтруемый список заявок с пагинацией и данными подтверждённого назначения.
- `GET /api/orders/{id}` — карточка с проверкой владельца и разрешёнными пользователю алертами.
- `GET /api/couriers` — список представителей с фильтрами, пагинацией и счётчиками подтверждённых назначений/открытых алертов.
- `GET /api/couriers/{id}/schedule` — полное расписание с подтверждённым маршрутом, остановками, геометрией и проверкой владельца.
- `GET /api/workday/map` — общая карта дня для диспетчера с контрактными проекциями и nullable-геометрией маршрутов.
- `GET /api/alerts` — список одиночных и групповых алертов с поиском, фильтрами, пагинацией и полными резолюциями.
- `GET /api/history` — история бизнес-событий с семью UI-категориями, SQL-фильтрацией и пагинацией.
- Базовые проверки конфигурации.

**Пока не реализовано:**

- Бизнес-сервисы заявок, планирования и исполнения.
- CRUD API для заявок, представителей и офисов.
- Бизнес-логика статусов.
- Взаимодействие с Python-оптимизатором.
- Проверка и активация планов.
- Разграничение прав PostgreSQL для Python.
- Симуляция и офлайн-синхронизация.
