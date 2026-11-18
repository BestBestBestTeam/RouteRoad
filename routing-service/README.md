# routing-service

Сервис назначения заявок полевым представителям RouteRoad (Python-сторона,
все данные синтетические). Ядро принимает сценарий дня — офисы, представителей
со сменами и заявки с окнами — и возвращает маршруты и назначения: кто, куда,
когда и почему заявка не назначена.

Два алгоритма на одной модели: `greedy` (дешёвая вставка заявка за заявкой)
и `ortools` (VRPTW через OR-Tools RoutingModel). Числа обоих пересчитывает
общий код (`app/core/common.py`), поэтому остановки, ETA, расстояния и
нарушения окон сходятся с валидатором. Контракт модели и правил —
[docs/contract-python.md](docs/contract-python.md).

## Установка

Требуется Python >= 3.11.

```bash
cd routing-service
python -m venv .venv && .venv/Scripts/activate   # Windows
pip install -e .[dev]                            # пакет + pytest + mypy
```

## Запуск

Быстрые тесты (полная матрица случайных сценариев помечена `slow` и по
умолчанию не прогоняется):

```bash
python -m pytest -q
```

Полный набор случайных сценариев (seeds 1-5 × 3 размера × 3 ширины окон):

```bash
python -m pytest -m slow
```

Проверка типов:

```bash
python -m mypy app
```

Демо — маленький сценарий (2 представителя, 6 заявок), оба алгоритма,
маршруты с временами и метрики в консоль:

```bash
python scripts/demo.py
```

В консоли Windows кириллицу может портить кодовая страница — запускайте
с `PYTHONIOENCODING=utf-8`.

Статистика сложности полных случайных сценариев (только greedy; по ширинам
окон — доля сценариев с неназначенными заявками и нарушениями окон):

```bash
python scripts/scenario_stats.py
```

HTTP-эндпоинтов и базы данных пока нет: сейчас только ядро (`app.core`),
сервисный слой — следующий этап.

## Структура

```
app/core/
    model.py           — модели Problem/Solution (Pydantic, по контракту)
    time_matrix.py     — времена и расстояния (единственный источник)
    validator.py       — self-валидация решения (11 кодов нарушений)
    common.py          — общее для солверов: остановки, отсечение, тексты
    greedy.py          — солвер дешёвой вставки
    ortools_solver.py  — солвер VRPTW (OR-Tools)
    solver.py          — протокол солвера
docs/
    contract-python.md — контракт ядра (модель, правила, reason codes)
    contract-api.md    — контракт API (не редактировать)
scripts/
    demo.py            — демо обоих алгоритмов в консоли
    scenario_stats.py  — статистика сложности случайных сценариев
tests/
    factories.py        — фабрики модели
    random_problems.py  — детерминированный генератор случайных сценариев
    test_model.py, test_time_matrix.py, test_validator.py,
    test_metrics.py     — модель, матрица, валидатор, метрики
    test_greedy.py, test_ortools.py, test_random.py,
    test_edge_cases.py  — солверы: сценарии, инварианты, границы
```
