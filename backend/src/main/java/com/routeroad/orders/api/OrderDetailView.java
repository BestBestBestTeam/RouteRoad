package com.routeroad.orders.api;

import com.routeroad.execution.api.AlertView;
import com.routeroad.shared.api.Clock;
import java.util.List;

public record OrderDetailView(OrderView order, List<AlertView> alerts, Clock clock) {}
