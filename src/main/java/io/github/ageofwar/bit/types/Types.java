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

    public static Type string() {
        var fields = new HashMap<String, Type>();
        var string = struct(fields);
        fields.put("sequence", function(struct(Map.of("next", function(union(string, none()))))));
        fields.put("size", function(integer()));
        fields.put("get", function(string, integer()));
        return string;
    }

    public static Type struct(Map<String, Type> fields) {
        return new Type.Struct(fields);
    }

    public static Type function(Type returnType, Type... parameters) {
        return new Type.Function(returnType, List.of(), parameters);
    }

    public static Type function(Type returnType, List<Type.TypeVariable> generics, Type... parameters) {
        return new Type.Function(returnType, generics, parameters);
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
        var actualType = any();

        for (var type : types) {
            if (type == ANY) continue;
            if (type == NEVER) return NEVER;
            if (type instanceof Type.Intersection(var subTypes)) {
                for (var subType : subTypes) {
                    if (subType == ANY) continue;
                    if (subType == NEVER) return NEVER;
                    if (extend(actualType, subType)) continue;
                    if (extend(subType, actualType)) actualType = subType;
                }
                continue;
            }
            if (extend(actualType, type)) continue;
            if (extend(type, actualType)) actualType = type;
            if (actualType instanceof Type.Struct(var fields) && type instanceof Type.Struct(var newFields)) {
                var mergedFields = new HashMap<>(fields);
                mergedFields.putAll(newFields);
                for (var entry : newFields.entrySet()) {
                    if (fields.containsKey(entry.getKey())) {
                        mergedFields.put(entry.getKey(), intersection(fields.get(entry.getKey()), entry.getValue()));
                    }
                }
                actualType = struct(mergedFields);
            }
            if (actualType instanceof Type.Union(var unionTypes)) {
                actualType = union(Stream.of(unionTypes).filter(t -> extend(type, t)).toArray(Type[]::new));
            }
            if (type instanceof Type.Union(var unionTypes)) {
                var tt = actualType;
                actualType = union(Stream.of(unionTypes).filter(t -> extend(tt, t)).toArray(Type[]::new));
            }
        }
        return actualType;
    }

    public static Type.TypeVariable generic(Type extendsType) {
        return new Type.TypeVariable(extendsType);
    }

    public static Type complete(Type type, List<Type> arguments) {
        if (!(type instanceof Type.Function(var returnType, var generics, var parameters))) {
            throw new IllegalArgumentException("Type is not a function type: " + type);
        }

        if (generics.size() != arguments.size()) {
            throw new IllegalArgumentException("Expected " + generics.size() + " generic arguments, but got " + arguments.size());
        }
        var mapping = new HashMap<Type.TypeVariable, Type>();
        for (var i = 0; i < generics.size(); i++) {
            var generic = generics.get(i);
            var argument = arguments.get(i);
            if (!extend(argument, generic.bounds())) {
                throw new IllegalArgumentException("Generic argument " + argument + " does not extend " + generic.bounds());
            }
            mapping.put(generic, argument);
        }
        return complete(type, mapping);
    }

    public static Type complete(Type type, Map<Type.TypeVariable, Type> mapping) {
        return complete(type, mapping, new IdentityHashMap<>());
    }

    private static Type complete(Type type, Map<Type.TypeVariable, Type> mapping, Map<Type, Type> visited) {
        if (visited.containsKey(type)) {
            return visited.get(type);
        }

        Type result = switch (type) {
            case Type.TypeVariable t -> mapping.getOrDefault(type, type);
            case Type.Union(var types) ->
                    union(Stream.of(types)
                            .map(t -> complete(t, mapping, visited))
                            .toArray(Type[]::new));
            case Type.Intersection(var types) ->
                    intersection(Stream.of(types)
                            .map(t -> complete(t, mapping, visited))
                            .toArray(Type[]::new));
            case Type.Function(var returnType, var generics, var parameters) ->
                    function(
                            complete(returnType, mapping, visited),
                            generics,
                            Stream.of(parameters)
                                    .map(p -> complete(p, mapping, visited))
                                    .toArray(Type[]::new)
                    );
            case Type.Struct(var fields) -> {
                var copy = new HashMap<String, Type>();
                var struct = struct(copy);
                visited.put(type, struct); // Importante: registra PRIMA di ricorrere
                for (var e : fields.entrySet()) {
                    copy.put(e.getKey(), complete(e.getValue(), mapping, visited));
                }
                yield struct;
            }
            default -> type;
        };

        visited.put(type, result);
        return result;
    }

    public static Map<Type.TypeVariable, Type> unify(List<Type> partialTypes, List<Type> actualTypes) {
        var mapping = new HashMap<Type.TypeVariable, Type>();
        unify(partialTypes, actualTypes, mapping);
        return mapping;
    }

    private static void unify(List<Type> partialTypes, List<Type> actualTypes, Map<Type.TypeVariable, Type> mapping) {
        for (var i = 0; i < partialTypes.size(); i++) {
            unify(partialTypes.get(i), actualTypes.get(i), mapping);
        }
    }

    private static void unify(Type partialType, Type actualType, Map<Type.TypeVariable, Type> mapping) {
        unify(partialType, actualType, mapping, true);
    }

    private static void unify(Type partialType, Type actualType, Map<Type.TypeVariable, Type> mapping, boolean covariant) {
        if (partialType instanceof Type.TypeVariable typeVariable) {
            var mappedType = mapping.get(typeVariable);
            if (mappedType == null) {
                mapping.put(typeVariable, actualType);
            } else {
                mapping.put(typeVariable, covariant ? unifyCovariant(mappedType, actualType) : unifyContravariant(mappedType, actualType));
            }
        }

        switch (partialType) {
            case Type.Union(var partialTypes) -> {
                for (var type : partialTypes) {
                    if (extend(actualType, never())) {
                        unify(type, never(), mapping, covariant);
                    }
                    if (!(actualType instanceof Type.Union(var actualTypes))) continue;
                    if (extend(type, actualType)) {
                        unify(union(Arrays.stream(partialTypes).filter(t -> t != type).toArray(Type[]::new)), union(Arrays.stream(actualTypes).filter(t -> !extend(type, t)).toArray(Type[]::new)), mapping, covariant);
                    }
                }
            }
            case Type.Intersection(var partialTypes) -> {
                // TODO
            }
            case Type.Function(var returnType, var generics, var parameters) -> {
                var actualFn = (Type.Function) (actualType instanceof Type.Function ? actualType : function(never(), never(), never(), never(), never(), never(), never(), never(), never(), never()));

                for (var i = 0; i < generics.size(); i++) {
                    var partialGeneric = generics.get(i);
                    var actualGeneric = actualFn.generics().get(i);
                    unify(partialGeneric, actualGeneric, mapping, !covariant);
                }

                for (var i = 0; i < parameters.length; i++) {
                    unify(parameters[i], actualFn.parameters()[i], mapping, covariant);
                }

                unify(returnType, actualFn.returnType(), mapping, covariant);
            }
            case Type.Struct(var fields) -> unify(new ArrayList<>(fields.values()), new ArrayList<>( actualType instanceof Type.Struct ? ((Type.Struct) actualType).fields().values() : fields.values().stream().map((f) -> never()).toList()) , mapping);
            default -> {}
        }
    }

    private static Type unifyCovariant(Type type1, Type type2) {
        if (extend(type1, type2)) return type2;
        if (extend(type2, type1)) return type1;
        return union(type1, type2);
    }

    private static Type unifyContravariant(Type type1, Type type2) {
        if (extend(type1, type2)) return type1;
        if (extend(type2, type1)) return type2;
        return intersection(type1, type2);
    }

    // extends

    public static boolean extend(Type type, Type other) {
        return extend(type, other, new HashSet<>());
    }

    private record Pair<T, U>(T first, U second) {}
    private static boolean extend(Type type, Type other, Set<Pair<Type, Type>> visited) {
        var pair = new Pair<>(type, other);
        if (visited.contains(pair)) {
            // Se abbiamo già controllato questa coppia, evitiamo di rieseguire
            return true;
        }
        visited.add(pair);

        if (type == other) return true;
        if (other == any()) return true;

        return switch (type) {
            case Type.Never never -> true;
            case Type.Any any -> false;
            case Type.Nominal nominal -> extend(nominal, other, visited);
            case Type.NumberLiteral numberLiteral -> extend(numberLiteral, other, visited);
            case Type.Integer integer -> extend(integer, other, visited);
            case Type.Union union -> extend(union, other, visited);
            case Type.Intersection intersection -> extend(intersection, other, visited);
            case Type.Function function -> extend(function, other, visited);
            case Type.Struct struct -> extend(struct, other, visited);
            case Type.TypeVariable typeVariable -> extend(typeVariable, other, visited);
        };
    }

    private static boolean extend(Type.Nominal type, Type other, Set<Pair<Type, Type>> visited) {
        if (other instanceof Type.Union(var types)) {
            return Stream.of(types).anyMatch(t -> extend(type, t, visited));
        }
        return type == other;
    }

    private static boolean extend(Type.NumberLiteral type, Type other, Set<Pair<Type, Type>> visited) {
        if (other == INTEGER) return true;
        if (other instanceof Type.Union(var types)) return Stream.of(types).anyMatch(t -> extend(type, t, visited));
        return other instanceof Type.NumberLiteral(var value) && type.value().equals(value);
    }

    private static boolean extend(Type.Integer type, Type other, Set<Pair<Type, Type>> visited) {
        if (other instanceof Type.Union(var types)) return Stream.of(types).anyMatch(t -> extend(type, t, visited));
        return other == INTEGER;
    }

    private static boolean extend(Type.Union type, Type other, Set<Pair<Type, Type>> visited) {
        for (var t : type.types()) {
            if (!extend(t, other, visited)) return false;
        }
        return true;
    }

    private static boolean extend(Type.Intersection type, Type other, Set<Pair<Type, Type>> visited) {
        for (var t : type.types()) {
            if (extend(t, other, visited)) return true;
        }
        return false;
    }

    private static boolean extend(Type.Function function, Type other, Set<Pair<Type, Type>> visited) {
        if (other instanceof Type.Union(var types)) return Stream.of(types).anyMatch(t -> extend(function, t, visited));
        if (!(other instanceof Type.Function(var returnType, var generics, var parameters))) return false;
        if (!extend(function.returnType(), returnType, visited)) return false;
        for (var i = 0;  i < parameters.length; i++) {
            if (!extend(parameters[i], function.parameters()[i], visited)) return false;
        }
        return true;
    }

    private static boolean extend(Type.Struct type, Type other, Set<Pair<Type, Type>> visited) {
        if (other instanceof Type.Union(var types)) return Stream.of(types).anyMatch(t -> extend(type, t, visited));
        if (!(other instanceof Type.Struct(var fields))) return false;
        for (var entry : fields.entrySet()) {
            if (!type.fields().containsKey(entry.getKey())) return false;
            if (!extend(type.fields().get(entry.getKey()), entry.getValue(), visited)) return false;
        }
        return true;
    }

    private static boolean extend(Type.TypeVariable typeVariable, Type other, Set<Pair<Type, Type>> visited) {
        if (other instanceof Type.TypeVariable otherTypeVariable) return typeVariable.name().equals(otherTypeVariable.name());
        if (other instanceof Type.Union(var types)) return Stream.of(types).anyMatch(t -> extend(typeVariable, t, visited));
        return extend(typeVariable.bounds(), other, visited);
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
