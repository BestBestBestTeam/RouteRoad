"""OR-Tools (VRPTW) для той же модели, что и greedy.

OR-Tools решает только состав и порядок заявок у каждого представителя;
остановки, ETA, расстояния и нарушения пересчитывает общий build_stops —
числа сходятся с валидатором. Цели солверов различаются: у greedy в стоимость
входит ожидание, у OR-Tools — дорога (travel_time_s) и штраф окон; сравниваем
по внешним метрикам. Детерминизма нет: с лимитом времени GUIDED_LOCAL_SEARCH
может давать разные решения между запусками.
"""
from __future__ import annotations

from datetime import datetime
from math import ceil
from time import perf_counter
from typing import Optional
from uuid import UUID, uuid4

from ortools.constraint_solver import (  # type: ignore[import-untyped]
    pywrapcp, routing_enums_pb2,
)

from app.core.common import (
    WINDOW_PENALTY_K, available as _available,
    build_stops as _build_stops, earliest_arrival as _earliest_arrival,
    infeasible_text as _infeasible_text, no_candidates_text as _no_candidates_text,
    shift_full_text as _shift_full_text,
)
from app.core.model import (
    Algorithm, Assignment, Courier, Office, Order, Problem, ReasonCode, Route,
    RouteStop, Solution,
)
from app.core.time_matrix import SupportsLatLon, TravelTimeMatrix

# Лимит локального поиска; на бенчмарке меняют, не трогая остальное.
TIME_LIMIT_S = 5
# Штраф секунды опоздания к window_to — та же WINDOW_PENALTY_K, целые секунды.
WINDOW_PENALTY_INT = int(round(WINDOW_PENALTY_K))
# Пропуск заявки заведомо дороже худшего опоздания на сутки при любом штрафе.
DROP_PENALTY = 2 * 86400 * max(WINDOW_PENALTY_INT, 1)
_HUGE = 4 * 86400


def _optimized_text(order: Order, courier: Courier, violation_s: int) -> str:
    tail = (
        f"; окно нарушено на {ceil(violation_s / 60)} мин" if violation_s
        else ", окно соблюдено"
    )
    return f"{order.client_name} ({order.address}): {courier.name}, план OR-Tools{tail}"


