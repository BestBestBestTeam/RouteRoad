"""Расстояния и время в пути между точками: единственный источник правды.

Солверы и валидатор обязаны вызывать *один и тот же* экземпляр матрицы: если у
каждого будет своя формула, валидатор завернёт правильные решения из-за
расхождений в несколько метров. Целые числа держат интерфейс готовым к OR-Tools
(он требует целые стоимости); время округляется вверх, чтобы ETA никогда не был
оптимистичнее дороги. OSRM позже — это просто ещё один класс с TravelTimeMatrix.
"""
from __future__ import annotations

import math
from typing import TYPE_CHECKING, Protocol

if TYPE_CHECKING:
    from app.core.model import Problem

EARTH_RADIUS_M = 6_371_000.0


class SupportsLatLon(Protocol):
    @property
    def lat(self) -> float: ...

    @property
    def lon(self) -> float: ...


def haversine_m(a: SupportsLatLon, b: SupportsLatLon) -> float:
    lat1, lat2 = math.radians(a.lat), math.radians(b.lat)
    dlat = lat2 - lat1
    dlon = math.radians(b.lon - a.lon)
    h = (
        math.sin(dlat / 2.0) ** 2
        + math.cos(lat1) * math.cos(lat2) * math.sin(dlon / 2.0) ** 2
    )
    return 2.0 * EARTH_RADIUS_M * math.asin(math.sqrt(h))


class TravelTimeMatrix(Protocol):
    def distance_m(self, a: SupportsLatLon, b: SupportsLatLon) -> int: ...

    def travel_time_s(self, a: SupportsLatLon, b: SupportsLatLon) -> int: ...


class HaversineMatrix:
    def __init__(self, speed_kmh: float) -> None:
        if speed_kmh <= 0:
            raise ValueError("speed_kmh must be positive")
        self._speed_m_per_s = speed_kmh * 1000.0 / 3600.0

    def distance_m(self, a: SupportsLatLon, b: SupportsLatLon) -> int:
        return round(haversine_m(a, b))

    def travel_time_s(self, a: SupportsLatLon, b: SupportsLatLon) -> int:
        return math.ceil(haversine_m(a, b) / self._speed_m_per_s)


def matrix_for(problem: "Problem") -> HaversineMatrix:
    return HaversineMatrix(problem.config.speed_kmh)
