"""Интерфейс солвера: greedy (этап 2) и ortools (этап 3) будут его реализовывать."""
from __future__ import annotations

from typing import Protocol

from app.core.model import Algorithm, Problem, Solution
from app.core.time_matrix import TravelTimeMatrix


class Solver(Protocol):
    name: Algorithm

    def solve(self, problem: Problem, matrix: TravelTimeMatrix) -> Solution: ...
