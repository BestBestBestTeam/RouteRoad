# Контракт Java ↔ Python: сервис расчётов

Версия от 10.11.2026. Сущности, ручки фронтенда и синхронизация описаны в
`contract-api.md`, здесь только то, что касается сервиса расчётов (Python):
что он читает и пишет, как вызывается, какие решения принимает.
При расхождении полей таблиц главным считается `contract-api.md`.

## 1. Принципы

- Java владеет схемой БД, основными данными, модельным временем, синхронизацией,
  разрешением конфликтов и активацией планов. Миграции ведёт Java.
- Python читает общую БД напрямую и пишет только таблицы плана:
  `planning_run`, `route`, `route_stop`, `assignment`. Статусы заявок Python не меняет.
- Формат: JSON, `snake_case`, время ISO 8601 с зоной (`2026-10-20T09:30:00+03:00`),
  идентификаторы UUID, координаты `lat`/`lon` (WGS84), расстояния в метрах,
  длительности в секундах.
- Версии (`version`, `plan_version`, `source_order_version`, `base_version`) имеют тип
  `Long` (`bigint` в БД) и передаются в JSON десятичными строками: `"42"`.
- MVP работает в рамках одного модельного дня.
- Представитель работает по всему городу и привязан к своему офису.
- Python-расчётчик вызывается только Java. Фронтенд и симулятор к нему не обращаются.

## 2. Что Python читает и пишет

| Таблица | Python |
| --- | --- |
| `service_order`, `courier`, `office` | читает |
| `route`, `route_stop`, `assignment`, `planning_run` | читает и добавляет записи |
| `business_event` | читает (ограничения после отказов), см. раздел 8 |
| `app_user`, `api_command`, `alert`, `alert_order`, `scenario_state` | не читает |

Права в БД: `SELECT` на разрешённые таблицы, `INSERT` на `planning_run`, `route`,
`route_stop`, `assignment`. `UPDATE` и `DELETE` не нужны: старые записи остаются как история.

### 2.1. Что расчёт берёт из заявки и представителя

Заявка (`service_order`): `id`, `client_name`, `address`, `lat`, `lon`, `window_from`,
`window_to`, `service_duration_s` (> 0), `service_date`, `status`, `version`.

- `window_from`, `window_to` — `timestamp` с зоной. **`window_to` — крайний срок
  начала встречи.** Встреча может закончиться позже, если укладывается в смену.
  Раннее прибытие нарушением не считается: представитель ждёт до `window_from`.
  Нарушение окна: `window_violation_s = max(0, начало_встречи − window_to)`.
- В расчёт попадают заявки в статусах `NEW`, `ASSIGNED`, `UNASSIGNED`, `EN_ROUTE`,
  `ARRIVED`. Заявки в `IN_PROGRESS`, `COMPLETED`, `CANCELLED`, `RESCHEDULED`
  расчёт не трогает.

Представитель (`courier`): `id`, `name`, `office_id`, `shift_start`, `shift_end`,
`position`, `is_available`.

- Назначать можно, если `is_available = true` и момент внутри смены
  (`shift_start <= время < shift_end`). `is_available` смену не включает.
- `is_available` действует на весь модельный день сценария (в MVP день один).

## 3. Таблицы плана (пишет Python)

Значения `plan_version` в записях ссылаются на `planning_run`.

### `planning_run` — запуск расчёта

| Поле | Тип | Описание |
| --- | --- | --- |
| `plan_version` | Long | Глобальный номер запуска (sequence). Первичный ключ |
| `request_id` | UUID | Идентификатор запроса Java. Уникален: при повторе возвращается прежний результат |
| `run_type` | перечисление | `ASSIGN` или `REASSIGN` |
| `event_type` | перечисление | Тип события для `REASSIGN`, иначе `null` |
| `algorithm` | перечисление | `greedy` или `ortools` |
| `plan_date` | дата | День, который считали |
| `model_now` | время | Модельное время запроса (`now`) |
| `created_at` | время | Модельное время создания записи |
| `metrics` | объект | `total_distance_m`, `violated_windows`, `unassigned_count`, `solve_time_ms` |

