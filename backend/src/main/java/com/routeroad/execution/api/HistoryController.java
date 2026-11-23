package com.routeroad.execution.api;

import com.routeroad.auth.api.ApiError;
import com.routeroad.execution.application.HistoryQueryService;
import com.routeroad.shared.api.Page;
import com.routeroad.shared.api.QueryParameters;
import com.routeroad.simulation.application.ScenarioNotInitializedException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.format.DateTimeParseException;

@RestController
public class HistoryController {
    private final HistoryQueryService service;
    public HistoryController(HistoryQueryService service) { this.service = service; }
    @GetMapping("/api/history")
    public Page<HistoryEntry> get(@RequestParam(required=false) String date,
            @RequestParam(required=false) String search, @RequestParam(required=false) String type,
            @RequestParam(name="actor_role",required=false) String actorRole,
            @RequestParam(name="order_id",required=false) String orderId,
            @RequestParam(required=false) String page, @RequestParam(name="page_size",required=false) String pageSize) {
        return service.get(QueryParameters.date(date),search,type,actorRole,QueryParameters.uuid(orderId),
                QueryParameters.integer(page,1),QueryParameters.integer(pageSize,10));
    }
    @ExceptionHandler({IllegalArgumentException.class, DateTimeParseException.class})
    ResponseEntity<ApiError> invalid() {
        return ResponseEntity.badRequest().body(ApiError.of("INVALID_REQUEST","Некорректные параметры истории"));
    }
    @ExceptionHandler(ScenarioNotInitializedException.class)
    ResponseEntity<ApiError> noScenario() {
        return ResponseEntity.status(409).body(ApiError.of("SCENARIO_NOT_INITIALIZED","Сценарий ещё не подготовлен"));
    }
}
