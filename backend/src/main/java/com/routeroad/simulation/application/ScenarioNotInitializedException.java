package com.routeroad.simulation.application;

public class ScenarioNotInitializedException extends RuntimeException {
    public ScenarioNotInitializedException() {
        super("Сценарий ещё не подготовлен");
    }
}
