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
DB_PASSWORD=change_me
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
└── V1__create_initial_schema.sql
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

## Тестирование

```bash
./mvnw clean verify
```

Сейчас реализованы:

- `RouteRoadApplicationTests` — проверка загрузки Spring-контекста.
- `ConfigurationProfilesTests` — проверка конфигурации и изоляции профилей.

Последняя проверка: **BUILD SUCCESS, 8 тестов пройдено**.

Тесты не подключаются к реальной PostgreSQL. Интеграционные тесты отложены на неопределённый срок, существующие заготовки сохранены.

## Текущее состояние

**Реализовано:**

- Spring Boot-проект и Maven Wrapper.
- Базовая вертикальная архитектура.
- PostgreSQL 18.6 через Docker Compose.
- JDBC-подключение и настройки Hibernate.
- Первая применённая миграция Flyway.
- Семь таблиц и ограничения целостности.
- `GET /api/health`.
- Базовые проверки конфигурации.

**Пока не реализовано:**

- JPA-сущности и репозитории.
- CRUD API для заявок, представителей и офисов.
- Бизнес-логика статусов.
- Взаимодействие с Python-оптимизатором.
- Проверка и активация планов.
- Разграничение прав PostgreSQL для Python.
- Симуляция и офлайн-синхронизация.
