package io.github.ageofwar.bit.resolver;

import io.github.ageofwar.bit.types.Type;

import java.util.List;
import java.util.Map;

public sealed interface ResolvedBit {
    record Program(List<ResolvedBit.Declaration> declarations, ResolverEnvironment environment, int variables) implements ResolvedBit {}

    sealed interface Declaration extends ResolvedBit {
        Symbol name();

        record Variable(Symbol name, Expression value, io.github.ageofwar.bit.types.Type type) implements Declaration {}
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

    record VariableAssignment(Symbol name, Expression value) implements ResolvedBit {}
    record VariableFieldAssignment(Expression struct, String name, Expression value) implements ResolvedBit {}

    sealed interface Expression extends ResolvedBit {
        Type type();
        Type returnType();

        record Identifier(Symbol name, Type type, Type returnType) implements Expression {}
        record Call(Expression callee, List<Expression> arguments, Type type, Type returnType) implements Expression {}
        record Block(List<ResolvedBit> statements, Type type, Type returnType) implements Expression {}
        record NumberLiteral(String value, Type type, Type returnType) implements Expression {}
        record StringLiteral(String value, Type type, Type returnType) implements Expression {}
        record BooleanLiteral(boolean value, Type type, Type returnType) implements Expression {}
        record Minus(Expression lhs, Expression rhs, Type type, Type returnType) implements Expression {}
        record Plus(Expression lhs, Expression rhs, Type type, Type returnType) implements Expression {}
        record Multiply(Expression lhs, Expression rhs, Type type, Type returnType) implements Expression {}
        record Divide(Expression lhs, Expression rhs, Type type, Type returnType) implements Expression {}
        record GreaterThan(Expression lhs, Expression rhs, Type type, Type returnType) implements Expression {}
        record GreaterThanOrEqual(Expression lhs, Expression rhs, Type type, Type returnType) implements Expression {}
        record LessThan(Expression lhs, Expression rhs, Type type, Type returnType) implements Expression {}
        record LessThanOrEqual(Expression lhs, Expression rhs, Type type, Type returnType) implements Expression {}
        record Equal(Expression lhs, Expression rhs, Type type, Type returnType) implements Expression {}
        record NotEqual(Expression lhs, Expression rhs, Type type, Type returnType) implements Expression {}
        record And(Expression lhs, Expression rhs, Type type, Type returnType) implements Expression {}
        record Or(Expression lhs, Expression rhs, Type type, Type returnType) implements Expression {}
        record Not(Expression expression, Type type, Type returnType) implements Expression {}
        record If(Expression condition, Expression thenBranch, Expression elseBranch, Type type, Type returnType) implements Expression {}
        record While(Expression condition, Expression body, Type type, Type returnType) implements Expression {}
        record As(Expression expression, Type type, Type returnType) implements Expression {}
        record Is(Expression expression, Type type, Type returnType) implements Expression {}
        record Access(Expression expression, String field, Type type, Type returnType) implements Expression {}
        record AccessExtension(Expression expression, Symbol name, Type type, Type returnType) implements Expression {}
        record Struct(Map<String, Expression> fields, Type type, Type returnType) implements Expression {}
        record Function(List<Parameter> parameters, Expression body, Type type, Type returnType) implements Expression {
            public record Parameter(Symbol name, Type type) {}
        }
        record Instantiation(Symbol className, List<Expression> arguments, Type type, Type returnType) implements Expression {}

        record Break(Type type, Type returnType) implements Expression {}
        record Continue(Type type, Type returnType) implements Expression {}
        record Return(Expression value, Type type, Type returnType) implements Expression {}
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
