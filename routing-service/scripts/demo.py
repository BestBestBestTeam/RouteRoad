"""Демо: маленький сценарий, оба алгоритма, вывод в консоль.

Запуск из routing-service:  python scripts/demo.py
Внешних зависимостей нет — только пакет app и стандартная библиотека.
"""
from __future__ import annotations

import sys
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from uuid import UUID

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))  # пакет app рядом

from app.core.greedy import GreedySolver
from app.core.model import (
    Algorithm, Courier, Office, Order, Problem, SolverConfig,
)
from app.core.ortools_solver import OrToolsSolver
from app.core.time_matrix import HaversineMatrix

TZ = timezone(timedelta(hours=7))  # Новосибирск
DAY = date(2026, 11, 9)


def ts(hour: int, minute: int = 0) -> datetime:
    return datetime(DAY.year, DAY.month, DAY.day, hour, minute, tzinfo=TZ)


def build_problem() -> Problem:
    office = Office(id=UUID("11111111-1111-1111-1111-111111111111"),
                    name="Офис Центральный", lat=55.030, lon=82.920)
    c1 = Courier(id=UUID("22222222-2222-2222-2222-222222222222"), name="Анна",
                 office_id=office.id, shift_start=ts(9), shift_end=ts(18))
    c2 = Courier(id=UUID("33333333-3333-3333-3333-333333333333"), name="Борис",
                 office_id=office.id, shift_start=ts(9), shift_end=ts(15))
    spots = [
        ("Иванова", "ул. Кирова, 12", ts(10), ts(12), 1800, 55.041, 82.937),
        ("Петров", "ул. Ленина, 5", ts(10), ts(11), 900, 55.022, 82.905),
        ("Сидорова", "ул. Советская, 30", ts(11), ts(13), 2700, 55.055, 82.960),
        ("Кузнецов", "пр. Маркса, 8", ts(13), ts(15), 1800, 55.013, 82.883),
        ("Попова", "ул. Мичурина, 17", ts(14), ts(17), 3600, 55.072, 82.860),
        ("Орлова", "ул. Фрунзе, 2", ts(9, 30), ts(10), 900, 55.028, 82.915),
    ]
    orders = [
        Order(id=UUID(int=0x44 + i), client_name=name, address=address,
              lat=lat, lon=lon, window_from=wf, window_to=wt,
              service_duration_s=dur, service_date=DAY, version=1)
        for i, (name, address, wf, wt, dur, lat, lon) in enumerate(spots)
    ]
    return Problem(
        plan_date=DAY, now=ts(8), algorithm=Algorithm.GREEDY,
        config=SolverConfig(speed_kmh=40.0), offices=[office],
        couriers=[c1, c2], orders=orders,
    )


def main() -> None:
    problem = build_problem()
    matrix = HaversineMatrix(problem.config.speed_kmh)
    names = {c.id: c.name for c in problem.couriers}
    clients = {o.id: f"{o.client_name} ({o.address})" for o in problem.orders}
    solvers = [GreedySolver(), OrToolsSolver(time_limit_s=3)]
    for solver in solvers:
        solution = solver.solve(problem, matrix)
        print(f"\n=== {solver.name.value} ({solution.solve_time_ms} мс) ===")
        for route in solution.routes:
            print(f"Маршрут: {names[route.courier_id]}")
            for stop in route.stops:
                when = f"{stop.arrival_eta:%H:%M}-{stop.departure_eta:%H:%M}"
                if stop.order_id is not None:
                    viol = (f", нарушение окна {stop.window_violation_s // 60} мин"
                            if stop.window_violation_s else "")
                    dist = f", {stop.distance_from_prev_m} м"
                    print(f"  {stop.sequence}. {clients[stop.order_id]}: "
                          f"{when}{dist}{viol}")
                else:
                    print(f"  {stop.sequence}. офис: {when}")
        if solution.unassigned:
            print("Неназначенные:")
            for a in solution.unassigned:
                print(f"  {clients[a.order_id]}: {a.reason_code.value} — "
                      f"{a.reason_text}")
        m = solution.metrics
        print(f"Метрики: путь {m.total_distance_m} м, нарушений окон "
              f"{m.violated_windows}, неназначенных {m.unassigned_count}, "
              f"расчёт {m.solve_time_ms} мс")


if __name__ == "__main__":
    main()
