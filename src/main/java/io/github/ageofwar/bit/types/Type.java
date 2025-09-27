package io.github.ageofwar.bit.types;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Map;

public sealed interface Type {
    record Any() implements Type {
        @Override
        public java.lang.String toString() {
            return "Any";
        }
    }
    record Never() implements Type {
        @Override
        public java.lang.String toString() {
            return "Never";
        }
    }
    record Nominal(java.lang.String name) implements Type {
        @Override
        public java.lang.String toString() {
            return name;
        }
    }
    record StringLiteral(java.lang.String value) implements Type {
        @Override
        public java.lang.String toString() {
            return "\"" + value + "\"";
        }
    }
    record NumberLiteral(BigInteger value) implements Type {
        @Override
        public java.lang.String toString() {
            return value.toString();
        }
    }
    record Integer() implements Type {
        @Override
        public java.lang.String toString() {
            return "Integer";
        }
    }
    record String() implements Type {
        @Override
        public java.lang.String toString() {
            return "String";
        }
    }
    record Struct(Map<java.lang.String, Type> fields) implements Type {
        public Type getFieldType(java.lang.String fieldName) {
            return fields.get(fieldName);
        }

        @Override
        public java.lang.String toString() {
            return "[ " + fields.entrySet().stream()
                    .map(entry -> entry.getKey() + ": " + entry.getValue())
                    .reduce((a, b) -> a + ", " + b).orElse("") + " ]";
        }
    }
    record Union(Type... types) implements Type {
        @Override
        public java.lang.String toString() {
            return "(" + java.lang.String.join(" | ", Arrays.stream(types).map(Type::toString).toList()) + ")";
        }
    }
    record Intersection(Type... types) implements Type {
        @Override
        public java.lang.String toString() {
            return "(" + java.lang.String.join(" & ", Arrays.stream(types).map(Type::toString).toList()) + ")";
        }
    }
    record Function(Type returnType, Type... parameters) implements Type {
        @Override
        public java.lang.String toString() {
            return "(" + java.lang.String.join(", ", Arrays.stream(parameters).map(Type::toString).toList()) + ") -> " + returnType;
        }
    }
}
