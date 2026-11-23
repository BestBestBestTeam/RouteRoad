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
    @Query(value = """
            SELECT a.id, a.courier_id AS "courierId", ao.order_id AS "orderId"
            FROM alert a LEFT JOIN alert_order ao ON ao.alert_id = a.id
            WHERE a.service_date = :date AND a.status = 'open'
            ORDER BY a.id, ao.order_id
            """, nativeQuery = true)
    List<OpenAlertOrderRow> findOpenOrderLinks(@Param("date") LocalDate date);

    @Query(value = AlertReadSql.TOTAL, nativeQuery = true)
    long countAlertList(@Param("date") LocalDate date, @Param("search") String search,
                       @Param("status") String status, @Param("severity") String severity);

    @Query(value = AlertReadSql.IDS, nativeQuery = true)
    List<UUID> findAlertPageIds(@Param("date") LocalDate date, @Param("search") String search,
            @Param("status") String status, @Param("severity") String severity,
            @Param("limit") int limit, @Param("offset") long offset);

    @Query(value = OrderAlertSql.BATCH, nativeQuery = true)
    List<OrderAlertRow> findViewsByIds(@Param("ids") java.util.Collection<UUID> ids, @Param("date") LocalDate date);

    @Query(value = OrderAlertSql.RELATED, nativeQuery = true)
    List<OrderAlertRow> findForOrder(@Param("orderId") UUID orderId, @Param("date") LocalDate date,
                                    @Param("courierId") UUID courierId);
}
