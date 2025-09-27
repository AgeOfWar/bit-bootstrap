package io.github.ageofwar.bit.interpreter;

import java.util.Map;

public record Struct(Map<String, Object> fields) {
    public Object getField(String fieldName) {
        return fields.get(fieldName);
    }
}
