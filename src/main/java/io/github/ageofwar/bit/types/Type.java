package io.github.ageofwar.bit.types;

import io.github.ageofwar.bit.resolver.ResolvedBit;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public sealed interface Type {
    record Any() implements Type {
        @Override
        public java.lang.String toString() {
            return safeToString();
        }
        @Override
        public boolean equals(Object obj) {
            return safeEquals(obj);
        }
        @Override
        public int hashCode() {
            return safeHashCode();
        }
    }
    record Never() implements Type {
        @Override
        public java.lang.String toString() {
            return safeToString();
        }
        @Override
        public boolean equals(Object obj) {
            return safeEquals(obj);
        }
        @Override
        public int hashCode() {
            return safeHashCode();
        }
    }
    record Nominal(java.lang.String name) implements Type {
        @Override
        public java.lang.String toString() {
            return safeToString();
        }
        @Override
        public boolean equals(Object obj) {
            return safeEquals(obj);
        }
        @Override
        public int hashCode() {
            return safeHashCode();
        }
    }
    record NumberLiteral(BigInteger value) implements Type {
        @Override
        public java.lang.String toString() {
            return safeToString();
        }
        @Override
        public boolean equals(Object obj) {
            return safeEquals(obj);
        }
        @Override
        public int hashCode() {
            return safeHashCode();
        }
    }
    record Integer() implements Type {
        @Override
        public java.lang.String toString() {
            return safeToString();
        }
        @Override
        public boolean equals(Object obj) {
            return safeEquals(obj);
        }
        @Override
        public int hashCode() {
            return safeHashCode();
        }
    }
    record Struct(Map<java.lang.String, Type> fields) implements Type {
        @Override
        public java.lang.String toString() {
            return safeToString();
        }
        @Override
        public boolean equals(Object obj) {
            return safeEquals(obj);
        }
        @Override
        public int hashCode() {
            return safeHashCode();
        }
    }
    record Union(Type... types) implements Type {
        @Override
        public java.lang.String toString() {
            return safeToString();
        }
        @Override
        public boolean equals(Object obj) {
            return safeEquals(obj);
        }
        @Override
        public int hashCode() {
            return safeHashCode();
        }
    }
    record Intersection(Type... types) implements Type {
        @Override
        public java.lang.String toString() {
            return safeToString();
        }
        @Override
        public boolean equals(Object obj) {
            return safeEquals(obj);
        }
        @Override
        public int hashCode() {
            return safeHashCode();
        }
    }
    record Function(Type returnType, List<TypeVariable> generics, Type... parameters) implements Type {
        @Override
        public java.lang.String toString() {
            return safeToString();
        }
        @Override
        public boolean equals(Object obj) {
            return safeEquals(obj);
        }
        @Override
        public int hashCode() {
            return safeHashCode();
        }
    }
    final class TypeVariable implements Type {
        private final Type bounds;
        private ResolvedBit.Symbol symbol;

        public TypeVariable(Type bounds) {
            this.bounds = bounds;
        }

        @Override
        public java.lang.String toString() {
            return safeToString();
        }

        @Override
        public boolean equals(Object obj) {
            return safeEquals(obj);
        }

        @Override
        public int hashCode() {
            return safeHashCode();
        }

        public Type bounds() {
            return bounds;
        }

        public ResolvedBit.Symbol name() {
            return symbol;
        }

        public void setSymbol(ResolvedBit.Symbol symbol) {
            this.symbol = symbol;
        }
    }

    default String safeToString() {
        return safeToString(new IdentityHashMap<>());
    }

    private String safeToString(Map<Type, Boolean> visited) {
        if (visited.containsKey(this))
            return "...";
        visited.put(this, true);

        return switch (this) {
            case Any a -> "Any";
            case Never n -> "Never";
            case Nominal n -> n.name();
            case NumberLiteral n -> n.value().toString();
            case Integer i -> "Integer";
            case Struct s -> {
                var inner = s.fields().entrySet().stream()
                        .map(e -> e.getKey() + ": " + e.getValue().safeToString(visited))
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("");
                yield "[ " + inner + " ]";
            }
            case Union u -> "(" + String.join(" | ",
                    Arrays.stream(u.types()).map(t -> t.safeToString(visited)).toList()) + ")";
            case Intersection i -> "(" + String.join(" & ",
                    Arrays.stream(i.types()).map(t -> t.safeToString(visited)).toList()) + ")";
            case Function f -> {
                var genericsString = f.generics().isEmpty() ? "" :
                        "<" + String.join(", ", f.generics().stream().map(TypeVariable::toString).toList()) + ">";
                var params = String.join(", ",
                        Arrays.stream(f.parameters()).map(t -> t.safeToString(visited)).toList());
                yield genericsString + "(" + params + ") -> " + f.returnType().safeToString(visited);
            }
            case TypeVariable v -> {
                var sym = v.name() == null ? "?" : v.name().toString();
                yield sym + ": " + v.bounds().safeToString(visited);
            }
        };
    }

    default boolean safeEquals(Object other) {
        if (!(other instanceof Type o)) return false;
        return safeEquals(o, new IdentityHashMap<>(), new IdentityHashMap<>());
    }

    private boolean safeEquals(Type other, Map<Type, Type> visitedA, Map<Type, Type> visitedB) {
        if (this == other) return true;
        if (other == null) return false;
        if (visitedA.containsKey(this) && visitedB.containsKey(other))
            return visitedA.get(this) == other && visitedB.get(other) == this;

        visitedA.put(this, other);
        visitedB.put(other, this);

        if (!this.getClass().equals(other.getClass())) return false;

        return switch (this) {
            case Any a -> true;
            case Never n -> true;
            case Nominal n -> n.name().equals(((Nominal) other).name());
            case NumberLiteral n -> n.value().equals(((NumberLiteral) other).value());
            case Integer i -> true;
            case Struct s -> {
                var o = (Struct) other;
                if (s.fields().size() != o.fields().size()) yield false;
                var ok = true;
                for (var e : s.fields().entrySet()) {
                    var oType = o.fields().get(e.getKey());
                    if (oType == null || !e.getValue().safeEquals(oType, visitedA, visitedB)) {
                        ok = false;
                        break;
                    }
                }
                yield ok;
            }
            case Union u -> Arrays.equals(u.types(), ((Union) other).types());
            case Intersection i -> Arrays.equals(i.types(), ((Intersection) other).types());
            case Function f -> {
                var o = (Function) other;
                if (!f.returnType().safeEquals(o.returnType(), visitedA, visitedB)) yield false;
                if (f.generics().size() != o.generics().size()) yield false;
                if (f.parameters().length != o.parameters().length) yield false;
                var ok = true;
                for (int j = 0; j < f.parameters().length; j++) {
                    if (!f.parameters()[j].safeEquals(o.parameters()[j], visitedA, visitedB)) {
                        ok = false;
                        break;
                    }
                }
                yield ok;
            }
            case TypeVariable v -> v.bounds().safeEquals(((TypeVariable) other).bounds(), visitedA, visitedB);
        };
    }

    default int safeHashCode() {
        return safeHashCode(new IdentityHashMap<>());
    }

    private int safeHashCode(Map<Type, Boolean> visited) {
        if (visited.containsKey(this)) return 0x9e3779b9; // numero fisso per cicli
        visited.put(this, true);

        return switch (this) {
            case Any a -> 1;
            case Never n -> 2;
            case Nominal n -> n.name().hashCode();
            case NumberLiteral n -> n.value().hashCode();
            case Integer i -> 3;
            case Struct s -> s.fields().entrySet().stream()
                    .mapToInt(e -> e.getKey().hashCode() * 31 + e.getValue().safeHashCode(visited))
                    .reduce(7, (a, b) -> a * 31 + b);
            case Union u -> Arrays.stream(u.types())
                    .mapToInt(t -> t.safeHashCode(visited)).reduce(5, (a, b) -> a * 31 + b);
            case Intersection i -> Arrays.stream(i.types())
                    .mapToInt(t -> t.safeHashCode(visited)).reduce(11, (a, b) -> a * 31 + b);
            case Function f -> f.returnType().safeHashCode(visited)
                    + f.generics().size() * 97
                    + Arrays.stream(f.parameters())
                    .mapToInt(p -> p.safeHashCode(visited))
                    .reduce(13, (a, b) -> a * 31 + b);
            case TypeVariable v -> v.bounds().safeHashCode(visited);
        };
    }
}
