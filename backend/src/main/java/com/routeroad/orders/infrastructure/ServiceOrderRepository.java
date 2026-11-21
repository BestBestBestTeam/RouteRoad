package com.routeroad.orders.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface ServiceOrderRepository extends JpaRepository<ServiceOrderEntity, UUID> {

    Optional<ServiceOrderEntity> findByNumber(String number);

    Page<ServiceOrderEntity> findByServiceDate(LocalDate serviceDate, Pageable pageable);
    interface WorkdayOrderCounts {
        long getOrdersTotal();
        long getOrdersAssigned();
        long getOrdersCompleted();
    }

    @Query(value = OrderReadSql.COUNTS, nativeQuery = true)
    WorkdayOrderCounts findWorkdayOrderCounts(@Param("date") LocalDate date);

    @Query(value = OrderReadSql.ROWS, nativeQuery = true)
    java.util.List<OrderListRow> findOrderList(@Param("date") LocalDate date, @Param("search") String search,
            @Param("status") String status, @Param("courierId") UUID courierId, @Param("assigned") Boolean assigned,
            @Param("limit") int limit, @Param("offset") long offset);

    @Query(value = OrderReadSql.TOTAL, nativeQuery = true)
    long countOrderList(@Param("date") LocalDate date, @Param("search") String search,
            @Param("status") String status, @Param("courierId") UUID courierId, @Param("assigned") Boolean assigned);

    @Query("select o.serviceDate from ServiceOrderEntity o where o.id = :id")
    Optional<LocalDate> findCurrentServiceDate(@Param("id") UUID id);

    @Query(value = OrderReadSql.DETAIL, nativeQuery = true)
    Optional<OrderListRow> findOrderDetail(@Param("id") UUID id, @Param("date") LocalDate date);

    interface ScheduleOrderRow extends OrderListRow {
        boolean getOrderRefused();
        boolean getDayRefused();
    }
    @Query(value = OrderReadSql.SCHEDULE, nativeQuery = true)
    java.util.List<ScheduleOrderRow> findSchedule(@Param("courierId") UUID courierId, @Param("date") LocalDate date);

    interface RefusalState {
        boolean getOrderRefused();
        boolean getDayRefused();
    }
    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM business_event e WHERE e.type = 'ORDER_REFUSED'
                    AND e.order_id = :orderId AND e.courier_id = :courierId) AS "orderRefused",
                EXISTS (SELECT 1 FROM business_event e WHERE e.type = 'COURIER_REFUSED'
                    AND e.courier_id = :courierId AND e.service_date = :date) AS "dayRefused"
            """, nativeQuery = true)
    RefusalState findRefusals(@Param("orderId") UUID orderId, @Param("courierId") UUID courierId,
                              @Param("date") LocalDate date);
}
