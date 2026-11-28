"""Статистика сложности случайных сценариев: только greedy (быстро).

Запуск из routing-service:  python scripts/scenario_stats.py
По каждой ширине окон: сценарии с неназначенными заявками, с нарушениями
окон, средняя доля назначенных. Нужно понять, что сценарии не слишком лёгкие.
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))  # пакет app рядом

from app.core.greedy import GreedySolver
from app.core.time_matrix import HaversineMatrix
from tests.random_problems import random_problem

SEEDS = range(1, 6)
SIZES = [(8, 2), (15, 3), (25, 4)]
WIDTHS = ["tight", "medium", "loose"]


def main() -> None:
    for width in WIDTHS:
        with_unassigned = 0
        with_violations = 0
        shares: list[float] = []
        for seed, (n_orders, n_couriers) in product_seeds():
            problem = random_problem(seed, n_orders, n_couriers, width)
            solution = GreedySolver().solve(problem, HaversineMatrix(
                problem.config.speed_kmh))
            m = solution.metrics
            if m.unassigned_count:
                with_unassigned += 1
            if m.violated_windows:
                with_violations += 1
            shares.append((n_orders - m.unassigned_count) / n_orders)
        total = len(shares)
        print(f"{width:7}: сценариев {total}, с неназначенными {with_unassigned}, "
              f"с нарушениями окон {with_violations}, "
              f"средняя доля назначенных {sum(shares) / total:.2f}")


def product_seeds():
    for seed in SEEDS:
        for size in SIZES:
            yield seed, size


if __name__ == "__main__":
    main()
