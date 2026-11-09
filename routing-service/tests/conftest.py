from __future__ import annotations

import pytest

from tests.factories import Scenario, build_scenario


@pytest.fixture
def scenario() -> Scenario:
    return build_scenario()
