package com.routeroad.workday.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WorkdayCounts(@JsonProperty("orders_total") int ordersTotal,
                            @JsonProperty("orders_assigned") int ordersAssigned,
                            @JsonProperty("orders_completed") int ordersCompleted,
                            @JsonProperty("couriers_total") int couriersTotal,
                            @JsonProperty("open_alerts") int openAlerts) {}
