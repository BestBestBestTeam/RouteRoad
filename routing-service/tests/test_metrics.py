"""Метрики и агрегаты маршрута выводятся из данных, а не хранятся."""
from __future__ import annotations

from app.core.validator import validate_solution
from tests.factories import Scenario, build_route, ts


def test_route_totals_follow_from_stops(scenario: Scenario) -> None:
    matrix = scenario.matrix
    expected = (
        matrix.distance_m(scenario.office, scenario.o1)
        + matrix.distance_m(scenario.o1, scenario.o3)
        + matrix.distance_m(scenario.office, scenario.o2)
    )
    assert scenario.solution.metrics.total_distance_m == expected
    assert scenario.solution.metrics.violated_windows == 0
    assert scenario.solution.metrics.unassigned_count == 1
    assert scenario.solution.metrics.solve_time_ms == 42


def test_late_meeting_counts_violations(scenario: Scenario) -> None:
    # При выезде в 10:00 встреча 09:00-09:30 начинается на 30 мин + дорога позже.
    late = build_route(
        scenario.c1, scenario.office, [scenario.o1], scenario.matrix, start=ts(10)
    )
    travel_s = scenario.matrix.travel_time_s(scenario.office, scenario.o1)
    assert late.stops[1].window_violation_s == 1800 + travel_s
    assert late.violated_windows == 1


def test_validator_agrees_with_a_clean_scenario(scenario: Scenario) -> None:
    # Тест метрик выше имеет смысл только если сценарий валиден.
    assert validate_solution(scenario.problem, scenario.solution, scenario.matrix) == []
