package com.routeroad.orders.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.routeroad.orders.domain.ServiceOrderStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record OrderView(UUID id, String number, @JsonProperty("client_name") String clientName,
        String address, String district, String product, double lat, double lon,
        @JsonProperty("window_from") Instant windowFrom, @JsonProperty("window_to") Instant windowTo,
        @JsonProperty("service_duration_s") int serviceDurationS, ServiceOrderStatus status,
        @JsonProperty("service_date") LocalDate serviceDate,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long version,
        @JsonProperty("day_status") ServiceOrderStatus dayStatus, AssignmentView assignment,
        @JsonProperty("available_actions") List<String> availableActions) {
            
    public OrderView withActions(List<String> actions) {
        return new OrderView(id, number, clientName, address, district, product, lat, lon, windowFrom, windowTo,
                serviceDurationS, status, serviceDate, version, dayStatus, assignment, actions);
    }
}
