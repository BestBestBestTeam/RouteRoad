package com.routeroad.execution.api;

import com.routeroad.auth.api.ApiError;
import com.routeroad.execution.application.AlertQueryService;
import com.routeroad.execution.domain.AlertSeverity;
import com.routeroad.execution.domain.AlertStatus;
import com.routeroad.shared.api.Page;
import com.routeroad.shared.api.QueryParameters;
import com.routeroad.simulation.application.ScenarioNotInitializedException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.format.DateTimeParseException;

@RestController
public class AlertController {
    private final AlertQueryService service;
    public AlertController(AlertQueryService service) { this.service = service; }
    @GetMapping("/api/alerts")
    public Page<AlertView> get(@RequestParam(required=false) String date,
            @RequestParam(required=false) String search, @RequestParam(required=false) String status,
            @RequestParam(required=false) String severity, @RequestParam(required=false) String page,
            @RequestParam(name="page_size",required=false) String pageSize) {
        return service.get(QueryParameters.date(date), search, status == null ? null : switch(status) {
            case "open" -> AlertStatus.OPEN; case "resolved" -> AlertStatus.RESOLVED;
            default -> throw new IllegalArgumentException("Unknown alert status");
        }, severity == null ? null : switch(severity) {
            case "warning" -> AlertSeverity.WARNING; case "critical" -> AlertSeverity.CRITICAL;
            default -> throw new IllegalArgumentException("Unknown alert severity");
        }, QueryParameters.integer(page,1), QueryParameters.integer(pageSize,10));
    }
    @ExceptionHandler({IllegalArgumentException.class, DateTimeParseException.class})
    ResponseEntity<ApiError> invalid() {
        return ResponseEntity.badRequest().body(ApiError.of("INVALID_REQUEST","Некорректные параметры списка алертов"));
    }
    @ExceptionHandler(ScenarioNotInitializedException.class)
    ResponseEntity<ApiError> noScenario() {
        return ResponseEntity.status(409).body(ApiError.of("SCENARIO_NOT_INITIALIZED","Сценарий ещё не подготовлен"));
    }
}
