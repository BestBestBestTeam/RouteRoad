package com.routeroad.auth.application;

public class AccountException extends RuntimeException {
    public enum Kind { INVALID, UNAUTHENTICATED, CONFLICT, SCENARIO_RESET, NOT_FOUND }
    private final Kind kind;
    public AccountException(Kind kind, String message) { super(message); this.kind = kind; }
    public Kind kind() { return kind; }
}
