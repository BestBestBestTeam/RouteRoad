"""Синтетические сценарии для теста на инварианты: один seed — один Problem.

Всё случайное идёт из random.Random(seed), включая UUID (детерминированный
инт), поэтому результат воспроизводим без хранения состояния.
"""
from __future__ import annotations

import random
from datetime import date, datetime, timedelta, timezone
from math import cos, pi, radians, sin, sqrt
from uuid import UUID

from app.core.model import (
    Algorithm, Courier, Office, Order, Problem, SolverConfig,
)

TZ = timezone(timedelta(hours=7))  # Новосибирск, +07:00
CENTER_LAT, CENTER_LON = 55.030, 82.920
RADIUS_KM = 12.0
PLAN_DATE = date(2026, 11, 9)
SPEED_KMH = 40.0
WIDTH_MIN = {"tight": 30, "medium": 120, "loose": 240}


def _uuid(rng: random.Random) -> UUID:
    return UUID(int=rng.getrandbits(128))


def ts(hour: int, minute: int = 0) -> datetime:
    return datetime(PLAN_DATE.year, PLAN_DATE.month, PLAN_DATE.day,
                    hour, minute, tzinfo=TZ)


def _point_in_circle(rng: random.Random) -> tuple[float, float]:
    """Случайная точка в радиусе RADIUS_KM от центра Новосибирска."""
    r = sqrt(rng.random())           # равномерно по площади
    angle = rng.uniform(0, 2 * pi)
    dlat = RADIUS_KM / 111.0         # 1 градус широты ~111 км
    dlon = RADIUS_KM / (111.0 * cos(radians(CENTER_LAT)))
    return (CENTER_LAT + r * dlat * sin(angle),
            CENTER_LON + r * dlon * cos(angle))


def random_problem(
    seed: int,
    n_orders: int,
    n_couriers: int,
    window_width: str = "medium",
) -> Problem:
    """Офис у центра; смены 09:00-18:00 (у ~20% короче), now = 08:00,
    окна шириной window_width, встречи 15-60 минут."""
    rng = random.Random(seed)
    width = WIDTH_MIN[window_width]
    office = Office(id=_uuid(rng), name="Office 1",
                    lat=CENTER_LAT + rng.uniform(-0.01, 0.01),
                    lon=CENTER_LON + rng.uniform(-0.01, 0.01))
    couriers = []
    for i in range(n_couriers):
        # ~20% представителей работают укороченную смену.
        end = ts(18) if rng.random() >= 0.2 else ts(rng.randrange(12, 18))
        couriers.append(Courier(
            id=_uuid(rng), name=f"C{i + 1}", office_id=office.id,
            shift_start=ts(9), shift_end=end,
        ))
    orders = []
    for i in range(n_orders):
        lat, lon = _point_in_circle(rng)
        # Начало окна — внутри дня; конец не позже 18:00, иначе окно за сменой.
        start_min = rng.randrange(9 * 60, max(9 * 60 + 1, 18 * 60 - width))
        window_from = ts(0) + timedelta(minutes=start_min)
        orders.append(Order(
            id=_uuid(rng), client_name=f"Client {i + 1}",
            address=f"ул. Случайная, {rng.randint(1, 120)}",
            lat=lat, lon=lon,
            window_from=window_from,
            window_to=window_from + timedelta(minutes=width),
            service_duration_s=rng.randrange(15, 61) * 60,
            service_date=PLAN_DATE, version=1,
        ))
    return Problem(
        plan_date=PLAN_DATE, now=ts(8), algorithm=Algorithm.GREEDY,
        config=SolverConfig(speed_kmh=SPEED_KMH), offices=[office],
        couriers=couriers, orders=orders,
    )
