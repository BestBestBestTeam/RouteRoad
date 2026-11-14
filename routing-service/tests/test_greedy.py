"""Этап 2: вставка одной заявки и greedy-солвер целиком."""
from __future__ import annotations

from datetime import timedelta

import pytest

from app.core.greedy import WINDOW_PENALTY_K, GreedySolver, _earliest_arrival, try_insert_order
from app.core.model import Algorithm, Problem, ReasonCode, Solution, SolverConfig
from app.core.time_matrix import HaversineMatrix, TravelTimeMatrix
from app.core.validator import validate_solution
from tests.factories import MONDAY, make_courier, make_office, make_order, ts


def make_problem(offices, couriers, orders, excluded=()) -> Problem:
    return Problem(
        plan_date=MONDAY,
        now=ts(8),
        algorithm=Algorithm.GREEDY,
        config=SolverConfig(speed_kmh=40.0),
        offices=offices,
        couriers=couriers,
        orders=orders,
        excluded_pairs=list(excluded),
    )


def solve(problem: Problem, matrix: TravelTimeMatrix | None = None) -> Solution:
    return GreedySolver().solve(
        problem, matrix or HaversineMatrix(problem.config.speed_kmh)
    )


def assignment(solution: Solution, order) -> object:
    return next(a for a in solution.assignments if a.order_id == order.id)


@pytest.fixture()
def env():
    office = make_office()
    courier = make_courier(office, "C")
    return office, courier, HaversineMatrix(40.0)


def test_insert_into_empty_route(env) -> None:
    office, courier, matrix = env
    order = make_order("O", ts(9), ts(9, 30), 900, 55.7555, 37.6180)
    result = try_insert_order(order, courier, office, [], matrix, ts(8))
    assert result is not None
    assert result.index == 1 and result.extra_violation_s == 0
    assert [stop.order_id for stop in result.stops[1:]] == [order.id]
    assert result.stops[0].arrival_eta == ts(8)


def test_insert_in_middle_beats_tail(env) -> None:
    office, courier, matrix = env
    afternoon = make_order("A", ts(13), ts(23), 600, 55.8010, 37.6180)  # ~5.5 км к северу
    morning = make_order("B", ts(9), ts(9, 15), 600, 55.7555, 37.6225)
    result = try_insert_order(morning, courier, office, [afternoon], matrix, ts(8))
    assert result is not None
    # Вечерняя точка в хвосте означала бы встречу B в ~13:20 вместо окна 9:00-9:15.
    assert result.index == 1 and result.extra_violation_s == 0


def test_order_does_not_fit_in_shift(env) -> None:
    office, _, matrix = env
    short_shift = make_courier(office, "C", shift_end=ts(9))
    order = make_order("O", ts(8, 40), ts(8, 45), 1800, 55.7555, 37.6180)
    assert try_insert_order(order, short_shift, office, [], matrix, ts(8)) is None


def test_soft_window_violation_allowed_but_priced(env) -> None:
    office, courier, matrix = env
    near = make_order("N", ts(8), ts(9), 600, 55.7555, 37.6180)
    far = make_order("F", ts(8), ts(8, 10), 600, 55.8410, 37.6180)  # ~10 км
    result = try_insert_order(far, courier, office, [near], matrix, ts(8))
    assert result is not None  # мягкое окно не запрет
    assert result.extra_violation_s > 0
    # Штраф входит в стоимость, а не только чистое время.
    assert result.cost_s == result.extra_time_s + WINDOW_PENALTY_K * result.extra_violation_s
    assert result.index == 1  # впереди дешевле: нарушение меньше


def test_earliest_arrival_starts_from_own_office() -> None:
    near_office = make_office(name="A")
    far_office = make_office(lat_shift=0.09, name="B")
    late_start = make_courier(near_office, "late", shift_start=ts(9))
    early_start = make_courier(far_office, "early")
    order = make_order("O", ts(12), ts(13), 600, 55.8410, 37.6180)  # у офиса B
    matrix = HaversineMatrix(40.0)
    offices = {near_office.id: near_office, far_office.id: far_office}
    got = _earliest_arrival(order, [late_start, early_start], offices, matrix, ts(8))
    # Раньше всех приедет early: старт 08:00 из своего офиса B, 500 м дороги.
    assert got == ts(8) + timedelta(seconds=matrix.travel_time_s(far_office, order))
    assert _earliest_arrival(order, [], offices, matrix, ts(8)) is None


