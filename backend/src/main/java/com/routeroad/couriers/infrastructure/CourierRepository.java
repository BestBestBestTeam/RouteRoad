package com.routeroad.couriers.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;

import java.util.UUID;

public interface CourierRepository extends JpaRepository<CourierEntity, UUID> {

    Page<CourierEntity> findByOfficeId(UUID officeId, Pageable pageable);

    @Query(value = CourierReadSql.ROWS, nativeQuery = true)
    List<CourierListRow> findCourierList(@Param("date") LocalDate date, @Param("search") String search,
            @Param("available") Boolean available, @Param("hasOpenAlerts") Boolean hasOpenAlerts,
            @Param("hasWorkload") Boolean hasWorkload, @Param("limit") int limit, @Param("offset") long offset);

    @Query(value = CourierReadSql.TOTAL, nativeQuery = true)
    long countCourierList(@Param("date") LocalDate date, @Param("search") String search,
            @Param("available") Boolean available, @Param("hasOpenAlerts") Boolean hasOpenAlerts,
            @Param("hasWorkload") Boolean hasWorkload);
}
