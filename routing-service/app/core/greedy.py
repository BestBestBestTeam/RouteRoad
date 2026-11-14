"""Greedy-назначение: заявка за заявкой дешёвой вставкой (cheapest insertion).

Маршрут начинается в офисе представителя в max(shift_start, now), возврат не
нужен. Конец смены жёсткий, нарушение окна мягкое: штраф WINDOW_PENALTY_K
секунд стоимости за секунду нарушения. Времена и расстояния — только из
матрицы. Результат проходит validate_solution.
"""
from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timedelta
from math import ceil
from time import perf_counter
from typing import Optional, Sequence
from uuid import UUID, uuid4

from app.core.model import (
    Algorithm, Assignment, Courier, Office, Order, Problem, ReasonCode, Route,
    RouteStop, Solution, StopType,
)
from app.core.time_matrix import SupportsLatLon, TravelTimeMatrix

# Секунда нарушения окна дороже секунды лишнего пути в WINDOW_PENALTY_K раз.
WINDOW_PENALTY_K = 2.0


@dataclass(frozen=True)
class Insert:
    """Лучшая вставка: позиция заявки (1 = после офиса), новые остановки,
    приросты времени/нарушений и стоимость в секундах."""

    index: int
    stops: list[RouteStop]
    extra_time_s: int
    extra_violation_s: int
    cost_s: float


def _build_stops(
    office: Office,
    orders: Sequence[Order],
    matrix: TravelTimeMatrix,
    start: datetime,
    shift_end: datetime,
) -> Optional[list[RouteStop]]:
    """Маршрут офис -> заявки, весь пересчитан от start.
    None, если последняя встреча заканчивается после shift_end."""
    stops = [RouteStop(
        sequence=1, type=StopType.OFFICE, office_id=office.id,
        arrival_eta=start, departure_eta=start, distance_from_prev_m=0,
    )]
    clock = start
    previous: SupportsLatLon = office
    for sequence, order in enumerate(orders, start=2):
        arrival = clock + timedelta(seconds=matrix.travel_time_s(previous, order))
        # Раннее прибытие = ожидание до window_from, не нарушение.
        meeting_start = max(arrival, order.window_from)
        departure = meeting_start + timedelta(seconds=order.service_duration_s)
        if departure > shift_end:
            return None
        stops.append(RouteStop(
            sequence=sequence, type=StopType.ORDER, order_id=order.id,
            arrival_eta=arrival, departure_eta=departure,
            distance_from_prev_m=matrix.distance_m(previous, order),
            window_violation_s=int(max((meeting_start - order.window_to).total_seconds(), 0)),
        ))
        clock = departure
        previous = order
    return stops


def _totals(
    office: Office,
    orders: Sequence[Order],
    matrix: TravelTimeMatrix,
    start: datetime,
    shift_end: datetime,
) -> Optional[tuple[int, int, list[RouteStop]]]:
    """(длительность, сумма нарушений, остановки) либо None вне смены."""
    stops = _build_stops(office, orders, matrix, start, shift_end)
    if stops is None:
        return None
    duration = int((stops[-1].departure_eta - start).total_seconds())
    return duration, sum(s.window_violation_s for s in stops), stops


def try_insert_order(
    order: Order,
    courier: Courier,
    office: Office,
    current: Sequence[Order],
    matrix: TravelTimeMatrix,
    now: datetime,
) -> Optional[Insert]:
    """Вставить заявку в маршрут дешевле всего.

    Перебираем все позиции; каждая пересчитывает весь маршрут, потому что
    вставка сдвигает последующие точки. Варианты, где встреча заканчивается
    после shift_end, отбрасываем — это жёстко. Стоимость = прирост
    длительности + штраф новых нарушений окон. При равенстве берётся первая
    позиция (детерминированность). None, если не влезает никуда.
    """
    start = max(courier.shift_start, now)
    base = _totals(office, current, matrix, start, courier.shift_end)
    if base is None:
        return None  # текущий маршрут уже вне смены — вставлять некуда
    best: Optional[Insert] = None
    for index in range(len(current) + 1):
        candidate = [*current[:index], order, *current[index:]]
        totals = _totals(office, candidate, matrix, start, courier.shift_end)
        if totals is None:
            continue
        duration, violation, stops = totals
        extra_time = duration - base[0]
        extra_violation = violation - base[1]
        cost = extra_time + WINDOW_PENALTY_K * extra_violation
        if best is None or cost < best.cost_s:
            best = Insert(
                index=index + 1, stops=stops, extra_time_s=extra_time,
                extra_violation_s=extra_violation, cost_s=cost,
            )
    return best


def _available(courier: Courier, now: datetime) -> bool:
    return courier.is_available and courier.shift_end > now


def _earliest_arrival(
    order: Order,
    couriers: Sequence[Courier],
    offices: dict[UUID, Office],
    matrix: TravelTimeMatrix,
    now: datetime,
) -> Optional[datetime]:
    """Когда приедет ближайший из представителей: старт из своего офиса в
    max(shift_start, now), дорога только по матрице. None, если кандидатов нет."""
    best: Optional[datetime] = None
    for courier in couriers:
        arrival = max(courier.shift_start, now) + timedelta(
            seconds=matrix.travel_time_s(offices[courier.office_id], order)
        )
        if best is None or arrival < best:
            best = arrival
    return best


