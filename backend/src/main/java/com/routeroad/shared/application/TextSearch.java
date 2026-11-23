package com.routeroad.shared.application;

import java.util.Locale;

/** Literal substring pattern for SQL LIKE ... ESCAPE '!'. */
public final class TextSearch {
    private TextSearch() {}
    public static String contains(String value) {
        return value == null || value.isBlank() ? null : "%" + value.strip().toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
}
