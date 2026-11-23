package com.routeroad.execution.infrastructure;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public interface OrderAlertRow {
    UUID getId();
    UUID getSourceEventId();
    LocalDate getServiceDate();
    UUID getCourierId();
    String getKind();
    String getScope();
    String getSeverity();
    String getStatus();
    String getReasonCode();
    String getReasonText();
    Instant getCreatedAt();
    Instant getDeadlineAt();
    Instant getResolvedAt();
    UUID getResolvedBy();
    String getResolutionReason();
    String getResolverName();
    String getResolverRole();
    UUID getOrderId();
    String getItemStatus();
    Instant getItemResolvedAt();
    UUID getItemResolvedBy();
    String getItemResolutionReason();
    String getItemResolverName();
    String getItemResolverRole();
    boolean getItemCanReassign();
}
