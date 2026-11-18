"""Фабрики модели для тестов этапа 1 и маленький сценарий, собранный той же
матрицей, что и валидатор: его времена верны по построению."""
from __future__ import annotations

from dataclasses import dataclass
from datetime import date, datetime, timedelta, timezone
from typing import Optional, Sequence
from uuid import UUID, uuid4

from app.core.model import (
    Algorithm,
    Assignment,
    Courier,
    Office,
    Order,
    Problem,
    ReasonCode,
    Route,
    RouteStop,
    Solution,
    SolverConfig,
    StopType,
)
from app.core.time_matrix import HaversineMatrix, SupportsLatLon

TZ = timezone(timedelta(hours=7))  # Новосибирск, +07:00
MONDAY = date(2026, 11, 9)
BASE_LAT, BASE_LON = 55.7510, 37.6180


def ts(hour: int, minute: int = 0) -> datetime:
    return datetime(MONDAY.year, MONDAY.month, MONDAY.day, hour, minute, tzinfo=TZ)


def make_office(lat_shift: float = 0.0, lon_shift: float = 0.0, name: str = "Office 1") -> Office:
    return Office(id=uuid4(), name=name, lat=BASE_LAT + lat_shift, lon=BASE_LON + lon_shift)


def make_courier(
    office: Office,
    name: str = "Courier",
    shift_start: Optional[datetime] = None,
    shift_end: Optional[datetime] = None,
    is_available: bool = True,
) -> Courier:
    return Courier(
        id=uuid4(),
        name=name,
        office_id=office.id,
        shift_start=shift_start or ts(8),
        shift_end=shift_end or ts(18),
        is_available=is_available,
    )


def make_order(
    name: str,
    window_from: datetime,
    window_to: datetime,
    duration_s: int,
    lat: float,
    lon: float,
    version: int = 1,
    address: str = "Синтетическая ул., 1",
) -> Order:
    return Order(
        id=uuid4(),
        client_name=name,
        address=address,
        lat=lat,
        lon=lon,
        window_from=window_from,
        window_to=window_to,
        service_duration_s=duration_s,
        service_date=window_from.date(),
        version=version,
    )


def make_stop(
    sequence: int,
    arrival: datetime,
    departure: datetime,
    *,
    order: Optional[Order] = None,
    office: Optional[Office] = None,
    distance_m: int = 0,
    violation_s: int = 0,
) -> RouteStop:
    if order is not None:
        return RouteStop(
            sequence=sequence,
            type=StopType.ORDER,
            order_id=order.id,
            arrival_eta=arrival,
            departure_eta=departure,
            distance_from_prev_m=distance_m,
            window_violation_s=violation_s,
        )
    assert office is not None
    return RouteStop(
        sequence=sequence,
        type=StopType.OFFICE,
        office_id=office.id,
        arrival_eta=arrival,
        departure_eta=departure,
        distance_from_prev_m=distance_m,
        window_violation_s=violation_s,
    )


def build_route(
    courier: Courier,
    office: Office,
    orders: Sequence[Order],
    matrix: HaversineMatrix,
    start: datetime,
    algorithm: Algorithm = Algorithm.GREEDY,
    plan_date: date = MONDAY,
) -> Route:
    """Цепочка офис -> заказы; каждое время пересчитано через матрицу, поэтому
    маршрут валиден, если тест не сломал его намеренно. start зажимается началом
    смены; встречи ждут window_from без нарушения."""
    stops: list[RouteStop] = []
    clock = max(start, courier.shift_start)
    stops.append(make_stop(1, clock, clock, office=office, distance_m=0))
    previous: SupportsLatLon = office
    departure = clock
    for sequence, order in enumerate(orders, start=2):
        arrival = departure + timedelta(seconds=matrix.travel_time_s(previous, order))
        meeting_start = max(arrival, order.window_from)
        departure = meeting_start + timedelta(seconds=order.service_duration_s)
        violation = int(max((meeting_start - order.window_to).total_seconds(), 0))
        stops.append(
            make_stop(
                sequence,
                arrival,
                departure,
                order=order,
                distance_m=matrix.distance_m(previous, order),
                violation_s=violation,
            )
        )
        previous = order
    return Route(
        id=uuid4(),
        courier_id=courier.id,
        plan_date=plan_date,
        algorithm=algorithm,
        stops=stops,
    )


def make_assignment(
    order: Order,
    courier: Optional[Courier] = None,
    route: Optional[Route] = None,
    reason: ReasonCode = ReasonCode.FREE_WINDOW,
    text: str = "window respected",
) -> Assignment:
    return Assignment(
        order_id=order.id,
        courier_id=courier.id if courier else None,
        route_id=route.id if route else None,
        reason_code=reason,
        reason_text=text,
    )


@dataclass
class Scenario:
    problem: Problem
    solution: Solution
    matrix: HaversineMatrix
    office: Office
    c1: Courier
    c2: Courier
    o1: Order
    o2: Order
    o3: Order
    o4: Order
    r1: Route
    r2: Route


def build_scenario() -> Scenario:
    """Один офис, два курьера, четыре заказа:

    o1/o3 у курьера 1, o2 у курьера 2 (короткая смена 08:00-11:00), o4 в ~38 км
    с окном 08:00-08:30: самый ранний возможный приезд ~08:57 при 40 км/ч,
    поэтому WINDOW_INFEASIBLE для него — правильный код.
    """
    office = make_office()
    c1 = make_courier(office, "C1")
    c2 = make_courier(office, "C2", shift_end=ts(11))
    o1 = make_order("O1", ts(9), ts(9, 30), 900, BASE_LAT + 0.0045, BASE_LON + 0.0045)
    o2 = make_order("O2", ts(9, 30), ts(10), 1800, BASE_LAT + 0.0090, BASE_LON - 0.0090)
    o3 = make_order("O3", ts(13), ts(23), 600, BASE_LAT + 0.0090, BASE_LON + 0.0090)
    o4 = make_order("O4", ts(8), ts(8, 30), 3600, BASE_LAT + 0.30, BASE_LON + 0.30)

    problem = Problem(
        plan_date=MONDAY,
        now=ts(8),
        algorithm=Algorithm.GREEDY,
        config=SolverConfig(speed_kmh=40.0),
        offices=[office],
        couriers=[c1, c2],
        orders=[o1, o2, o3, o4],
    )
    matrix = HaversineMatrix(40.0)
    r1 = build_route(c1, office, [o1, o3], matrix, start=ts(8))
    r2 = build_route(c2, office, [o2], matrix, start=ts(8))
    assignments = [
        make_assignment(o1, c1, r1),
        make_assignment(o3, c1, r1),
        make_assignment(o2, c2, r2),
        make_assignment(
            o4,
            reason=ReasonCode.WINDOW_INFEASIBLE,
            text="38 km away: nobody can arrive before 08:30 at 40 km/h",
        ),
    ]
    solution = Solution(
        algorithm=problem.algorithm,
        routes=[r1, r2],
        assignments=assignments,
        solve_time_ms=42,
    )
    return Scenario(
        problem=problem,
        solution=solution,
        matrix=matrix,
        office=office,
        c1=c1,
        c2=c2,
        o1=o1,
        o2=o2,
        o3=o3,
        o4=o4,
        r1=r1,
        r2=r2,
    )
