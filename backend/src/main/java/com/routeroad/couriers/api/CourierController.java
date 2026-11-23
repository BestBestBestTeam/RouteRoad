package com.routeroad.couriers.api;

import com.routeroad.auth.api.ApiError;
import com.routeroad.couriers.application.CourierQueryService;
import com.routeroad.couriers.application.CourierScheduleService;
import com.routeroad.couriers.application.CourierNotFoundException;
import org.springframework.security.core.Authentication;
import com.routeroad.shared.api.Page;
import com.routeroad.shared.api.QueryParameters;
import com.routeroad.simulation.application.ScenarioNotInitializedException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.format.DateTimeParseException;

@RestController
public class CourierController {
    private final CourierQueryService service;
    private final CourierScheduleService schedules;
    public CourierController(CourierQueryService service, CourierScheduleService schedules) {
        this.service = service;
        this.schedules = schedules;
    }

    @GetMapping("/api/couriers")
    public Page<CourierView> get(@RequestParam(required = false) String date,
            @RequestParam(required = false) String search,
            @RequestParam(name = "is_available", required = false) String available,
            @RequestParam(name = "has_open_alerts", required = false) String hasOpenAlerts,
            @RequestParam(required = false) String workload,
            @RequestParam(required = false) String page,
            @RequestParam(name = "page_size", required = false) String pageSize) {
        Boolean hasWorkload = workload == null ? null : switch (workload) {
            case "assigned" -> true;
            case "unassigned" -> false;
            default -> throw new IllegalArgumentException("Unknown workload");
        };

        return service.get(QueryParameters.date(date), search, QueryParameters.bool(available),
                QueryParameters.bool(hasOpenAlerts), hasWorkload, QueryParameters.integer(page, 1),
                QueryParameters.integer(pageSize, 10));
    }

    @GetMapping("/api/couriers/{id}/schedule")
    public ScheduleView schedule(@PathVariable String id, @RequestParam(required = false) String date,
                                 Authentication authentication) {
        return schedules.get(QueryParameters.uuid(id), QueryParameters.date(date), authentication.getName());
    }

    @ExceptionHandler(CourierNotFoundException.class)
    ResponseEntity<ApiError> notFound() {
        return ResponseEntity.status(404).body(ApiError.of("NOT_FOUND", "Представитель не найден"));
    }

    @ExceptionHandler({IllegalArgumentException.class, DateTimeParseException.class})
    ResponseEntity<ApiError> invalid() {
        return ResponseEntity.badRequest().body(ApiError.of("INVALID_REQUEST", "Некорректные параметры запроса представителей"));
    }
    
    @ExceptionHandler(ScenarioNotInitializedException.class)
    ResponseEntity<ApiError> noScenario() {
        return ResponseEntity.status(409).body(ApiError.of("SCENARIO_NOT_INITIALIZED", "Сценарий ещё не подготовлен"));
    }
}
