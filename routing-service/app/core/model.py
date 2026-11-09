"""Корневая модель задачи назначения и маршрутизации: без базы данных и HTTP.

Конвенции взяты из docs/contract.md: snake_case в полях, ISO 8601 с таймзоной,
длительности в секундах, расстояния в метрах, идентификаторы UUID.

* ``window_to`` ограничивает *начало* встречи: встреча может закончиться после
  окна, если укладывается в смену; приехать раньше — это ожидание, не нарушение;
* окна — абсолютные datetime; ``service_date`` — организационная метка дня, а
  DEFER_NEXT_DAY означает, что ядро лишь сообщает причину — заказ сдвигает Java.
"""
from __future__ import annotations

import enum
from datetime import date
from typing import Optional
from uuid import UUID

from pydantic import AwareDatetime, BaseModel, Field, model_validator


class Algorithm(str, enum.Enum):
    GREEDY = "greedy"
    ORTOOLS = "ortools"


class WindowMode(str, enum.Enum):
    SOFT = "soft"
    HARD = "hard"


class StopType(str, enum.Enum):
    OFFICE = "OFFICE"
    ORDER = "ORDER"


class ReasonCode(str, enum.Enum):
    """Значения reason_code из контракта."""

    NEAREST = "NEAREST"
    FREE_WINDOW = "FREE_WINDOW"
    OPTIMIZED = "OPTIMIZED"
    REASSIGNED_AFTER_REFUSAL = "REASSIGNED_AFTER_REFUSAL"
    MANUAL_DISPATCHER = "MANUAL_DISPATCHER"
    AUTO_AFTER_TIMEOUT = "AUTO_AFTER_TIMEOUT"
    NO_CAPACITY = "NO_CAPACITY"
    WINDOW_INFEASIBLE = "WINDOW_INFEASIBLE"
    DEFER_NEXT_DAY = "DEFER_NEXT_DAY"


ASSIGNED_REASONS = frozenset(
    {
        ReasonCode.NEAREST,
        ReasonCode.FREE_WINDOW,
        ReasonCode.OPTIMIZED,
        ReasonCode.REASSIGNED_AFTER_REFUSAL,
        ReasonCode.MANUAL_DISPATCHER,
        ReasonCode.AUTO_AFTER_TIMEOUT,
    }
)
UNASSIGNED_REASONS = frozenset(
    {
        ReasonCode.NO_CAPACITY,
        ReasonCode.WINDOW_INFEASIBLE,
        ReasonCode.DEFER_NEXT_DAY,
    }
)


class LatLng(BaseModel):
    lat: float = Field(ge=-90.0, le=90.0)
    lon: float = Field(ge=-180.0, le=180.0)


class Office(LatLng):
    id: UUID
    name: str


class CourierPosition(LatLng):
    """Последняя известная позиция курьера; пригодится при перепланировании
    дня посреди смены. (Где начинается маршрут в середине дня — открытый
    вопрос контракта; модель готова к любому ответу.)"""

    at: AwareDatetime


class Courier(BaseModel):
    id: UUID
    name: str
    office_id: UUID
    shift_start: AwareDatetime
    shift_end: AwareDatetime
    position: Optional[CourierPosition] = None
    is_available: bool = True

    @model_validator(mode="after")
    def _shift_not_empty(self) -> "Courier":
        if self.shift_end <= self.shift_start:
            raise ValueError("shift_end must be after shift_start")
        return self


class Order(LatLng):
    id: UUID
    client_name: str
    window_from: AwareDatetime
    window_to: AwareDatetime
    service_duration_s: int = Field(ge=0)
    service_date: date
    # service_order.version: попадёт в assignment.source_order_version при
    # записи в БД (этап 5); через ядро проходит без изменений.
    version: int = Field(ge=0)

    @model_validator(mode="after")
    def _windows_consistent(self) -> "Order":
        if self.window_to < self.window_from:
            raise ValueError("window_to must not be before window_from")
        # Если команда когда-нибудь разрешит окнам «переливаться» за полночь
        # мимо service_date, достаточно убрать эту проверку.
        if self.service_date != self.window_from.date():
            raise ValueError("service_date must match the date of window_from")
        return self


