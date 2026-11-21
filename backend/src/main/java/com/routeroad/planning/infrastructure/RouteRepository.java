package com.routeroad.planning.infrastructure;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RouteRepository extends Repository<RouteEntity, UUID> {

    Optional<RouteEntity> findById(UUID id);

    Optional<RouteEntity> findByCourierIdAndPlanDateAndActiveTrue(UUID courierId, LocalDate planDate);

    List<RouteEntity> findByPlanDateAndActiveTrue(LocalDate planDate);

    List<RouteEntity> findByPlanningRun_PlanVersion(Long planVersion);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM route r
                WHERE r.plan_date = :date AND r.is_active AND EXISTS (
                    SELECT 1 FROM business_event e
                    WHERE e.type = 'PLAN_ACTIVATED' AND e.service_date = :date
                        AND e.payload ->> 'plan_version' = CAST(r.plan_version AS text)
                )
            )
            """, nativeQuery = true)
    boolean existsConfirmedActiveByPlanDate(@Param("date") LocalDate date);

    @Query(value = """
            SELECT r.* FROM route r
            WHERE r.courier_id = :courierId AND r.plan_date = :date AND r.is_active
                AND EXISTS (SELECT 1 FROM business_event e
                    WHERE e.type = 'PLAN_ACTIVATED' AND e.service_date = :date
                        AND e.payload ->> 'plan_version' = CAST(r.plan_version AS text))
            """, nativeQuery = true)
    Optional<RouteEntity> findConfirmedActive(@Param("courierId") UUID courierId, @Param("date") LocalDate date);

    /** Requires a caller transaction; flushes and clears its persistence context. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RouteEntity r set r.active = :active where r.id = :id")
    int setActive(@Param("id") UUID id, @Param("active") boolean active);
}
