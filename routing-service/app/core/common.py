"""Общее для greedy и ortools: доступность, построение остановок, отсечение
неназначенных и их тексты. Времена и расстояния — только через матрицу."""
from __future__ import annotations

from datetime import datetime, timedelta
from typing import Optional, Sequence
from uuid import UUID

from app.core.model import Courier, Office, Order, RouteStop, StopType
from app.core.time_matrix import SupportsLatLon, TravelTimeMatrix

# Секунда нарушения окна дороже секунды лишнего пути в WINDOW_PENALTY_K раз.
WINDOW_PENALTY_K = 2.0


def available(courier: Courier, now: datetime) -> bool:
    return courier.is_available and courier.shift_end > now


def build_stops(
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


def earliest_arrival(
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


def infeasible_text(order: Order, earliest: Optional[datetime]) -> str:
    if earliest is None:
        return (
            f"{order.client_name} ({order.address}): никто из доступных "
            f"представителей не доедет до {order.window_to:%H:%M}"
        )
    return (
        f"{order.client_name} ({order.address}): ближайший представитель "
        f"приедет к {earliest:%H:%M}, а окно закрывается в {order.window_to:%H:%M}"
    )


def shift_full_text(order: Order) -> str:
    return f"{order.client_name} ({order.address}): не помещается ни в одну смену"


def no_candidates_text(order: Order) -> str:
    return f"{order.client_name} ({order.address}): нет доступных представителей"
