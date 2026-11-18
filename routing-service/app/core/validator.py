"""validate_solution сверяет решение с задачей: все числа пересчитываются по
матрице, на слово не верится ничего.

Проверяем семь вещей: маршрут в границах смены; прибытие/отъезд/расстояние
сходятся с матрицей; window_violation_s посчитан верно; у каждой заявки ровно
одно назначение; назначение согласовано с маршрутом; назначены только доступные
курьеры; назначение не нарушает excluded_pairs. Остальные проверки убраны
намеренно (упрощение этапа 1).
"""
from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timedelta
from enum import Enum
from typing import Optional
from uuid import UUID

from app.core.model import (
    Courier,
    Office,
    Order,
    Problem,
    Route,
    RouteStop,
    Solution,
    StopType,
)
from app.core.time_matrix import SupportsLatLon, TravelTimeMatrix


class ViolationCode(str, Enum):
    SHIFT_START_VIOLATION = "SHIFT_START_VIOLATION"
    SHIFT_END_VIOLATION = "SHIFT_END_VIOLATION"
    ARRIVAL_TOO_EARLY = "ARRIVAL_TOO_EARLY"
    DEPARTURE_MISMATCH = "DEPARTURE_MISMATCH"
    DISTANCE_MISMATCH = "DISTANCE_MISMATCH"
    WINDOW_VIOLATION_MISMATCH = "WINDOW_VIOLATION_MISMATCH"
    MISSING_ASSIGNMENT = "MISSING_ASSIGNMENT"
    DUPLICATE_ASSIGNMENT = "DUPLICATE_ASSIGNMENT"
    ASSIGNMENT_ROUTE_MISMATCH = "ASSIGNMENT_ROUTE_MISMATCH"
    UNAVAILABLE_COURIER = "UNAVAILABLE_COURIER"
    FORBIDDEN_PAIR = "FORBIDDEN_PAIR"


@dataclass(frozen=True)
class Violation:
    code: ViolationCode
    message: str
    route_id: Optional[UUID] = None
    stop_sequence: Optional[int] = None
    order_id: Optional[UUID] = None


def _service_start(stop: RouteStop, order: Order) -> datetime:
    # Приехать раньше — не нарушение: курьер ждёт до window_from.
    return max(stop.arrival_eta, order.window_from)


def _expected_violation_s(start: datetime, order: Order) -> int:
    # window_to ограничивает начало встречи — по контракту.
    delta = (start - order.window_to).total_seconds()
    return int(delta) if delta > 0 else 0


def _stop_point(
    stop: RouteStop,
    offices: dict[UUID, Office],
    orders: dict[UUID, Order],
) -> Optional[SupportsLatLon]:
    if stop.type == StopType.OFFICE:
        return offices.get(stop.office_id)  # type: ignore[arg-type]
    return orders.get(stop.order_id)  # type: ignore[arg-type]


def _validate_route(
    route: Route,
    courier: Optional[Courier],
    offices: dict[UUID, Office],
    orders: dict[UUID, Order],
    matrix: TravelTimeMatrix,
    add,
) -> None:
    if courier is not None and route.stops[0].arrival_eta < courier.shift_start:
        add(
            ViolationCode.SHIFT_START_VIOLATION,
            "route starts before the shift opens",
            route_id=route.id,
            stop_sequence=route.stops[0].sequence,
        )

    for index, stop in enumerate(route.stops[1:], start=1):
        prev = route.stops[index - 1]
        prev_point = _stop_point(prev, offices, orders)
        point = _stop_point(stop, offices, orders)
        if prev_point is None or point is None:
            continue  # точки нет в задаче — сверять геометрию нечем
        travel = matrix.travel_time_s(prev_point, point)
        if stop.arrival_eta < prev.departure_eta + timedelta(seconds=travel):
            add(
                ViolationCode.ARRIVAL_TOO_EARLY,
                "arrival ignores travel time from the previous stop",
                route_id=route.id,
                stop_sequence=stop.sequence,
            )
        if stop.distance_from_prev_m != matrix.distance_m(prev_point, point):
            add(
                ViolationCode.DISTANCE_MISMATCH,
                "distance_from_prev_m does not match the matrix",
                route_id=route.id,
                stop_sequence=stop.sequence,
            )

    for stop in route.stops:
        if stop.type != StopType.ORDER or stop.order_id not in orders:
            continue
        order = orders[stop.order_id]
        start = _service_start(stop, order)
        expected_departure = start + timedelta(seconds=order.service_duration_s)
        if stop.departure_eta != expected_departure:
            add(
                ViolationCode.DEPARTURE_MISMATCH,
                "departure_eta must equal max(arrival, window_from) + service_duration_s",
                route_id=route.id,
                stop_sequence=stop.sequence,
                order_id=order.id,
            )
        expected_violation = _expected_violation_s(start, order)
        if stop.window_violation_s != expected_violation:
            add(
                ViolationCode.WINDOW_VIOLATION_MISMATCH,
                f"window_violation_s {stop.window_violation_s} != recomputed {expected_violation}",
                route_id=route.id,
                stop_sequence=stop.sequence,
                order_id=order.id,
            )

    if courier is not None and route.stops[-1].departure_eta > courier.shift_end:
        add(
            ViolationCode.SHIFT_END_VIOLATION,
            "route runs past shift end",
            route_id=route.id,
            stop_sequence=route.stops[-1].sequence,
        )


