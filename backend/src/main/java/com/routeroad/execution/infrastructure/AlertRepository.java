package com.routeroad.execution.infrastructure;

import com.routeroad.execution.domain.AlertStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.UUID;

public interface AlertRepository extends JpaRepository<AlertEntity, UUID> {

    Page<AlertEntity> findByServiceDate(LocalDate serviceDate, Pageable pageable);

    Page<AlertEntity> findByServiceDateAndStatus(LocalDate serviceDate, AlertStatus status, Pageable pageable);
}