class SolverConfig(BaseModel):
    speed_kmh: float = Field(gt=0)
    window_mode: WindowMode = WindowMode.SOFT
    return_to_office: bool = False


class Problem(BaseModel):
    plan_date: date
    now: AwareDatetime
    algorithm: Algorithm
    config: SolverConfig
    offices: list[Office]
    couriers: list[Courier]
    orders: list[Order]

    @model_validator(mode="after")
    def _references_are_valid(self) -> "Problem":
        for label, items in (
            ("office", self.offices),
            ("courier", self.couriers),
            ("order", self.orders),
        ):
            ids = [item.id for item in items]
            if len(ids) != len(set(ids)):
                raise ValueError(f"duplicate {label} id in problem")
        office_ids = {office.id for office in self.offices}
        for courier in self.couriers:
            if courier.office_id not in office_ids:
                raise ValueError(
                    f"courier {courier.id} references unknown office {courier.office_id}"
                )
        return self


class RouteStop(BaseModel):
    sequence: int = Field(ge=1)
    type: StopType
    order_id: Optional[UUID] = None
    office_id: Optional[UUID] = None
    arrival_eta: AwareDatetime
    departure_eta: AwareDatetime
    distance_from_prev_m: int = Field(ge=0)
    window_violation_s: int = Field(ge=0, default=0)

    @model_validator(mode="after")
    def _type_refs_and_time_order(self) -> "RouteStop":
        if self.type == StopType.ORDER and (
            self.order_id is None or self.office_id is not None
        ):
            raise ValueError("ORDER stop needs order_id and no office_id")
        if self.type == StopType.OFFICE and (
            self.office_id is None or self.order_id is not None
        ):
            raise ValueError("OFFICE stop needs office_id and no order_id")
        if self.departure_eta < self.arrival_eta:
            raise ValueError("departure_eta must not be before arrival_eta")
        return self


class Route(BaseModel):
    id: UUID
    courier_id: UUID
    plan_date: date
    algorithm: Algorithm
    stops: list[RouteStop] = Field(min_length=1)

    # Агрегаты выводятся из стопов и нигде не хранятся: маршрут не может
    # противоречить своим же стопам, а маппинг в БД (этап 5) читает свойства.
    @property
    def total_distance_m(self) -> int:
        return sum(stop.distance_from_prev_m for stop in self.stops)

    @property
    def violated_windows(self) -> int:
        return sum(1 for stop in self.stops if stop.window_violation_s > 0)


class Assignment(BaseModel):
    """Решение по одному заказу; повторяет таблицу ``assignment`` из контракта:
    неназначенный заказ — это courier_id и route_id, оба None."""

    order_id: UUID
    courier_id: Optional[UUID] = None
    route_id: Optional[UUID] = None
    reason_code: ReasonCode
    reason_text: str

    @model_validator(mode="after")
    def _assigned_has_both_refs(self) -> "Assignment":
        if (self.courier_id is None) != (self.route_id is None):
            raise ValueError(
                "assigned order must reference both courier_id and route_id, "
                "unassigned neither"
            )
        return self


class Metrics(BaseModel):
    total_distance_m: int
    violated_windows: int
    unassigned_count: int
    solve_time_ms: int


class Solution(BaseModel):
    algorithm: Algorithm
    routes: list[Route] = Field(default_factory=list)
    assignments: list[Assignment] = Field(default_factory=list)
    solve_time_ms: int = Field(ge=0, default=0)

    @property
    def unassigned(self) -> list[Assignment]:
        return [a for a in self.assignments if a.courier_id is None]

    @property
    def metrics(self) -> Metrics:
        return Metrics(
            total_distance_m=sum(route.total_distance_m for route in self.routes),
            violated_windows=sum(route.violated_windows for route in self.routes),
            unassigned_count=len(self.unassigned),
            solve_time_ms=self.solve_time_ms,
        )
