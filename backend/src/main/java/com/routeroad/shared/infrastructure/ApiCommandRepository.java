package com.routeroad.shared.infrastructure;

import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ApiCommandRepository extends JpaRepository<ApiCommandEntity, UUID> {

    Optional<ApiCommandEntity> findByActorIdAndIdempotencyKey(@Nullable UUID actorId, UUID idempotencyKey);
}