Каждый запуск `/assign` и `/reassign` создаёт ровно одну запись.

### `route` — маршрут представителя на день

| Поле | Тип | Описание |
| --- | --- | --- |
| `id` | UUID | Идентификатор маршрута |
| `courier_id` | UUID | Чей маршрут |
| `plan_date` | дата | День маршрута |
| `plan_version` | Long | Запуск, в котором создан маршрут |
| `is_active` | да/нет | **Пишет Java.** Python создаёт маршрут с `false` |
| `algorithm` | перечисление | `greedy` или `ortools` |
| `total_distance_m` | целое | Суммарное расстояние |
| `violated_windows` | целое | Число нарушенных окон |
| `created_at` | время | Модельное время построения |

Активный маршрут один на `(courier_id, plan_date)`: `WHERE is_active`. При частичном
пересчёте новые маршруты создаются только у затронутых представителей, у остальных
остаются прежние активные маршруты со своей `plan_version`. Актуальный план дня —
это все активные маршруты, а не маршруты с максимальной версией.

### `route_stop` — остановка маршрута

| Поле | Тип | Описание |
| --- | --- | --- |
| `route_id` | UUID | Маршрут |
| `sequence` | целое | Порядок остановки, с 1. Первичный ключ `(route_id, sequence)` |
| `type` | перечисление | `OFFICE` (в том числе заезд за картами) или `ORDER` |
| `order_id` | UUID | Заявка, если `type = ORDER`, иначе `null` |
| `office_id` | UUID | Офис, если `type = OFFICE`, иначе `null` |
| `arrival_eta`, `departure_eta` | время | Плановые прибытие и отъезд |
| `distance_from_prev_m` | целое | Расстояние от предыдущей остановки; у первой 0 |
| `window_violation_s` | целое | На сколько секунд нарушено окно (0, если не нарушено) |

- Первая остановка — офис. Возвращаться в офис в конце дня не обязательно.
- Время ожидания: `departure_eta = max(arrival_eta, window_from) + service_duration_s`.
- Порядок и ETA хранятся только здесь.
- **Открыто:** при пересчёте посреди дня новый маршрут тоже начинается с офиса.
  Старт от текущей позиции потребует нового типа остановки (`POSITION`); решаем отдельно.

### `assignment` — решение по заявке (журнал)

| Поле | Тип | Описание |
| --- | --- | --- |
| `order_id` | UUID | Заявка |
| `plan_version` | Long | Запуск, в котором принято решение |
| `courier_id` | UUID | Кому назначена; `null`, если не назначена |
| `route_id` | UUID | Маршрут; `null`, если не назначена |
| `source_order_version` | Long | `service_order.version`, по которой считали |
| `reason_code` | перечисление | Код причины |
| `reason_text` | строка | Причина простыми словами на русском, для диспетчера |
| `decided_by` | перечисление | `SYSTEM` или `DISPATCHER` |
| `created_at` | время | Модельное время решения |

- Первичный ключ `(order_id, plan_version)`, отдельного `id` нет. В одном запуске
  по заявке одно решение.
- `courier_id` и `route_id` либо оба заполнены, либо оба `null`.
- Старые записи не удаляются.
- `reason_text` формирует Python, фронтенд показывает его как есть.

## 4. Статусы и коды причин

`OrderStatus`: `NEW`, `ASSIGNED`, `UNASSIGNED`, `EN_ROUTE`, `ARRIVED`, `IN_PROGRESS`,
`COMPLETED`, `RESCHEDULED`, `CANCELLED`. Меняет их только Java.

### Коды причин (`reason_code`)

