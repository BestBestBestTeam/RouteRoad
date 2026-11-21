package com.routeroad.orders.application;

public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException() { super("Заявка не найдена"); }
}
