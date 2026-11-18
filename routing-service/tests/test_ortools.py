"""Этап 3: OR-Tools-солвер. Детерминизма нет — сравниваем только инварианты
(валидатор, коды причин, метрики), а не побитово одинаковые маршруты."""
from __future__ import annotations

import pytest

from app.core.greedy import GreedySolver
from app.core.model import Algorithm, Problem, ReasonCode, SolverConfig
from app.core.ortools_solver import OrToolsSolver
from app.core.time_matrix import HaversineMatrix
from app.core.validator import validate_solution
from tests.factories import MONDAY, make_courier, make_office, make_order, ts

# Лимит для тестов: GLS всегда выбирает его целиком, дефолт солвера (5 с) не
# проверяем — только инварианты решения.
TEST_TIME_LIMIT_S = 1


def make_problem(offices, couriers, orders, excluded=()) -> Problem:
    return Problem(
        plan_date=MONDAY,
        now=ts(8),
        algorithm=Algorithm.ORTOOLS,
        config=SolverConfig(speed_kmh=40.0),
        offices=offices,
        couriers=couriers,
        orders=orders,
        excluded_pairs=list(excluded),
    )


def solve(problem: Problem, time_limit_s: int = TEST_TIME_LIMIT_S):
    solver = OrToolsSolver(time_limit_s=time_limit_s)
    solution = solver.solve(problem, HaversineMatrix(problem.config.speed_kmh))
    assert not solver.last_run_failed  # во всех сценариях решение есть
    return solution


def assignment(solution, order):
    return next(a for a in solution.assignments if a.order_id == order.id)


def unassigned_codes(solution) -> dict:
    return {
        a.order_id: a.reason_code
        for a in solution.assignments
        if a.courier_id is None
    }


@pytest.fixture()
def smoke_problem() -> Problem:
    """Тот же сценарий, что в смоук-прогоне и у greedy: два офиса, два
    представителя (у C2 смена до 11:00), пять заявок, o5 недостижима."""
    office = make_office()
    far_office = make_office(lat_shift=0.2, name="Office 2")
    c1 = make_courier(office, "C1")
    c2 = make_courier(far_office, "C2", shift_end=ts(11))
    o1 = make_order("O1", ts(9), ts(9, 30), 900, 55.7555, 37.6225)
    o2 = make_order("O2", ts(9, 30), ts(10), 1800, 55.7600, 37.6090)
    o3 = make_order("O3", ts(13), ts(23), 600, 55.7600, 37.6270)
    o4 = make_order("O4", ts(8), ts(8, 10), 1800, 55.9555, 37.6180)  # у офиса 2
    o5 = make_order("O5", ts(8), ts(8, 5), 600, 56.2510, 37.6180)  # ~33 км от всех
    return make_problem([office, far_office], [c1, c2], [o1, o2, o3, o4, o5])


def test_smoke_scenario_passes_validator(smoke_problem: Problem) -> None:
    solution = solve(smoke_problem)
    assert validate_solution(smoke_problem, solution, HaversineMatrix(40.0)) == []
    assert solution.metrics.unassigned_count == 1
    late = next(a for a in solution.assignments if a.courier_id is None)
    assert late.reason_code == ReasonCode.WINDOW_INFEASIBLE
    assert "приедет к 08:50" in late.reason_text
    assert "окно закрывается в 08:05" in late.reason_text
    for a in solution.assignments:
        if a.courier_id is not None:
            assert a.reason_code == ReasonCode.OPTIMIZED
            assert "план OR-Tools" in a.reason_text


def test_shift_split_scenario_passes_validator() -> None:
    """Один офис, у C2 смена до 11:00: вечерняя o3 физически только за C1."""
    office = make_office()
    c1 = make_courier(office, "C1")
    c2 = make_courier(office, "C2", shift_end=ts(11))
    o1 = make_order("O1", ts(9), ts(9, 30), 900, 55.7555, 37.6225)
    o2 = make_order("O2", ts(9, 30), ts(10), 1800, 55.7600, 37.6090)
    o3 = make_order("O3", ts(13), ts(23), 600, 55.7600, 37.6270)
    o4 = make_order("O4", ts(8), ts(8, 30), 3600, 56.0510, 37.9180)  # ~30 км
    problem = make_problem([office], [c1, c2], [o1, o2, o3, o4])
    solution = solve(problem)
    assert validate_solution(problem, solution, HaversineMatrix(40.0)) == []
    assert assignment(solution, o3).courier_id == c1.id  # смена C2 к 13:00 закрыта
    assert unassigned_codes(solution) == {o4.id: ReasonCode.WINDOW_INFEASIBLE}


def test_one_second_limit_still_valid(smoke_problem: Problem) -> None:
    solution = solve(smoke_problem, time_limit_s=1)
    assert validate_solution(smoke_problem, solution, HaversineMatrix(40.0)) == []


def test_excluded_pair_is_never_assigned() -> None:
    near_office = make_office(lat_shift=0.0045, lon_shift=0.0045, name="A")
    far_office = make_office(lat_shift=0.05, name="B")
    order = make_order("O", ts(9), ts(10), 600, 55.7555, 37.6225)
    c1, c2 = make_courier(near_office, "C1"), make_courier(far_office, "C2")
    problem = make_problem([near_office, far_office], [c1, c2], [order])
    assert assignment(solve(problem), order).courier_id == c1.id
    problem2 = problem.model_copy(update={"excluded_pairs": [(order.id, c1.id)]})
    forced = solve(problem2)
    assert assignment(forced, order).courier_id == c2.id
    assert validate_solution(problem2, forced, HaversineMatrix(40.0)) == []


