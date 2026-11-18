"""Haversine-матрица: известное расстояние, симметрия, целое время с округлением вверх."""
from __future__ import annotations

import math

import pytest

from app.core.model import Office
from app.core.time_matrix import HaversineMatrix, haversine_m, matrix_for
from tests.factories import build_scenario


def pt(lat: float, lon: float) -> Office:
    # Office здесь выступает просто переносчиком id+имени+координат; матрице
    # нужен любой объект с lat/lon.
    return Office(id="00000000-0000-0000-0000-000000000000", name="p", lat=lat, lon=lon)


def test_same_point_is_zero() -> None:
    matrix = HaversineMatrix(40)
    point = pt(55.751, 37.618)
    assert matrix.distance_m(point, point) == 0
    assert matrix.travel_time_s(point, point) == 0


def test_one_degree_at_equator() -> None:
    matrix = HaversineMatrix(40)
    # R=6371 км, 1 градус = pi/180*R = 111194.9 м.
    assert abs(matrix.distance_m(pt(0, 0), pt(0, 1)) - 111_195) <= 2


def test_distance_is_symmetric() -> None:
    matrix = HaversineMatrix(40)
    a, b = pt(55.751, 37.618), pt(55.76, 37.63)
    assert matrix.distance_m(a, b) == matrix.distance_m(b, a)
    assert matrix.travel_time_s(a, b) == matrix.travel_time_s(b, a)


def test_travel_time_rounds_up() -> None:
    matrix = HaversineMatrix(40)
    a, b = pt(55.751, 37.618), pt(55.7555, 37.6225)
    raw = haversine_m(a, b)
    time_s = matrix.travel_time_s(a, b)
    speed_m_per_s = 40.0 / 3.6
    assert (time_s - 1) * speed_m_per_s < raw <= time_s * speed_m_per_s
    assert math.isclose(time_s, math.ceil(raw / speed_m_per_s))


def test_nonpositive_speed_rejected() -> None:
    with pytest.raises(ValueError):
        HaversineMatrix(0)
    with pytest.raises(ValueError):
        HaversineMatrix(-5)


def test_matrix_for_uses_config_speed() -> None:
    scenario = build_scenario()
    built = matrix_for(scenario.problem)
    reference = HaversineMatrix(scenario.problem.config.speed_kmh)
    assert built.distance_m(scenario.o1, scenario.o2) == reference.distance_m(
        scenario.o1, scenario.o2
    )
