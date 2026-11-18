"""Инварианты на случайных сценариях: быстрый набор всегда, полный — под -m slow.

Солверы между собой не сравниваем: это делает бенчмарк, а OR-Tools
недетерминирован. Проверяем только: решение проходит validate_solution,
у каждой заявки ровно одно назначение, один seed даёт один Problem.
"""
from __future__ import annotations

from itertools import product

import pytest

from app.core.greedy import GreedySolver
from app.core.model import Problem, Solution
from app.core.ortools_solver import OrToolsSolver
from app.core.time_matrix import HaversineMatrix
from app.core.validator import validate_solution
from tests.random_problems import random_problem

SIZES = [(8, 2), (15, 3), (25, 4)]
WIDTHS = ["tight", "medium", "loose"]
# Быстрый набор: seeds 1-3, три размера, medium. Полный (под маркером slow):
# seeds 1-5, три размера, все ширины.
FAST_CASES = list(product(range(1, 4), SIZES))
SLOW_CASES = list(product(range(1, 6), SIZES, WIDTHS))
TIME_LIMIT_S = 1


def assert_invariants(problem: Problem, solution: Solution) -> None:
    matrix = HaversineMatrix(problem.config.speed_kmh)
    assert validate_solution(problem, solution, matrix) == []
    order_ids = [order.id for order in problem.orders]
    assigned_ids = [a.order_id for a in solution.assignments]
    assert sorted(assigned_ids) == sorted(order_ids)  # ровно по одному решению


@pytest.mark.parametrize("seed,size", FAST_CASES)
def test_random_scenario_invariants_fast(seed: int, size: tuple[int, int]) -> None:
    problem = random_problem(seed, *size)
    matrix = HaversineMatrix(problem.config.speed_kmh)
    assert_invariants(problem, GreedySolver().solve(problem, matrix))
    solver = OrToolsSolver(time_limit_s=TIME_LIMIT_S)
    assert_invariants(problem, solver.solve(problem, matrix))


@pytest.mark.slow
@pytest.mark.parametrize("seed,size,width", SLOW_CASES)
def test_random_scenario_invariants_full(
    seed: int, size: tuple[int, int], width: str
) -> None:
    problem = random_problem(seed, *size, window_width=width)
    matrix = HaversineMatrix(problem.config.speed_kmh)
    assert_invariants(problem, GreedySolver().solve(problem, matrix))
    solver = OrToolsSolver(time_limit_s=TIME_LIMIT_S)
    assert_invariants(problem, solver.solve(problem, matrix))


def test_same_seed_same_problem() -> None:
    for seed, size, width in SLOW_CASES[:5]:
        assert (random_problem(seed, *size, window_width=width)
                == random_problem(seed, *size, window_width=width))
