package com.routeroad.execution.infrastructure;

import com.routeroad.shared.api.Change;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class HistoryChanges {
    private static final Set<String> VERSIONS = Set.of("version", "plan_version", "source_order_version", "base_version", "revision");
    private final JsonMapper json;
    public HistoryChanges(JsonMapper json) { this.json = json; }

    public List<Change> read(String value) {
        if (value == null) return List.of();
        try {
            var node = json.readTree(value);
            if (!node.isArray()) return List.of();
            var changes = new ArrayList<Change>();
            for (var change : node) {
                if (!change.isObject() || !change.path("field").isString() || change.path("field").asString().isBlank()
                        || !change.has("before") || !change.has("after")) return List.of();
                var field = change.path("field").asString();
                changes.add(new Change(field, normalize(change.get("before"), field), normalize(change.get("after"), field)));
            }
            return List.copyOf(changes);
        } catch (RuntimeException ex) {
            // A malformed optional audit fragment must not prevent reading the event itself.
            return List.of();
        }
    }
    private JsonNode normalize(JsonNode value, String field) {
        if (value.isNull()) return value;
        var leaf = field.substring(field.lastIndexOf('.') + 1);
        if (VERSIONS.contains(leaf)) {
            if (!value.isIntegralNumber() && !value.isString()) throw new IllegalArgumentException("Invalid version");
            return json.valueToTree(Long.toString(Long.parseLong(value.asString())));
        }
        if (value.isObject()) {
            var object = json.createObjectNode();
            value.properties().forEach(entry -> object.set(entry.getKey(), normalize(entry.getValue(), entry.getKey())));
            return object;
        }
        if (value.isArray()) {
            var array = json.createArrayNode();
            value.forEach(item -> array.add(normalize(item, "")));
            return array;
        }
        return value.deepCopy();
    }
}