| Код | Когда ставит Python |
| --- | --- |
| `NEAREST` | Greedy: ближайший подходящий представитель |
| `FREE_WINDOW` | Greedy: есть свободное время в маршруте, окно соблюдается |
| `OPTIMIZED` | Решение OR-Tools |
| `REASSIGNED_AFTER_REFUSAL` | Перераспределение после `COURIER_REFUSED` или `ORDER_REFUSED` |
| `MANUAL_DISPATCHER` | Ручное переназначение (`MANUAL_REASSIGN`), `decided_by = DISPATCHER` |
| `AUTO_AFTER_TIMEOUT` | Автоперераспределение после 10 минут без решения диспетчера |
| `NO_CAPACITY` | Не назначена: смены заполнены |
| `WINDOW_INFEASIBLE` | Не назначена: окно недостижимо |
| `DEFER_NEXT_DAY` | Не назначена: перенос на следующий день |

**Рабочее правило выбора кода для неназначенных (подтвердить с командой):**

- `WINDOW_INFEASIBLE` — заявка недостижима до `window_to` даже на пустом дне:
  ни один доступный представитель не приедет к ней раньше `window_to`.
- `NO_CAPACITY` — по отдельности заявка выполнима, но времени в сменах не хватило.
- `DEFER_NEXT_DAY` — заявка не влезла в смены оставшихся представителей после
  `COURIER_REFUSED`.

**`DEFER_NEXT_DAY`:** Python только пишет `assignment` с `courier_id = null` и этим
кодом, `service_order` не меняет. Java при активации ставит `service_date + 1` и
статус `NEW`. Расчёт следующего дня в MVP не выполняется.

## 5. Запросы расчёта (Python)

### `POST /assign` — назначение и построение маршрутов

Python сам читает состояние из БД, считает и записывает план.

| Поле запроса | Описание |
| --- | --- |
| `request_id` | UUID запроса, создаёт Java |
| `now` | Текущее модельное время |
| `algorithm` | `greedy` или `ortools` |
| `scope.date` | День, который считаем |
| `config.speed_kmh` | Скорость для пересчёта расстояния во времени |
| `config.window_mode` | `soft`: нарушение окна допустимо со штрафом |

| Поле ответа | Описание |
| --- | --- |
| `request_id` | Эхо запроса |
| `plan_version` | Версия записанного плана (строка) |
| `routes_written`, `assignments_written` | Сколько маршрутов и назначений записано |
| `unassigned` | Не назначенные заявки: `order_id`, `reason_code`, `reason_text` |
| `metrics` | `total_distance_m`, `violated_windows`, `unassigned_count`, `solve_time_ms` |

### `POST /reassign` — перераспределение при событии

Запрос: `request_id`, `now`, `algorithm`, `event`. Python читает текущие активные
маршруты, пересчитывает затронутые и записывает только изменения.
Ответ: `request_id`, `plan_version`, `changed_couriers`, `assignments_written`,
`unassigned`, `metrics`.

Поля `event`: `type`, `courier_id`, `order_ids`, `target_courier_id`
(только для `MANUAL_REASSIGN`), `delay_s` (только для `COURIER_DELAYED`,
**предложение**: нужно, чтобы пересчитать ETA).

| `event.type` | Что делает расчёт |
| --- | --- |
| `CLIENT_CANCELLED` | Заявка убирается без перераспределения, маршрут перестраивается только у её представителя |
| `ORDER_REFUSED` | Отказ одной заявки: `courier_id` — отказавшийся, `order_ids` — одна заявка. Заявка перераспределяется, отказавшийся исключается для неё |
| `COURIER_REFUSED` | Заявки представителя (`ASSIGNED`, `EN_ROUTE`, `ARRIVED`) распределяются между свободными, не влезшие получают `DEFER_NEXT_DAY`. Может добавиться заезд в офис за картами. Начатую встречу (`IN_PROGRESS`) не трогаем |
| `COURIER_DELAYED` | Обновляет ETA и проверяет окна. При нарушении алерт создаёт Java |
| `AUTO_REASSIGN_REQUEST` | Диспетчер нажал «перераспределить автоматически» или истёк 10-минутный срок |
| `MANUAL_REASSIGN` | Диспетчер выбрал представителя: `order_ids` и `target_courier_id`, заявка ставится в его маршрут |

