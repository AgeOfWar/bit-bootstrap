package io.github.ageofwar.bit.types;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Stream;

public class Types {
    private static final Type ANY = new Type.Any();
    private static final Type NEVER = new Type.Never();
    private static final Type NONE = new Type.Nominal("None");
    private static final Type TRUE = new Type.Nominal("true");
    private static final Type FALSE = new Type.Nominal("false");
    private static final Type INTEGER = new Type.Integer();
    private static final Type STRING = new Type.String();

    private Types() {
    }

    public static Type integer() {
        return INTEGER;
    }

    public static Type integer(BigInteger value) {
        return new Type.NumberLiteral(value);
    }

    public static Type integer(String value) {
        return integer(new BigInteger(value));
    }

    public static Type any() {
        return ANY;
    }

    public static Type never() {
        return NEVER;
    }

    public static Type none() {
        return NONE;
    }

    public static Type _true() {
        return TRUE;
    }

    public static Type _false() {
        return FALSE;
    }

    public static Type _boolean() {
        return new Type.Union(_true(), _false());
    }

    public static Type string(String value) {
        return new Type.StringLiteral(value);
    }

    public static Type string() {
        return STRING;
    }

    public static Type struct(Map<String, Type> fields) {
        return new Type.Struct(fields);
    }

    public static Type function(Type returnType, Type... parameters) {
        return new Type.Function(returnType, parameters);
    }

    public static Type nominal(String name) {
        return new Type.Nominal(name);
    }

    public static Type union(Type... types) {
        var actualTypes = new ArrayList<Type>();

        for (var type : types) {
            if (type == ANY) return ANY;
            if (type == NEVER) continue;
            if (type instanceof Type.Union(var subTypes)) {
                for (var subType : subTypes) {
                    if (subType == ANY) return ANY;
                    if (subType == NEVER) continue;
                    if (actualTypes.stream().anyMatch(t -> extend(subType, t))) continue;
                    actualTypes.removeIf(t -> extend(t, subType));
                    actualTypes.add(subType);
                }
                continue;
            }
            if (actualTypes.stream().anyMatch(t -> extend(type, t))) continue;
            actualTypes.removeIf(t -> extend(t, type));
            actualTypes.add(type);
        }

        if (actualTypes.isEmpty()) return NEVER;
        if (actualTypes.size() == 1) return actualTypes.getFirst();

        return new Type.Union(actualTypes.toArray(Type[]::new));
    }

    public static Type intersection(Type... types) {
        var actualTypes = new ArrayList<Type>();

        for (var type : types) {
            if (type == ANY) continue;
            if (type == NEVER) return NEVER;
            if (type instanceof Type.Intersection(var subTypes)) {
                for (var subType : subTypes) {
                    if (subType == ANY) continue;
                    if (subType == NEVER) return NEVER;
                    if (actualTypes.stream().anyMatch(t -> extend(t, subType))) continue;
                    actualTypes.removeIf(t -> extend(subType, t));
                    actualTypes.add(subType);
                }
                continue;
            }
            if (actualTypes.stream().anyMatch(t -> extend(t, type))) continue;
            actualTypes.removeIf(t -> extend(type, t));
            actualTypes.add(type);
        }

        if (actualTypes.isEmpty()) return ANY;
        if (actualTypes.size() == 1) return actualTypes.getFirst();

        for (var type1 : actualTypes) {
            for (var type2 : actualTypes) {
                if (type1 != type2 && !compatible(type1, type2)) {
                    return NEVER;
                }
            }
        }

        return new Type.Intersection(actualTypes.toArray(Type[]::new));
    }

    private static boolean compatible(Type type, Type other) {
        return switch (type) {
            case Type.Struct struct -> {
                if (!(other instanceof Type.Struct(var fields))) yield false;
                for (var entry : struct.fields().entrySet()) {
                    var otherField = fields.get(entry.getKey());
                    if (otherField != null && !compatible(entry.getValue(), otherField)) yield false;
                }
                yield true;
            }
            default -> false;
        };
    }

