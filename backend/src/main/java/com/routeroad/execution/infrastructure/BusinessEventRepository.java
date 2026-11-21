package com.routeroad.execution.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface BusinessEventRepository extends Repository<BusinessEventEntity, UUID> {

    boolean existsByTypeAndCourierIdAndServiceDate(com.routeroad.execution.domain.BusinessEventType type,
                                                  UUID courierId, LocalDate serviceDate);

    <S extends BusinessEventEntity> S save(S entity);

    Optional<BusinessEventEntity> findById(UUID id);

    Page<BusinessEventEntity> findByServiceDate(LocalDate serviceDate, Pageable pageable);

    Page<BusinessEventEntity> findByOrderId(UUID orderId, Pageable pageable);
}