**Открыто (предложение):** чтобы различить кнопку диспетчера (`decided_by = DISPATCHER`)
и таймаут (`AUTO_AFTER_TIMEOUT`, `SYSTEM`) у `AUTO_REASSIGN_REQUEST`, нужен признак
в событии, например `by_timeout: true|false`.

## 6. Правила записи плана

1. Python пишет `planning_run`, `route` (с `is_active = false`), `route_stop` и
   `assignment` одной транзакцией.
2. Повторный запрос с тем же `request_id` возвращает результат первого запуска,
   дубликаты не создаются.
3. Перед записью Python сверяет `version` заявок, по которым считал. Если Java успела
   их изменить, ничего не записывается, ответ `CONFLICT` (409), запрос можно повторить.
4. Блокировать строки заявок Python не может (у него только `SELECT`). Окончательная
   проверка при активации на стороне Java: `assignment.source_order_version` сравнивается
   с текущей `service_order.version`, а также проверяются сценарий, доступность и
   запреты после отказов. При несовпадении план не активируется, Java запрашивает пересчёт.
5. После записи Python вызывает Java `POST /plans/ready` с `plan_version`. Java проверяет
   план, в одной транзакции выключает старые маршруты затронутых представителей и включает
   новые, записывает `PLAN_ACTIVATED`, обновляет панель, рассылает маршруты, создаёт алерты.
6. Python при расчёте читает только активные маршруты. Неактивированный план на расчёт
   не влияет.
7. Текущими считаются только назначения из запусков, подтверждённых Java.

## 7. Внутренние вызовы Java ↔ Python

| Вызов | Кто → кому | Назначение |
| --- | --- | --- |
| `POST /assign` | Java → Python | Построение плана |
| `POST /reassign` | Java → Python | Перерасчёт по событию |
| `POST /plans/ready` | Python → Java | План записан, можно проверять и активировать |
| `POST /generate-scenario` | Java → Python | Генерация сценария |

`POST /generate-scenario`: запрос `{date, seed, config}`, ответ `GeneratedScenario`
(`date`, `seed`, `config`, `offices`, `couriers`, `orders`) из `contract-api.md`.
Генератор не пишет в БД: сценарий сохраняет Java. Генератор задаёт согласованные UUID,
`address`, координаты, окна, длительности; исходно у заявок `status = NEW`,
`service_date = date`, `version = "1"`.

## 8. Ограничения после отказов

После `ORDER_REFUSED` отказавшийся представитель не может получить эту же заявку снова,
после `COURIER_REFUSED` он недоступен до конца дня. Расчёт учитывает это при любом
запуске, включая `/assign`, ручное и автоматическое переназначение.

**Открыто:** откуда расчёт получает ограничения.
- Вариант 1 (в `contract-api.md`): Python читает их из `business_event`.
- Вариант 2: Java передаёт их в запросе списком `excluded_pairs: [{order_id, courier_id}]`.

Ядро расчёта принимает список пар в любом случае. Вариант 2 надёжнее: Python не
разбирает `payload` чужой таблицы.

## 9. Ошибки

Единый формат: `{ "error": { "code", "message", "details" } }`.

| Код | HTTP | Когда |
| --- | --- | --- |
| `VALIDATION_ERROR` | 400 | Неверные данные запроса |
| `NOT_FOUND` | 404 | Нет объекта |
| `CONFLICT` | 409 | Версии заявок изменились во время расчёта |
| `INFEASIBLE` | 422 | План построить нельзя |
| `INTERNAL_ERROR` | 500 | Сбой |

## 10. Открытые вопросы

1. Откуда расчёт берёт запрещённые пары: `business_event` или запрос Java (раздел 8).
2. Старт нового маршрута при пересчёте посреди дня: офис или текущая позиция (`POSITION`).
3. Признак таймаута в `AUTO_REASSIGN_REQUEST` (раздел 5).
4. Нужен ли `delay_s` в `event` для `COURIER_DELAYED`.
5. Правило выбора `NO_CAPACITY` / `WINDOW_INFEASIBLE` / `DEFER_NEXT_DAY`: подтвердить.