def validate_solution(
    problem: Problem, solution: Solution, matrix: TravelTimeMatrix
) -> list[Violation]:
    violations: list[Violation] = []

    def add(code: ViolationCode, message: str, **refs: object) -> None:
        violations.append(Violation(code=code, message=message, **refs))  # type: ignore[arg-type]

    offices = {office.id: office for office in problem.offices}
    couriers = {courier.id: courier for courier in problem.couriers}
    orders = {order.id: order for order in problem.orders}
    routes_by_id = {route.id: route for route in solution.routes}

    stopped_orders: dict[UUID, UUID] = {}  # order_id -> route_id
    for route in solution.routes:
        courier = couriers.get(route.courier_id)
        if courier is not None and not courier.is_available:
            add(
                ViolationCode.UNAVAILABLE_COURIER,
                f"route built for unavailable courier {route.courier_id}",
                route_id=route.id,
            )
        _validate_route(route, courier, offices, orders, matrix, add)
        for stop in route.stops:
            if stop.type == StopType.ORDER and stop.order_id is not None:
                stopped_orders.setdefault(stop.order_id, route.id)

    _validate_assignments(problem, solution, stopped_orders, routes_by_id, orders, add)
    return violations


def _validate_assignments(
    problem: Problem,
    solution: Solution,
    stopped_orders: dict[UUID, UUID],
    routes_by_id: dict[UUID, Route],
    orders: dict[UUID, Order],
    add,
) -> None:
    decided: set[UUID] = set()
    for assignment in solution.assignments:
        if assignment.order_id not in orders:
            continue
        if assignment.order_id in decided:
            add(
                ViolationCode.DUPLICATE_ASSIGNMENT,
                "more than one decision about the same order",
                order_id=assignment.order_id,
            )
            continue
        decided.add(assignment.order_id)
        stopped_in = stopped_orders.get(assignment.order_id)

        if assignment.courier_id is None:
            if stopped_in is not None:
                add(
                    ViolationCode.ASSIGNMENT_ROUTE_MISMATCH,
                    "order is stopped in a route but reported unassigned",
                    route_id=stopped_in,
                    order_id=assignment.order_id,
                )
            continue

        route = routes_by_id.get(assignment.route_id)  # type: ignore[arg-type]
        if (
            route is None
            or route.courier_id != assignment.courier_id
            or stopped_in != assignment.route_id
        ):
            add(
                ViolationCode.ASSIGNMENT_ROUTE_MISMATCH,
                "assignment, route membership and route owner must agree",
                route_id=assignment.route_id,
                order_id=assignment.order_id,
            )
        # Запрет после ORDER_REFUSED: этот курьер больше не берёт эту заявку.
        if (assignment.order_id, assignment.courier_id) in problem.excluded_pairs:
            add(
                ViolationCode.FORBIDDEN_PAIR,
                "assignment breaks a pair excluded after ORDER_REFUSED",
                route_id=assignment.route_id,
                order_id=assignment.order_id,
            )

    for order in problem.orders:
        if order.id not in decided:
            add(
                ViolationCode.MISSING_ASSIGNMENT,
                f"order {order.id} has no decision in the solution",
                order_id=order.id,
            )