    public static Type typeOf(Type type) {
        return switch (type) {
            case Type.Never never -> struct(Map.of("type", string("Never")));
            case Type.Any any -> struct(Map.of("type", string("Any")));
            case Type.Nominal nominal -> struct(Map.of("type", string(nominal.name())));
            case Type.NumberLiteral numberLiteral -> struct(Map.of("type", string("Number"), "value", integer(numberLiteral.value())));
            case Type.StringLiteral stringLiteral -> struct(Map.of("type", string("String"), "value", string(stringLiteral.value())));
            case Type.Integer integer -> struct(Map.of("type", string("Integer")));
            case Type.String string -> struct(Map.of("type", string("String")));
            case Type.Struct struct -> struct(Map.of("type", string("Struct"), "fields", struct(struct.fields().entrySet().stream().map(e -> Map.entry(e.getKey(), typeOf(e.getValue()))).collect(HashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()), HashMap::putAll))));
            case Type.Union union -> struct(Map.of("type", string("Union"), "types", union(Stream.of(union.types()).map(Types::typeOf).toArray(Type[]::new))));
            case Type.Intersection intersection -> struct(Map.of("type", string("Intersection"), "types", union(Stream.of(intersection.types()).map(Types::typeOf).toArray(Type[]::new))));
            case Type.Function function -> struct(Map.of("type", string("Function"), "returnType", typeOf(function.returnType()), "parameters", union(Stream.of(function.parameters()).map(Types::typeOf).toArray(Type[]::new))));
        };
    }

    // extends

    public static boolean extend(Type type, Type other) {
        if (type == other) return true;
        if (other == ANY) return true;

        return switch (type) {
            case Type.Never never -> true;
            case Type.Any any -> false;
            case Type.Nominal nominal -> extend(nominal, other);
            case Type.NumberLiteral numberLiteral -> extend(numberLiteral, other);
            case Type.StringLiteral stringLiteral -> extend(stringLiteral, other);
            case Type.Integer integer -> extend(integer, other);
            case Type.String string ->  extend(string, other);
            case Type.Union union -> extend(union, other);
            case Type.Intersection intersection -> extend(intersection, other);
            case Type.Function function -> extend(function, other);
            case Type.Struct struct -> extend(struct, other);
        };
    }

    private static boolean extend(Type.Nominal type, Type other) {
        if (other instanceof Type.Union(var types)) {
            return Stream.of(types).anyMatch(t -> extend(type, t));
        }
        return type == other;
    }

    private static boolean extend(Type.NumberLiteral type, Type other) {
        if (other == INTEGER) return true;
        if (other instanceof Type.Union(var types)) return Stream.of(types).anyMatch(t -> extend(type, t));
        return other instanceof Type.NumberLiteral(var value) && type.value().equals(value);
    }

    private static boolean extend(Type.StringLiteral type, Type other) {
        if (other == STRING) return true;
        if (other instanceof Type.Union(var types)) return Stream.of(types).anyMatch(t -> extend(type, t));
        return other instanceof Type.StringLiteral(var value) && type.value().equals(value);
    }

    private static boolean extend(Type.Integer type, Type other) {
        return other == INTEGER;
    }

    private static boolean extend(Type.String type, Type other) {
        return other == STRING;
    }

    private static boolean extend(Type.Union type, Type other) {
        for (var t : type.types()) {
            if (!extend(t, other)) return false;
        }
        return true;
    }

    private static boolean extend(Type.Intersection type, Type other) {
        for (var t : type.types()) {
            if (extend(t, other)) return true;
        }
        return false;
    }

    private static boolean extend(Type.Function function, Type other) {
        if (!(other instanceof Type.Function(var returnType, var parameters))) return false;
        if (!extend(function.returnType(), returnType)) return false;
        for (var i = 0;  i < parameters.length; i++) {
            if (!extend(parameters[i], function.parameters()[i])) return false;
        }
        return true;
    }

    private static boolean extend(Type.Struct type, Type other) {
        if (!(other instanceof Type.Struct(var fields))) return false;
        for (var entry : fields.entrySet()) {
            if (!extend(type.fields().get(entry.getKey()), entry.getValue())) return false;
        }
        return true;
    }

    // operations

    public static Type add(Type left, Type right) {
        if (!extend(left, integer())) throw new IllegalArgumentException("Left operand must be an integer type");
        if (!extend(right, integer())) throw new IllegalArgumentException("Right operand must be an integer type");
        if (left instanceof Type.NumberLiteral(var value1) && right instanceof Type.NumberLiteral(var value2)) {
            return integer(value1.add(value2));
        }
        if (left instanceof Type.Union(var types)) {
            return union(Stream.of(types).map(t -> add(t, right)).toArray(Type[]::new));
        }
        if (right instanceof Type.Union(var types)) {
            return union(Stream.of(types).map(t -> add(left, t)).toArray(Type[]::new));
        }
        return integer();
    }

    public static Type subtract(Type left, Type right) {
        if (!extend(left, integer())) throw new IllegalArgumentException("Left operand must be an integer type");
        if (!extend(right, integer())) throw new IllegalArgumentException("Right operand must be an integer type");
        if (left instanceof Type.NumberLiteral(var value1) && right instanceof Type.NumberLiteral(var value2)) {
            return integer(value1.subtract(value2));
        }
        if (left instanceof Type.Union(var types)) {
            return union(Stream.of(types).map(t -> subtract(t, right)).toArray(Type[]::new));
        }
        if (right instanceof Type.Union(var types)) {
            return union(Stream.of(types).map(t -> subtract(left, t)).toArray(Type[]::new));
        }
        return integer();
    }

    public static Type multiply(Type left, Type right) {
        if (!extend(left, integer())) throw new IllegalArgumentException("Left operand must be an integer type");
        if (!extend(right, integer())) throw new IllegalArgumentException("Right operand must be an integer type");
        if (left instanceof Type.NumberLiteral(var value1) && right instanceof Type.NumberLiteral(var value2)) {
            return integer(value1.multiply(value2));
        }
        if (left instanceof Type.Union(var types)) {
            return union(Stream.of(types).map(t -> multiply(t, right)).toArray(Type[]::new));
        }
        if (right instanceof Type.Union(var types)) {
            return union(Stream.of(types).map(t -> multiply(left, t)).toArray(Type[]::new));
        }
        return integer();
    }

    public static Type divide(Type left, Type right) {
        if (!extend(left, integer())) throw new IllegalArgumentException("Left operand must be an integer type");
        if (!extend(right, integer())) throw new IllegalArgumentException("Right operand must be an integer type");
        if (left instanceof Type.NumberLiteral(var value1) && right instanceof Type.NumberLiteral(var value2)) {
            if (value2.equals(BigInteger.ZERO)) throw new ArithmeticException("Division by zero");
            return integer(value1.divide(value2));
        }
        if (left instanceof Type.Union(var types)) {
            return union(Stream.of(types).map(t -> divide(t, right)).toArray(Type[]::new));
        }
        if (right instanceof Type.Union(var types)) {
            return union(Stream.of(types).map(t -> divide(left, t)).toArray(Type[]::new));
        }
        return integer();
    }

    public static Type divideExact(Type left, Type right) {
        if (!extend(left, integer())) throw new IllegalArgumentException("Left operand must be an integer type");
        if (!extend(right, integer())) throw new IllegalArgumentException("Right operand must be an integer type");
        if (left instanceof Type.NumberLiteral(var value1) && right instanceof Type.NumberLiteral(var value2)) {
            if (value2.equals(BigInteger.ZERO)) throw new ArithmeticException("Division by zero");
            var result = value1.divideAndRemainder(value2);
            if (result[1].equals(BigInteger.ZERO)) {
                return integer(result[0]);
            } else {
                return never();
            }
        }
        if (left instanceof Type.Union(var types)) {
            return union(Stream.of(types).map(t -> divide(t, right)).toArray(Type[]::new));
        }
        if (right instanceof Type.Union(var types)) {
            return union(Stream.of(types).map(t -> divide(left, t)).toArray(Type[]::new));
        }
        return integer();
    }

    public static Type lessThanOrEqual(Type left, Type right) {
        if (!extend(left, integer())) throw new IllegalArgumentException("Left operand must be an integer type");
        if (!extend(right, integer())) throw new IllegalArgumentException("Right operand must be an integer type");
        if (left instanceof Type.NumberLiteral(var value1) && right instanceof Type.NumberLiteral(var value2)) {
            return value1.compareTo(value2) <= 0 ? _true() : _false();
        }
        if (left instanceof Type.Union(var types)) {
            return union(Stream.of(types).map(t -> lessThanOrEqual(t, right)).toArray(Type[]::new));
        }
        if (right instanceof Type.Union(var types)) {
            return union(Stream.of(types).map(t -> lessThanOrEqual(left, t)).toArray(Type[]::new));
        }
        return _boolean();
    }

    public static Type greaterThanOrEqual(Type left, Type right) {
        if (!extend(left, integer())) throw new IllegalArgumentException("Left operand must be an integer type");
        if (!extend(right, integer())) throw new IllegalArgumentException("Right operand must be an integer type");
        if (left instanceof Type.NumberLiteral(var value1) && right instanceof Type.NumberLiteral(var value2)) {
            return value1.compareTo(value2) >= 0 ? _true() : _false();
        }
        if (left instanceof Type.Union(var types)) {
            return union(Stream.of(types).map(t -> greaterThanOrEqual(t, right)).toArray(Type[]::new));
        }
        if (right instanceof Type.Union(var types)) {
            return union(Stream.of(types).map(t -> greaterThanOrEqual(left, t)).toArray(Type[]::new));
        }
        return _boolean();
    }

    public static Type lessThan(Type left, Type right) {
        if (!extend(left, integer())) throw new IllegalArgumentException("Left operand must be an integer type");
        if (!extend(right, integer())) throw new IllegalArgumentException("Right operand must be an integer type");
        if (left instanceof Type.NumberLiteral(var value1) && right instanceof Type.NumberLiteral(var value2)) {
            return value1.compareTo(value2) < 0 ? _true() : _false();
        }
        if (left instanceof Type.Union(var types)) {
            return union(Stream.of(types).map(t -> lessThan(t, right)).toArray(Type[]::new));
        }
        if (right instanceof Type.Union(var types)) {
            return union(Stream.of(types).map(t -> lessThan(left, t)).toArray(Type[]::new));
        }
        return _boolean();
    }

    public static Type greaterThan(Type left, Type right) {
        if (!extend(left, integer())) throw new IllegalArgumentException("Left operand must be an integer type");
        if (!extend(right, integer())) throw new IllegalArgumentException("Right operand must be an integer type");
        if (left instanceof Type.NumberLiteral(var value1) && right instanceof Type.NumberLiteral(var value2)) {
            return value1.compareTo(value2) > 0 ? _true() : _false();
        }
        if (left instanceof Type.Union(var types)) {
            return union(Stream.of(types).map(t -> greaterThan(t, right)).toArray(Type[]::new));
        }
        if (right instanceof Type.Union(var types)) {
            return union(Stream.of(types).map(t -> greaterThan(left, t)).toArray(Type[]::new));
        }
        return _boolean();
    }

    public static Type equal(Type left, Type right) {
        if (!extend(left, integer())) throw new IllegalArgumentException("Left operand must be an integer type");
        if (!extend(right, integer())) throw new IllegalArgumentException("Right operand must be an integer type");
        if (left instanceof Type.NumberLiteral(var value1) && right instanceof Type.NumberLiteral(var value2)) {
            return value1.equals(value2) ? _true() : _false();
        }
        if (left instanceof Type.Union(var types)) {
            return union(Stream.of(types).map(t -> equal(t, right)).toArray(Type[]::new));
        }
        if (right instanceof Type.Union(var types)) {
            return union(Stream.of(types).map(t -> equal(left, t)).toArray(Type[]::new));
        }
        return _boolean();
    }

    public static Type notEqual(Type left, Type right) {
        if (!extend(left, integer())) throw new IllegalArgumentException("Left operand must be an integer type");
        if (!extend(right, integer())) throw new IllegalArgumentException("Right operand must be an integer type");
        if (left instanceof Type.NumberLiteral(var value1) && right instanceof Type.NumberLiteral(var value2)) {
            return !value1.equals(value2) ? _true() : _false();
        }
        if (left instanceof Type.Union(var types)) {
            return union(Stream.of(types).map(t -> notEqual(t, right)).toArray(Type[]::new));
        }
        if (right instanceof Type.Union(var types)) {
            return union(Stream.of(types).map(t -> notEqual(left, t)).toArray(Type[]::new));
        }
        return _boolean();
    }

    public static Type negate(Type type) {
        if (!extend(type, integer())) throw new IllegalArgumentException("Operand must be an integer type");
        if (type instanceof Type.NumberLiteral(var value)) {
            return integer(value.negate());
        }
        if (type instanceof Type.Union(var types)) {
            return union(Stream.of(types).map(Types::negate).toArray(Type[]::new));
        }
        return integer();
    }

    public static Type and(Type left, Type right) {
        if (!extend(left, _boolean())) throw new IllegalArgumentException("Left operand must be a boolean type");
        if (!extend(right, _boolean())) throw new IllegalArgumentException("Right operand must be a boolean type");
        if (left == _false() || right == _false()) return _false();
        if (left == _true() && right == _true()) return _true();
        return _boolean();
    }

    public static Type or(Type left, Type right) {
        if (!extend(left, _boolean())) throw new IllegalArgumentException("Left operand must be a boolean type");
        if (!extend(right, _boolean())) throw new IllegalArgumentException("Right operand must be a boolean type");
        if (left == _true() || right == _true()) return _true();
        if (left == _false() && right == _false()) return _false();
        return _boolean();
    }

    public static Type not(Type type) {
        if (!extend(type, _boolean())) throw new IllegalArgumentException("Operand must be a boolean type");
        if (type == _true()) return _false();
        if (type == _false()) return _true();
        return _boolean();
    }
}