def _infeasible_text(order: Order, earliest: Optional[datetime]) -> str:
    if earliest is None:
        return (
            f"{order.client_name} ({order.address}): никто из доступных "
            f"представителей не доедет до {order.window_to:%H:%M}"
        )
    return (
        f"{order.client_name} ({order.address}): ближайший представитель "
        f"приедет к {earliest:%H:%M}, а окно закрывается в {order.window_to:%H:%M}"
    )


def _assigned_text(
    order: Order, courier: Courier, extra_time_s: int, was_empty: bool,
    violation_s: int,
) -> str:
    """Пишется по финальным остановкам: поздняя вставка может сдвинуть чужую
    встречу за окно, и прежнее «окно соблюдено» стало бы неправдой."""
    head = "Наименьший прирост времени" if was_empty else "Вставка в маршрут"
    tail = (
        f"; окно нарушено на {ceil(violation_s / 60)} мин" if violation_s
        else ", окно соблюдено"
    )
    return (
        f"{order.client_name} ({order.address}): {head}: {courier.name}, "
        f"+{round(extra_time_s / 60)} мин к маршруту{tail}"
    )


class GreedySolver:
    """Заявки идут по возрастанию window_to (при равенстве — по id), каждая
    встаёт к представителю с минимальной стоимостью вставки."""

    name: Algorithm = Algorithm.GREEDY

    def solve(self, problem: Problem, matrix: TravelTimeMatrix) -> Solution:
        started = perf_counter()
        now = problem.now
        offices = {office.id: office for office in problem.offices}
        excluded = set(problem.excluded_pairs)
        sequences: dict[UUID, list[Order]] = {}
        stops_by_courier: dict[UUID, list[RouteStop]] = {}
        unassigned: dict[UUID, tuple[ReasonCode, str]] = {}
        # order -> (курьер, был ли маршрут пуст, прирост времени);
        # текст назначения строится в конце, по финальным остановкам.
        assigned: dict[UUID, tuple[Courier, bool, int]] = {}

        for order in sorted(problem.orders, key=lambda o: (o.window_to, str(o.id))):
            candidates = [
                c for c in problem.couriers
                if _available(c, now) and (order.id, c.id) not in excluded
            ]
            if not candidates:
                # Дело не в окне — назначать просто некому.
                unassigned[order.id] = (
                    ReasonCode.NO_CAPACITY,
                    f"{order.client_name} ({order.address}): нет доступных представителей",
                )
                continue
            earliest = _earliest_arrival(order, candidates, offices, matrix, now)
            if earliest is None or earliest > order.window_to:
                # Доехать вовремя некому — даже с пустым маршрутом.
                unassigned[order.id] = (
                    ReasonCode.WINDOW_INFEASIBLE, _infeasible_text(order, earliest),
                )
                continue
            best: Optional[tuple[Insert, Courier]] = None
            for courier in candidates:
                insert = try_insert_order(
                    order, courier, offices[courier.office_id],
                    sequences.get(courier.id, ()), matrix, now,
                )
                if insert is not None and (best is None or insert.cost_s < best[0].cost_s):
                    best = (insert, courier)
            if best is None:
                unassigned[order.id] = (
                    ReasonCode.NO_CAPACITY,
                    f"{order.client_name} ({order.address}): не помещается ни в одну смену",
                )
                continue
            insert, courier = best
            was_empty = courier.id not in sequences
            sequences.setdefault(courier.id, []).insert(insert.index - 1, order)
            stops_by_courier[courier.id] = insert.stops
            assigned[order.id] = (courier, was_empty, insert.extra_time_s)

        routes = [Route(
            id=uuid4(), courier_id=courier_id, plan_date=problem.plan_date,
            algorithm=Algorithm.GREEDY, stops=stops,
        ) for courier_id, stops in stops_by_courier.items()]
        route_ids = {route.courier_id: route.id for route in routes}
        final_violation = {  # поздняя вставка могла сдвинуть чужую встречу
            stop.order_id: stop.window_violation_s
            for stops in stops_by_courier.values()
            for stop in stops if stop.order_id is not None
        }

        def to_assignment(order: Order) -> Assignment:
            if order.id in assigned:
                courier, was_empty, extra = assigned[order.id]
                return Assignment(
                    order_id=order.id, courier_id=courier.id,
                    route_id=route_ids[courier.id],
                    reason_code=ReasonCode.NEAREST if was_empty else ReasonCode.FREE_WINDOW,
                    reason_text=_assigned_text(
                        order, courier, extra, was_empty,
                        final_violation.get(order.id, 0),
                    ),
                )
            code, text = unassigned[order.id]
            return Assignment(order_id=order.id, reason_code=code, reason_text=text)

        return Solution(
            algorithm=Algorithm.GREEDY,
            routes=routes,
            assignments=[to_assignment(order) for order in problem.orders],
            solve_time_ms=int((perf_counter() - started) * 1000),
        )
