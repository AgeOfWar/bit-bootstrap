package io.github.ageofwar.bit.interpreter;

import java.util.Map;
import java.util.StringJoiner;

public record Struct(Map<String, Object> fields) {
    public Object getField(String fieldName) {
        return fields.get(fieldName);
    }

    @Override
    public String toString() {
        var joiner = new StringJoiner(", ", "[", "]");
        fields.forEach((key, value) -> joiner.add(key + "=" + value));
        return joiner.toString();
    }
}
