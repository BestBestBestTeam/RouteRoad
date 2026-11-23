package com.routeroad.execution.domain;

import java.time.Instant;
import java.util.List;

/** Read-side actions; commands must independently recheck these constraints when implemented. */
public final class ReassignmentActions {
    private ReassignmentActions() {}
    public static boolean manualAllowed(Instant modelNow, Instant deadline) {
        return deadline != null && modelNow.isBefore(deadline);
    }
    public static List<String> forAlert(boolean eligible, Instant modelNow, Instant deadline) {
        if (!eligible) return List.of();
        return manualAllowed(modelNow, deadline) ? List.of("REASSIGN", "AUTO_REASSIGN") : List.of("AUTO_REASSIGN");
    }
}
