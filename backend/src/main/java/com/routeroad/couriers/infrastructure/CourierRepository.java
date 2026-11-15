package com.routeroad.couriers.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CourierRepository extends JpaRepository<CourierEntity, UUID> {

    Page<CourierEntity> findByOfficeId(UUID officeId, Pageable pageable);
}
