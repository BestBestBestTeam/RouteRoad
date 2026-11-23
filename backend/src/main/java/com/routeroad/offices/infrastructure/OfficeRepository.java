package com.routeroad.offices.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OfficeRepository extends JpaRepository<OfficeEntity, UUID> {
}
