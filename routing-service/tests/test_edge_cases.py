"""Граничные сценарии: оба солвера обязаны давать валидное решение
(или честно неназначать) там, где выбор нечего или некого."""
from __future__ import annotations

from app.core.greedy import GreedySolver
from app.core.model import Algorithm, Problem, ReasonCode, Solution
from app.core.ortools_solver import OrToolsSolver
from app.core.time_matrix import HaversineMatrix
from app.core.validator import validate_solution
from tests.factories import MONDAY, make_courier, make_office, make_order, ts
from tests.test_greedy import make_problem

TIME_LIMIT_S = 1


def solve_both(problem: Problem) -> list[Solution]:
    matrix = HaversineMatrix(problem.config.speed_kmh)
    solutions = [GreedySolver().solve(problem, matrix)]
    solver = OrToolsSolver(time_limit_s=TIME_LIMIT_S)
    solutions.append(solver.solve(problem, matrix))
    assert not solver.last_run_failed
    for solution in solutions:
        assert validate_solution(problem, solution, matrix) == []
    return solutions


def codes_by_order(solution: Solution, order) -> ReasonCode:
    return next(a.reason_code for a in solution.assignments
                if a.order_id == order.id)


def test_no_orders() -> None:
    office = make_office()
    problem = make_problem([office], [make_courier(office, "C")], [])
    for solution in solve_both(problem):
        assert solution.routes == []
        assert solution.assignments == []


def test_no_couriers() -> None:
    office = make_office()
    order = make_order("O", ts(9), ts(10), 600, 55.7555, 37.6225)
    problem = make_problem([office], [], [order])
    for solution in solve_both(problem):
        assert solution.routes == []
        assert codes_by_order(solution, order) == ReasonCode.NO_CAPACITY


def test_all_couriers_unavailable() -> None:
    office = make_office()
    order = make_order("O", ts(9), ts(10), 600, 55.7555, 37.6225)
    couriers = [make_courier(office, "C1", is_available=False),
                make_courier(office, "C2", is_available=False)]
    problem = make_problem([office], couriers, [order])
    for solution in solve_both(problem):
        assert solution.routes == []
        unassigned = solution.assignments[0]
        assert unassigned.reason_code == ReasonCode.NO_CAPACITY
        assert "нет доступных представителей" in unassigned.reason_text


def test_now_after_shift_start() -> None:
    """Смена началась в 09:00, планируем в 12:00: маршрут стартует из now."""
    office = make_office()
    order = make_order("O", ts(13), ts(14), 600, 55.7555, 37.6225)
    problem = make_problem([office], [make_courier(office, "C")], [order])
    problem = problem.model_copy(update={"now": ts(12)})
    for solution in solve_both(problem):
        assert len(solution.routes) == 1
        office_stop = solution.routes[0].stops[0]
        assert office_stop.arrival_eta == ts(12)  # не раньше now
        assert codes_by_order(solution, order) in (
            ReasonCode.NEAREST, ReasonCode.OPTIMIZED
        )


def test_now_after_everyones_shift_end() -> None:
    office = make_office()
    order = make_order("O", ts(13), ts(14), 600, 55.7555, 37.6225)
    short = make_courier(office, "C", shift_end=ts(12))
    problem = make_problem([office], [short], [order])
    problem = problem.model_copy(update={"now": ts(13)})
    for solution in solve_both(problem):
        assert solution.routes == []
        assert codes_by_order(solution, order) == ReasonCode.NO_CAPACITY


def test_all_orders_unreachable() -> None:
    """~550 км при 40 км/ч: доехать в окно 09:00-09:30 невозможно."""
    office = make_office()
    orders = [
        make_order(f"O{i}", ts(9), ts(9, 30), 600, 60.0 + i * 0.01, 37.6180)
        for i in range(3)
    ]
    problem = make_problem([office], [make_courier(office, "C")], orders)
    for solution in solve_both(problem):
        assert solution.routes == []
        for order in orders:
            assert codes_by_order(solution, order) == ReasonCode.WINDOW_INFEASIBLE
