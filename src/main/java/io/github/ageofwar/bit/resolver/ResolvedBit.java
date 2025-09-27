package io.github.ageofwar.bit.resolver;

import io.github.ageofwar.bit.types.Type;

import java.util.List;
import java.util.Map;

public sealed interface ResolvedBit {
    record Program(List<ResolvedBit.Declaration> declarations, ResolverEnvironment environment) implements ResolvedBit {}

    sealed interface Declaration extends ResolvedBit {
        Symbol name();

        record Variable(Symbol name, Expression value, io.github.ageofwar.bit.types.Type type) implements Declaration {}
        record VariableAssignment(Symbol name, Expression value) implements Declaration {}
        record Value(Symbol name, Expression value, io.github.ageofwar.bit.types.Type type) implements Declaration {}
        record Function(Symbol name, List<Parameter> parameters, Expression body, io.github.ageofwar.bit.types.Type type) implements Declaration {
            public record Parameter(Symbol name, io.github.ageofwar.bit.types.Type type) {}
        }
        record Type(Symbol name, Symbol valueName, List<TypeParameter> parameters, io.github.ageofwar.bit.types.Type value) implements Declaration {
            public record TypeParameter(io.github.ageofwar.bit.types.Type type) {}
        }
        record Class(Symbol name, Symbol valueName, Symbol thisSymbol, Constructor constructor, List<Member> members, io.github.ageofwar.bit.types.Type type) implements Declaration {
            public record Member(Declaration declaration, Visibility visibility) {
                public enum Visibility {
                    PUBLIC, PRIVATE
                }
            }
            public record Constructor(List<Parameter> parameters) {
                public record Parameter(Symbol name, io.github.ageofwar.bit.types.Type type) {}
            }
        }
        record Implementation(Symbol name, io.github.ageofwar.bit.types.Type receiver, List<Function> extensions) implements Declaration {
            public record Function(Symbol name, Symbol thisSymbol, List<Parameter> parameters, Expression body, io.github.ageofwar.bit.types.Type type) {
                public record Parameter(Symbol name, io.github.ageofwar.bit.types.Type type) {}
            }
        }
    }

    sealed interface Expression extends ResolvedBit {
        Type type();

        record Identifier(Symbol name, Type type) implements Expression {}
        record Call(Expression callee, List<Expression> arguments, Type type) implements Expression {}
        record Block(List<ResolvedBit> statements, Type type) implements Expression {}
        record NumberLiteral(String value, Type type) implements Expression {}
        record StringLiteral(String value, Type type) implements Expression {}
        record BooleanLiteral(boolean value, Type type) implements Expression {}
        record Minus(Expression lhs, Expression rhs, Type type) implements Expression {}
        record Plus(Expression lhs, Expression rhs, Type type) implements Expression {}
        record Multiply(Expression lhs, Expression rhs, Type type) implements Expression {}
        record Divide(Expression lhs, Expression rhs, Type type) implements Expression {}
        record GreaterThan(Expression lhs, Expression rhs, Type type) implements Expression {}
        record GreaterThanOrEqual(Expression lhs, Expression rhs, Type type) implements Expression {}
        record LessThan(Expression lhs, Expression rhs, Type type) implements Expression {}
        record LessThanOrEqual(Expression lhs, Expression rhs, Type type) implements Expression {}
        record Equal(Expression lhs, Expression rhs, Type type) implements Expression {}
        record NotEqual(Expression lhs, Expression rhs, Type type) implements Expression {}
        record And(Expression lhs, Expression rhs, Type type) implements Expression {}
        record Or(Expression lhs, Expression rhs, Type type) implements Expression {}
        record Not(Expression expression, Type type) implements Expression {}
        record If(Expression condition, Expression thenBranch, Expression elseBranch, Type type) implements Expression {}
        record As(Expression expression, Type type) implements Expression {}
        record Is(Expression expression, Type type) implements Expression {}
        record Access(Expression expression, String field, Type type) implements Expression {}
        record AccessExtension(Expression expression, Symbol name, Type type) implements Expression {}
        record Struct(Map<String, Expression> fields, Type type) implements Expression {}
        record Function(List<Parameter> parameters, Expression body, Type type) implements Expression {
            public record Parameter(Symbol name, Type type) {}
        }
        record Instantiation(Symbol className, List<Expression> arguments, Type type) implements Expression {}
    }

    final class Symbol {
        private final String name;
        private final int id;

        public Symbol(String name, int id) {
            this.name = name;
            this.id = id;
        }

        @Override
        public String toString() {
            return name + "#" + id;
        }

        public String name() {
            return name;
        }

        public int id() {
            return id;
        }
    }
}
