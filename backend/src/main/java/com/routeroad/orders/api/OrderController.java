package com.routeroad.orders.api;

import com.routeroad.auth.api.ApiError;
import com.routeroad.orders.application.OrderQueryService;
import com.routeroad.orders.application.OrderNotFoundException;
import org.springframework.security.core.Authentication;
import com.routeroad.orders.domain.ServiceOrderStatus;
import com.routeroad.shared.api.Page;
import com.routeroad.shared.api.QueryParameters;
import com.routeroad.simulation.application.ScenarioNotInitializedException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.format.DateTimeParseException;

@RestController
public class OrderController {
    private final OrderQueryService service;
    public OrderController(OrderQueryService service) { this.service = service; }
    
    @GetMapping("/api/orders")
    public Page<OrderView> get(@RequestParam(required = false) String date,
            @RequestParam(required = false) String search, @RequestParam(required = false) String status,
            @RequestParam(name = "courier_id", required = false) String courierId,
            @RequestParam(required = false) String assigned, @RequestParam(required = false) String page,
            @RequestParam(name = "page_size", required = false) String pageSize) {
        return service.get(QueryParameters.date(date), search,
                status == null ? null : ServiceOrderStatus.valueOf(status), QueryParameters.uuid(courierId),
                QueryParameters.bool(assigned), QueryParameters.integer(page, 1), QueryParameters.integer(pageSize, 10));
    }

    @GetMapping("/api/orders/{id}")
    public OrderDetailView detail(@PathVariable String id, @RequestParam(required = false) String date,
                                   Authentication authentication) {
        return service.detail(QueryParameters.uuid(id), QueryParameters.date(date), authentication.getName());
    }

    @ExceptionHandler(OrderNotFoundException.class)
    ResponseEntity<ApiError> notFound() {
        return ResponseEntity.status(404).body(ApiError.of("NOT_FOUND", "Заявка не найдена"));
    }

    @ExceptionHandler({IllegalArgumentException.class, DateTimeParseException.class})
    ResponseEntity<ApiError> invalid() {
        return ResponseEntity.badRequest().body(ApiError.of("INVALID_REQUEST", "Некорректные параметры заявки"));
    }

    @ExceptionHandler(ScenarioNotInitializedException.class)
    ResponseEntity<ApiError> missingScenario() {
        return ResponseEntity.status(409).body(ApiError.of("SCENARIO_NOT_INITIALIZED", "Сценарий ещё не подготовлен"));
    }
}
