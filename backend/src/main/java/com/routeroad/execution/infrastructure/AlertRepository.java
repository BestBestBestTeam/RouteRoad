package com.routeroad.execution.infrastructure;

import com.routeroad.execution.domain.AlertStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

import java.time.LocalDate;
import java.util.UUID;

public interface AlertRepository extends JpaRepository<AlertEntity, UUID> {

    Page<AlertEntity> findByServiceDate(LocalDate serviceDate, Pageable pageable);

    Page<AlertEntity> findByServiceDateAndStatus(LocalDate serviceDate, AlertStatus status, Pageable pageable);
    long countByServiceDateAndStatus(LocalDate serviceDate, AlertStatus status);
    long countByCourierIdAndServiceDateAndStatus(UUID courierId, LocalDate serviceDate, AlertStatus status);
    @Query(value = OrderAlertSql.RELATED, nativeQuery = true)
    List<OrderAlertRow> findForOrder(@Param("orderId") UUID orderId, @Param("date") LocalDate date,
                                    @Param("courierId") UUID courierId);
}
