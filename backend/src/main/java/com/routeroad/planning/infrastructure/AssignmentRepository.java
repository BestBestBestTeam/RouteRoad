package com.routeroad.planning.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssignmentRepository extends Repository<AssignmentEntity, AssignmentId> {

    Optional<AssignmentEntity> findById(AssignmentId id);

    Page<AssignmentEntity> findById_OrderIdOrderById_PlanVersionDesc(UUID orderId, Pageable pageable);

    List<AssignmentEntity> findById_PlanVersion(Long planVersion);
}
