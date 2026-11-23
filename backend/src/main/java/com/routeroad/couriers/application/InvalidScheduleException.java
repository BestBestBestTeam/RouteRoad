package com.routeroad.couriers.application;

/** A confirmed route contradicts the current confirmed assignments; never expose its partial contents. */
public class InvalidScheduleException extends RuntimeException {
    public InvalidScheduleException() { super("Confirmed route and assignments are inconsistent"); }
}
