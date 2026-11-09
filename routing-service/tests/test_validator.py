"""По одному тесту на каждую из шести проверок плюс корректное решение.
Мутаторы — model_copy поверх заведомо правильного решения, поэтому ломается
только проверяемое правило."""
from __future__ import annotations

from datetime import timedelta

from app.core.model import Solution
from app.core.validator import ViolationCode, validate_solution
from tests.factories import Scenario, ts


def codes(violations) -> set[ViolationCode]:
    return {violation.code for violation in violations}


def validate(
    scenario: Scenario, solution: Solution | None = None, problem=None
) -> list:
    return validate_solution(
        problem or scenario.problem,
        solution or scenario.solution,
        scenario.matrix,
    )


def solution_with(scenario: Scenario, routes: list | None = None) -> Solution:
    if routes is None:
        return scenario.solution
    return scenario.solution.model_copy(update={"routes": routes})


def route_with(route, stops: list):
    return route.model_copy(update={"stops": stops})


def test_scenario_is_valid(scenario: Scenario) -> None:
    assert validate(scenario) == []


def test_route_stays_inside_shift(scenario: Scenario) -> None:
    # Смена 09:00-13:00: выезд в 08:00 раньше открытия, поездка до o3
    # заканчивается в 13:10 — позже закрытия.
    narrow = scenario.c1.model_copy(
        update={"shift_start": ts(9), "shift_end": ts(13)}
    )
    problem2 = scenario.problem.model_copy(update={"couriers": [narrow, scenario.c2]})
    found = codes(validate(scenario, problem=problem2))
    assert {ViolationCode.SHIFT_START_VIOLATION, ViolationCode.SHIFT_END_VIOLATION} <= found


def test_times_follow_the_matrix(scenario: Scenario) -> None:
    office_stop, order_stop, last = scenario.r1.stops
    wrong = order_stop.model_copy(
        update={
            "arrival_eta": office_stop.departure_eta,  # без дорожной паузы
            "departure_eta": office_stop.departure_eta + timedelta(seconds=600),
            "distance_from_prev_m": 1,
        }
    )
    broken = solution_with(
        scenario, routes=[route_with(scenario.r1, [office_stop, wrong, last]), scenario.r2]
    )
    found = codes(validate(scenario, broken))
    assert {
        ViolationCode.ARRIVAL_TOO_EARLY,
        ViolationCode.DEPARTURE_MISMATCH,
        ViolationCode.DISTANCE_MISMATCH,
    } <= found


def test_window_violation_field_mismatch(scenario: Scenario) -> None:
    first, stop, last = scenario.r1.stops
    faked = stop.model_copy(update={"window_violation_s": 60})
    broken = solution_with(
        scenario, routes=[route_with(scenario.r1, [first, faked, last]), scenario.r2]
    )
    assert ViolationCode.WINDOW_VIOLATION_MISMATCH in codes(validate(scenario, broken))


def test_each_order_has_exactly_one_assignment(scenario: Scenario) -> None:
    # Без решения o3 и с двумя решениями про o1 сразу.
    assignments = [
        a
        for a in scenario.solution.assignments
        if a.order_id != scenario.o3.id
    ] + [
        next(a for a in scenario.solution.assignments if a.order_id == scenario.o1.id)
    ]
    broken = scenario.solution.model_copy(update={"assignments": assignments})
    found = codes(validate(scenario, broken))
    assert {ViolationCode.MISSING_ASSIGNMENT, ViolationCode.DUPLICATE_ASSIGNMENT} <= found


def test_assignment_must_point_at_the_actual_route(scenario: Scenario) -> None:
    # o1 стоит в r1 курьера c1, а назначение ссылается на r2.
    original = next(
        a for a in scenario.solution.assignments if a.order_id == scenario.o1.id
    )
    wrong = original.model_copy(update={"route_id": scenario.r2.id})
    assignments = [
        wrong if a.order_id == scenario.o1.id else a
        for a in scenario.solution.assignments
    ]
    broken = scenario.solution.model_copy(update={"assignments": assignments})
    assert ViolationCode.ASSIGNMENT_ROUTE_MISMATCH in codes(validate(scenario, broken))


def test_only_available_couriers_are_assigned(scenario: Scenario) -> None:
    declined = scenario.c1.model_copy(update={"is_available": False})
    problem2 = scenario.problem.model_copy(update={"couriers": [declined, scenario.c2]})
    assert ViolationCode.UNAVAILABLE_COURIER in codes(validate(scenario, problem=problem2))
