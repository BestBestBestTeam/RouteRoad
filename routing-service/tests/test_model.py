"""Самопроверки корневой модели: плохие данные в ядро не проходят."""
from __future__ import annotations

from datetime import date, datetime, timedelta
from uuid import uuid4

import pytest
from pydantic import ValidationError

from app.core.model import (
    Assignment,
    Courier,
    LatLng,
    Order,
    Problem,
    ReasonCode,
    Route,
    RouteStop,
    SolverConfig,
    StopType,
    WindowMode,
)
from tests.factories import MONDAY, make_order, make_stop, ts


def order_kwargs(**overrides) -> dict:
    base = dict(
        id=uuid4(),
        client_name="client",
        address="Тестовая ул., 1",
        lat=55.75,
        lon=37.6,
        window_from=ts(9),
        window_to=ts(10),
        service_duration_s=600,
        service_date=MONDAY,
        version=1,
    )
    base.update(overrides)
    return base


def test_naive_window_rejected() -> None:
    naive = datetime(2026, 10, 19, 9, 0)
    with pytest.raises(ValidationError):
        Order(**order_kwargs(window_from=naive, window_to=naive + timedelta(hours=1)))


def test_naive_shift_rejected() -> None:
    naive = datetime(2026, 10, 19, 9, 0)
    with pytest.raises(ValidationError):
        Courier(
            id=uuid4(),
            name="c",
            office_id=uuid4(),
            shift_start=naive,
            shift_end=naive + timedelta(hours=8),
        )


def test_window_to_before_from_rejected() -> None:
    with pytest.raises(ValidationError):
        Order(**order_kwargs(window_from=ts(10), window_to=ts(9)))


def test_service_date_mismatch_rejected() -> None:
    with pytest.raises(ValidationError):
        Order(**order_kwargs(service_date=date(2026, 10, 20)))


def test_nonpositive_duration_rejected() -> None:
    # По контракту service_duration_s > 0.
    for duration in (-1, 0):
        with pytest.raises(ValidationError):
            Order(**order_kwargs(service_duration_s=duration))


def test_version_is_long_string_in_json() -> None:
    order = Order(**order_kwargs(version="42"))
    assert order.version == 42 and isinstance(order.version, int)
    assert '"version":"42"' in order.model_dump_json()
    for garbage in ("abc", "4.5", "", "-1"):
        with pytest.raises(ValidationError):
            Order(**order_kwargs(version=garbage))


def test_empty_shift_rejected() -> None:
    with pytest.raises(ValidationError):
        Courier(
            id=uuid4(),
            name="c",
            office_id=uuid4(),
            shift_start=ts(9),
            shift_end=ts(9),
        )


def test_duplicate_order_ids_rejected() -> None:
    office, first = make_office_for_problem(), make_order("a", ts(9), ts(10), 600, 55.75, 37.6)
    twin = first.model_copy()  # тот же id
    with pytest.raises(ValidationError):
        Problem(
            plan_date=MONDAY,
            now=ts(8),
            algorithm="greedy",
            config=SolverConfig(speed_kmh=40),
            offices=[office],
            couriers=[],
            orders=[first, twin],
        )


def make_office_for_problem():
    from app.core.model import Office

    return Office(id=uuid4(), name="o", lat=55.75, lon=37.6)


def test_courier_with_unknown_office_rejected() -> None:
    office = make_office_for_problem()
    courier = Courier(
        id=uuid4(),
        name="c",
        office_id=uuid4(),  # нет в problem.offices
        shift_start=ts(9),
        shift_end=ts(18),
    )
    with pytest.raises(ValidationError):
        Problem(
            plan_date=MONDAY,
            now=ts(8),
            algorithm="greedy",
            config=SolverConfig(speed_kmh=40),
            offices=[office],
            couriers=[courier],
            orders=[],
        )


def test_stop_type_must_match_refs() -> None:
    with pytest.raises(ValidationError):
        RouteStop(
            sequence=1,
            type=StopType.ORDER,
            office_id=uuid4(),
            arrival_eta=ts(9),
            departure_eta=ts(9),
            distance_from_prev_m=0,
        )
    with pytest.raises(ValidationError):
        RouteStop(
            sequence=1,
            type=StopType.OFFICE,
            arrival_eta=ts(9),
            departure_eta=ts(9),
            distance_from_prev_m=0,
        )


def test_departure_before_arrival_rejected() -> None:
    with pytest.raises(ValidationError):
        make_stop(1, ts(10), ts(9), office=make_office_for_problem())


def test_zero_sequence_rejected() -> None:
    with pytest.raises(ValidationError):
        make_stop(0, ts(10), ts(10), office=make_office_for_problem())


def test_route_requires_at_least_one_stop() -> None:
    with pytest.raises(ValidationError):
        Route(
            id=uuid4(),
            courier_id=uuid4(),
            plan_date=MONDAY,
            algorithm="greedy",
            stops=[],
        )


def test_assignment_needs_both_refs_or_neither() -> None:
    with pytest.raises(ValidationError):
        Assignment(
            order_id=uuid4(),
            courier_id=uuid4(),
            route_id=None,
            reason_code=ReasonCode.NEAREST,
            reason_text="text",
        )


def test_coordinates_are_bounded() -> None:
    with pytest.raises(ValidationError):
        LatLng(lat=91.0, lon=0.0)
    with pytest.raises(ValidationError):
        LatLng(lat=0.0, lon=-181.0)


def test_config_defaults() -> None:
    config = SolverConfig(speed_kmh=40)
    assert config.window_mode == WindowMode.SOFT
