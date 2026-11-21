package com.routeroad.workday.api;

import com.routeroad.auth.api.ApiError;
import com.routeroad.simulation.application.ScenarioNotInitializedException;
import com.routeroad.workday.application.WorkdayService;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@RestController
public class WorkdayController {
    private final WorkdayService service;

    public WorkdayController(WorkdayService service) {
        this.service = service;
    }

    @GetMapping("/api/workday")
    public WorkdayView get(@RequestParam(required = false) @Nullable String date) {
        return service.get(com.routeroad.shared.api.QueryParameters.date(date));
    }

    @ExceptionHandler({DateTimeParseException.class, IllegalArgumentException.class})
    ResponseEntity<ApiError> invalidDate() {
        return ResponseEntity.badRequest().body(ApiError.of("INVALID_REQUEST", "Ожидается корректная дата YYYY-MM-DD"));
    }

    @ExceptionHandler(ScenarioNotInitializedException.class)
    ResponseEntity<ApiError> missingScenario() {
        return ResponseEntity.status(409).body(ApiError.of("SCENARIO_NOT_INITIALIZED", "Сценарий ещё не подготовлен"));
    }
}
