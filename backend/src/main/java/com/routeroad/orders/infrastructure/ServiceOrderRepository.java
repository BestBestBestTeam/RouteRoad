package com.routeroad.orders.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface ServiceOrderRepository extends JpaRepository<ServiceOrderEntity, UUID> {

    Optional<ServiceOrderEntity> findByNumber(String number);

    Page<ServiceOrderEntity> findByServiceDate(LocalDate serviceDate, Pageable pageable);
}