def test_unavailable_courier_gets_no_route() -> None:
    office = make_office()
    c1 = make_courier(office, "C1", is_available=False)
    c2 = make_courier(office, "C2")
    order = make_order("O", ts(9), ts(10), 600, 55.7555, 37.6225)
    problem = make_problem([office], [c1, c2], [order])
    solution = solve(problem)
    assert [route.courier_id for route in solution.routes] == [c2.id]
    assert validate_solution(problem, solution, HaversineMatrix(40.0)) == []


def test_unassigned_codes_match_greedy(smoke_problem: Problem) -> None:
    """На одном сценарии коды неназначенных заявок у обоих солверов одинаковы."""
    matrix = HaversineMatrix(40.0)
    greedy = GreedySolver().solve(smoke_problem, matrix)
    ortools = solve(smoke_problem)
    assert unassigned_codes(greedy) == unassigned_codes(ortools)
    for a_g, a_o in zip(greedy.assignments, ortools.assignments):
        if a_g.courier_id is None:
            assert a_g.reason_text == a_o.reason_text  # тексты общие из common.py


def test_shift_full_code_matches_greedy() -> None:
    office = make_office()
    courier = make_courier(office, "C", shift_end=ts(9))
    order = make_order("O", ts(8, 30), ts(9), 2400, 55.7555, 37.6225)
    problem = make_problem([office], [courier], [order])
    matrix = HaversineMatrix(40.0)
    greedy = GreedySolver().solve(problem, matrix)
    ortools = solve(problem)
    assert unassigned_codes(greedy) == unassigned_codes(ortools) == {
        order.id: ReasonCode.NO_CAPACITY
    }
    assert "не помещается ни в одну смену" in assignment(ortools, order).reason_text


def test_no_candidates_code_matches_greedy() -> None:
    office = make_office()
    offline = make_courier(office, "C", is_available=False)
    order = make_order("O", ts(9), ts(10), 600, 55.7555, 37.6225)
    problem = make_problem([office], [offline], [order])
    matrix = HaversineMatrix(40.0)
    greedy = GreedySolver().solve(problem, matrix)
    ortools = solve(problem)
    assert unassigned_codes(greedy) == unassigned_codes(ortools) == {
        order.id: ReasonCode.NO_CAPACITY
    }
    assert "нет доступных представителей" in assignment(ortools, order).reason_text


def test_shift_overflow_keeps_first_order() -> None:
    """Регрессия: дуга в фиктивный конец должна стоить службу последнего узла.

    Без этого End-кумулятива не видела конца последней встречи, OR-Tools
    брал o2, build_stops отказывал — и весь маршрут улетал в NO_CAPACITY.
    """
    office = make_office()
    courier = make_courier(office, "C", shift_end=ts(9))
    o1 = make_order("O1", ts(8), ts(8, 30), 600, 55.7555, 37.6225)
    o2 = make_order("O2", ts(8, 40), ts(8, 50), 1800, 55.7600, 37.6225)
    problem = make_problem([office], [courier], [o1, o2])
    solution = solve(problem)
    assert validate_solution(problem, solution, HaversineMatrix(40.0)) == []
    assert assignment(solution, o1).courier_id == courier.id
    route = next(r for r in solution.routes if r.courier_id == courier.id)
    assert [s.order_id for s in route.stops[1:]] == [o1.id]  # маршрут не пропал
    late = assignment(solution, o2)
    assert late.reason_code == ReasonCode.NO_CAPACITY
    assert "не помещается ни в одну смену" in late.reason_text


def test_ortools_beats_greedy_on_distance() -> None:
    """Greedy минимизирует время маршрута (с ожиданием) и фиксирует порядок
    по window_to; OR-Tools минимизирует дорогу и переставляет заявки глобально.
    Здесь greedy оставляет длинный маятник o0->o1->o2.

    Сравнение честное: оба назначили всё, нарушений окон у обоих ноль,
    офис один — дело только в порядке и составе маршрутов.
    """
    office = make_office()
    c1, c2 = make_courier(office, "C1"), make_courier(office, "C2")
    o0 = make_order("North 1", ts(9), ts(11), 1800, 55.8836, 37.6337)
    o1 = make_order("West", ts(10), ts(12), 1800, 55.7887, 37.5672)
    o2 = make_order("North 2", ts(13), ts(15), 3600, 55.8723, 37.6487)
    problem = make_problem([office], [c1, c2], [o0, o1, o2])
    matrix = HaversineMatrix(40.0)
    greedy = GreedySolver().solve(problem, matrix)
    ortools = solve(problem)
    assert validate_solution(problem, greedy, matrix) == []
    assert validate_solution(problem, ortools, matrix) == []
    assert greedy.metrics.unassigned_count == ortools.metrics.unassigned_count == 0
    assert greedy.metrics.violated_windows == ortools.metrics.violated_windows == 0
    assert ortools.metrics.total_distance_m < greedy.metrics.total_distance_m