class OrToolsSolver:
    """Доступные представители = машины; старт — узел своего офиса, конец —
    общий фиктивный узел с нулевой дугой (маршрут не возвращается в офис)."""

    name: Algorithm = Algorithm.ORTOOLS

    def __init__(self, time_limit_s: int = TIME_LIMIT_S) -> None:
        self.time_limit_s = time_limit_s
        # Ставится, если OR-Tools не вернул решение вовсе — об этом в отчёте.
        self.last_run_failed = False

    def solve(self, problem: Problem, matrix: TravelTimeMatrix) -> Solution:
        started = perf_counter()
        self.last_run_failed = False
        now = problem.now
        offices = {office.id: office for office in problem.offices}
        excluded = set(problem.excluded_pairs)
        couriers = [c for c in problem.couriers if _available(c, now)]

        # То же отсечение, что у greedy: в модель попадают только выполнимые.
        unassigned: dict[UUID, tuple[ReasonCode, str]] = {}
        pending: list[Order] = []
        for order in problem.orders:
            candidates = [c for c in couriers if (order.id, c.id) not in excluded]
            if not candidates:
                unassigned[order.id] = (
                    ReasonCode.NO_CAPACITY, _no_candidates_text(order),
                )
                continue
            earliest = _earliest_arrival(order, candidates, offices, matrix, now)
            if earliest is None or earliest > order.window_to:
                unassigned[order.id] = (
                    ReasonCode.WINDOW_INFEASIBLE, _infeasible_text(order, earliest),
                )
                continue
            pending.append(order)

        sequences: dict[UUID, list[Order]] = {}
        if pending:
            self.last_run_failed = self._route(
                now, couriers, offices, excluded, pending, matrix, sequences
            )

        routes_by_courier: dict[UUID, list[RouteStop]] = {}
        assigned: dict[UUID, Courier] = {}
        for courier in couriers:
            sequence = sequences.get(courier.id)
            if not sequence:
                continue
            stops = _build_stops(
                offices[courier.office_id], sequence, matrix,
                max(courier.shift_start, now), courier.shift_end,
            )
            if stops is None:
                # OR-Tools обещал смену — не вышло. Не подгоняем: переполнение.
                for order in sequence:
                    unassigned[order.id] = (ReasonCode.NO_CAPACITY, _shift_full_text(order))
                continue
            routes_by_courier[courier.id] = stops
            for order in sequence:
                assigned[order.id] = courier
                unassigned.pop(order.id, None)
        # Пропущенные disjunction'ом заявки — переполнение смен.
        for order in pending:
            if order.id not in assigned and order.id not in unassigned:
                unassigned[order.id] = (ReasonCode.NO_CAPACITY, _shift_full_text(order))

        routes = [Route(
            id=uuid4(), courier_id=courier_id, plan_date=problem.plan_date,
            algorithm=Algorithm.ORTOOLS, stops=stops,
        ) for courier_id, stops in routes_by_courier.items()]
        route_ids = {route.courier_id: route.id for route in routes}
        final_violation = {
            stop.order_id: stop.window_violation_s
            for stops in routes_by_courier.values()
            for stop in stops if stop.order_id is not None
        }
        assignments: list[Assignment] = []
        for order in problem.orders:
            if order.id in assigned:
                courier = assigned[order.id]
                assignments.append(Assignment(
                    order_id=order.id, courier_id=courier.id,
                    route_id=route_ids[courier.id],
                    reason_code=ReasonCode.OPTIMIZED,
                    reason_text=_optimized_text(
                        order, courier, final_violation.get(order.id, 0)
                    ),
                ))
            else:
                code, text = unassigned[order.id]
                assignments.append(Assignment(
                    order_id=order.id, reason_code=code, reason_text=text
                ))
        return Solution(
            algorithm=Algorithm.ORTOOLS,
            routes=routes,
            assignments=assignments,
            solve_time_ms=int((perf_counter() - started) * 1000),
        )

    def _route(
        self,
        now: datetime,
        couriers: list[Courier],
        offices: dict[UUID, Office],
        excluded: set[tuple[UUID, UUID]],
        pending: list[Order],
        matrix: TravelTimeMatrix,
        sequences: dict[UUID, list[Order]],
    ) -> bool:
        """Построить модель и решить. True = решение не найдено."""
        base = min([now] + [c.shift_start for c in couriers])

        def sec(value: datetime) -> int:
            return int((value - base).total_seconds())

        dummy = 0
        nodes: list[Optional[SupportsLatLon]] = [None]  # узел 0 — фиктивный конец
        office_node: dict[UUID, int] = {}
        for courier in couriers:
            office_node[courier.id] = len(nodes)
            nodes.append(offices[courier.office_id])
        order_node: dict[UUID, int] = {}
        for order in pending:
            order_node[order.id] = len(nodes)
            nodes.append(order)
        service = {order_node[o.id]: o.service_duration_s for o in pending}
        horizon = max(sec(c.shift_end) for c in couriers) + 86400

        def arc_nodes(i: int, j: int) -> tuple[int, int]:
            # Коллбэки получают индексы менеджера — переводим в узлы.
            return manager.IndexToNode(i), manager.IndexToNode(j)

        def arc_time(i: int, j: int) -> int:
            a, b = arc_nodes(i, j)
            # Фиктивный конец: нулевые дуги, маршрут не тянем обратно в офис.
            if a == dummy or b == dummy:
                return 0
            point_a, point_b = nodes[a], nodes[b]
            assert point_a is not None and point_b is not None  # не-dummy = есть точка
            return matrix.travel_time_s(point_a, point_b)

        def time_arc(i: int, j: int) -> int:
            a, b = arc_nodes(i, j)
            if b == dummy:
                # Дуга в конец стоит служба последнего узла, иначе shift_end
                # проверяла бы только начало последней встречи.
                return service.get(a, 0)
            if a == dummy:
                return 0
            point_a, point_b = nodes[a], nodes[b]
            assert point_a is not None and point_b is not None
            return matrix.travel_time_s(point_a, point_b) + service.get(a, 0)

        starts = [office_node[c.id] for c in couriers]
        ends = [dummy] * len(couriers)
        manager = pywrapcp.RoutingIndexManager(len(nodes), len(couriers), starts, ends)
        routing = pywrapcp.RoutingModel(manager)
        distance_cb = routing.RegisterTransitCallback(arc_time)
        time_cb = routing.RegisterTransitCallback(time_arc)
        routing.SetArcCostEvaluatorOfAllVehicles(distance_cb)
        routing.AddDimension(time_cb, _HUGE, horizon, False, "Time")
        time_dim = routing.GetDimensionOrDie("Time")

        for order in pending:
            node = manager.NodeToIndex(order_node[order.id])
            # Кумулятива = момент начала встречи: ждём до window_from, опоздание
            # к window_to мягкое (штраф добавляется в целевую автоматически).
            time_dim.CumulVar(node).SetRange(sec(order.window_from), horizon)
            time_dim.SetCumulVarSoftUpperBound(
                node, sec(order.window_to), WINDOW_PENALTY_INT
            )
        for order in pending:
            # Пропуск дороже любой задержки: модель бросает заявку последней.
            routing.AddDisjunction(
                [manager.NodeToIndex(order_node[order.id])], DROP_PENALTY
            )
            vehicle_var = routing.VehicleVar(manager.NodeToIndex(order_node[order.id]))
            for vehicle, courier in enumerate(couriers):
                if (order.id, courier.id) in excluded:
                    vehicle_var.RemoveValue(vehicle)
        for vehicle, courier in enumerate(couriers):
            start_s = sec(max(courier.shift_start, now))
            time_dim.CumulVar(routing.Start(vehicle)).SetRange(start_s, start_s)
            # Конец маршрута не позже shift_end — жёстко.
            time_dim.CumulVar(routing.End(vehicle)).SetRange(0, sec(courier.shift_end))

        params = pywrapcp.DefaultRoutingSearchParameters()
        params.first_solution_strategy = (
            routing_enums_pb2.FirstSolutionStrategy.PATH_CHEAPEST_ARC
        )
        params.local_search_metaheuristic = (
            routing_enums_pb2.LocalSearchMetaheuristic.GUIDED_LOCAL_SEARCH
        )
        params.time_limit.seconds = self.time_limit_s
        solution = routing.SolveWithParameters(params)
        if solution is None:
            return True
        node_to_order = {order_node[order.id]: order for order in pending}
        for vehicle, courier in enumerate(couriers):
            index = routing.Start(vehicle)
            sequence: list[Order] = []
            while not routing.IsEnd(index):
                stopped = node_to_order.get(manager.IndexToNode(index))
                if stopped is not None:
                    sequence.append(stopped)
                index = solution.Value(routing.NextVar(index))
            if sequence:
                sequences[courier.id] = sequence
        return False