def test_greedy_output_passes_validator() -> None:
    office, far_office = make_office(), make_office(lat_shift=0.2, name="Office 2")
    c1 = make_courier(office, "C1")
    c2 = make_courier(far_office, "C2", shift_end=ts(11))
    o1 = make_order("O1", ts(9), ts(9, 30), 900, 55.7555, 37.6225)
    o2 = make_order("O2", ts(9, 30), ts(10), 1800, 55.7600, 37.6090)
    o3 = make_order("O3", ts(13), ts(23), 600, 55.7600, 37.6270)
    o4 = make_order("O4", ts(8), ts(8, 10), 1800, 55.9555, 37.6180)  # у офиса 2
    o5 = make_order("O5", ts(8), ts(8, 5), 600, 56.2510, 37.6180)  # ~33 км от всех
    problem = make_problem([office, far_office], [c1, c2], [o1, o2, o3, o4, o5])
    solution = solve(problem)
    assert validate_solution(problem, solution, HaversineMatrix(40.0)) == []
    late = assignment(solution, o5)
    assert late.reason_code == ReasonCode.WINDOW_INFEASIBLE
    # Проверка pre-check: 33 км при 40 км/ч — приезд ~08:50 при окне до 08:05.
    assert "приедет к 08:50" in late.reason_text
    assert "окно закрывается в 08:05" in late.reason_text


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


def test_two_runs_are_identical() -> None:
    office = make_office()
    c1, c2 = make_courier(office, "C1"), make_courier(office, "C2")
    orders = [
        make_order("O1", ts(9), ts(9, 30), 900, 55.7555, 37.6225),
        make_order("O2", ts(9), ts(9, 30), 600, 55.7600, 37.6090),
        make_order("O3", ts(10), ts(10, 30), 600, 55.7600, 37.6270),
        make_order("O4", ts(11), ts(23), 600, 55.7455, 37.6135),
    ]
    problem = make_problem([office], [c1, c2], orders)

    def signature(solution: Solution):
        routes = sorted(solution.routes, key=lambda r: str(r.courier_id))
        return (
            [
                (r.courier_id, [s.order_id for s in r.stops if s.order_id])
                for r in routes
            ],
            [
                (a.order_id, a.courier_id, a.reason_code, a.reason_text)
                for a in solution.assignments
            ],
        )

    assert signature(solve(problem)) == signature(solve(problem))


def test_shift_full_gives_no_capacity() -> None:
    office = make_office()
    courier = make_courier(office, "C", shift_end=ts(9))
    order = make_order("O", ts(8, 30), ts(9), 2400, 55.7555, 37.6225)
    problem = make_problem([office], [courier], [order])
    result = assignment(solve(problem), order)
    # Доехать к 8:30 можно, но встреча 40 минут заканчивается в 9:10 — вне смены.
    assert result.reason_code == ReasonCode.NO_CAPACITY
    assert "не помещается ни в одну смену" in result.reason_text


def test_no_candidates_is_no_capacity() -> None:
    office = make_office()
    offline = make_courier(office, "C", is_available=False)
    order = make_order("O", ts(9), ts(10), 600, 55.7555, 37.6225)
    problem = make_problem([office], [offline], [order])
    result = assignment(solve(problem), order)
    assert result.reason_code == ReasonCode.NO_CAPACITY
    assert "нет доступных представителей" in result.reason_text
    # Единственный кандидат, исключённый парой, — тот же случай.
    available = make_courier(office, "C")
    problem2 = make_problem([office], [available], [order], excluded=[(order.id, available.id)])
    result2 = assignment(solve(problem2), order)
    assert result2.reason_code == ReasonCode.NO_CAPACITY
    assert "нет доступных представителей" in result2.reason_text


def test_first_order_nearest_second_free_window() -> None:
    office = make_office()
    courier = make_courier(office, "C")
    o1 = make_order("O1", ts(9), ts(9, 30), 900, 55.7555, 37.6225)
    o2 = make_order("O2", ts(10), ts(10, 30), 600, 55.7600, 37.6090)
    problem = make_problem([office], [courier], [o1, o2])
    solution = solve(problem)
    first, second = assignment(solution, o1), assignment(solution, o2)
    assert first.reason_code == ReasonCode.NEAREST
    assert "Наименьший прирост времени: C" in first.reason_text
    assert second.reason_code == ReasonCode.FREE_WINDOW
    assert "окно соблюдено" in second.reason_text
    assert validate_solution(problem, solution, HaversineMatrix(40.0)) == []


def test_forced_violation_is_assigned_with_honest_text() -> None:
    office = make_office()
    courier = make_courier(office, "C")
    o1 = make_order("O1", ts(9), ts(9, 5), 600, 55.7555, 37.6225)
    o2 = make_order("O2", ts(9), ts(9, 10), 600, 55.7575, 37.6225)
    problem = make_problem([office], [courier], [o1, o2])
    solution = solve(problem)
    assert validate_solution(problem, solution, HaversineMatrix(40.0)) == []
    texts = [assignment(solution, o).reason_text for o in (o1, o2)]
    assert sum("окно нарушено на" in text for text in texts) == 1
    broken = next(
        a for a in solution.assignments if "окно нарушено на" in a.reason_text
    )
    # o1 (окно до 09:05) идёт первой: встреча ровно 09:00-09:10. o2 встаёт
    # следом с началом ~09:10:40 — на 40 с позже окна. Мягкое: назначаем,
    # текст говорит о нарушении прямо.
    assert broken.order_id == o2.id
    assert broken.reason_code == ReasonCode.FREE_WINDOW
    assert solution.metrics.violated_windows == 1
