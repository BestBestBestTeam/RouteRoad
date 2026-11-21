package com.routeroad.couriers.application;

public class CourierNotFoundException extends RuntimeException {
    public CourierNotFoundException() { super("Представитель не найден"); }
}
