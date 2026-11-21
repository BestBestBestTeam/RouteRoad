package com.routeroad.orders.infrastructure;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public interface OrderListRow {
    UUID getId();
    String getNumber();
    String getClientName();
    String getAddress();
    String getDistrict();
    String getProduct();
    Double getLat();
    Double getLon();
    Instant getWindowFrom();
    Instant getWindowTo();
    Integer getServiceDurationS();
    String getStatus();
    LocalDate getServiceDate();
    Long getVersion();
    String getDayStatus();
    Long getPlanVersion();
    UUID getCourierId();
    UUID getRouteId();
    Long getSourceOrderVersion();
    String getReasonCode();
    String getReasonText();
    String getDecidedBy();
    Instant getDecisionCreatedAt();
    String getCourierName();
    UUID getActorId();
    String getActorName();
    String getActorRole();
    boolean getCanReassign();
}
