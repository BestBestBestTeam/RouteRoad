package com.routeroad.execution.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AlertOrderRepository extends JpaRepository<AlertOrderEntity, AlertOrderId> {

    List<AlertOrderEntity> findById_AlertId(UUID alertId);

    List<AlertOrderEntity> findById_OrderId(UUID orderId);
}
