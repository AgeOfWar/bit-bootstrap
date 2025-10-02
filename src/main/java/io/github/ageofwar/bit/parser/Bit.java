package io.github.ageofwar.bit.parser;

import java.util.List;
import java.util.Map;

public sealed interface Bit {
    record Program(List<Import> imports, List<Declaration> declarations) implements Bit {
        public record Import(String[] path, IdentifierSelector identifiers) {
            public sealed interface IdentifierSelector {
                boolean isIdentifier(String identifier);

                record All() implements IdentifierSelector {
                    @Override
                    public boolean isIdentifier(String identifier) {
                        return true;
                    }
                }
                record Only(List<String> identifiers) implements IdentifierSelector {
                    @Override
                    public boolean isIdentifier(String identifier) {
                        return identifiers.contains(identifier);
                    }
                }
            }
        }
    }

    sealed interface Declaration extends Bit {
        String name();

        record Variable(String name, Expression value, TypeExpression type) implements Declaration {}
        record Value(String name, Expression value, TypeExpression type) implements Declaration {}
        record Function(String name, List<GenericDeclaration> generics, List<Parameter> parameters, Expression body, TypeExpression returnType) implements Declaration {
            public record Parameter(String name, TypeExpression type) {}
        }
        record Type(String name, List<TypeParameter> parameters, TypeExpression value) implements Declaration {
            public record TypeParameter(String name, TypeExpression type) {}
        }
        record Class(String name, Constructor constructor, List<Member> members) implements Declaration {
            public record Member(Declaration declaration, Visibility visibility) {
                public enum Visibility {
                    PUBLIC, PRIVATE
                }
            }
            public record Constructor(List<Parameter> parameters) {
                public record Parameter(String name, TypeExpression type) {}
            }
        }
        record Implementation(String name, List<Function> extensions) implements Declaration {}
    }

    record VariableAssignment(Bit.Expression name, Expression value) implements Bit {}

    sealed interface Expression extends Bit {
        record Identifier(String name) implements Expression {}
        record Call(Expression callee, List<Expression> arguments, List<TypeExpression> generics) implements Expression {}
        record Block(List<Bit> statements) implements Expression {}
        record NumberLiteral(String value) implements Expression {}
        record StringLiteral(String value) implements Expression {}
        record BooleanLiteral(boolean value) implements Expression {}
        record Minus(Expression lhs, Expression rhs) implements Expression {}
        record Plus(Expression lhs, Expression rhs) implements Expression {}
        record Multiply(Expression lhs, Expression rhs) implements Expression {}
        record Divide(Expression lhs, Expression rhs) implements Expression {}
        record GreaterThan(Expression lhs, Expression rhs) implements Expression {}
        record GreaterThanOrEqual(Expression lhs, Expression rhs) implements Expression {}
        record LessThan(Expression lhs, Expression rhs) implements Expression {}
        record LessThanOrEqual(Expression lhs, Expression rhs) implements Expression {}
        record Equal(Expression lhs, Expression rhs) implements Expression {}
        record NotEqual(Expression lhs, Expression rhs) implements Expression {}
        record And(Expression lhs, Expression rhs) implements Expression {}
        record Or(Expression lhs, Expression rhs) implements Expression {}
        record Not(Expression expression) implements Expression {}
        record If(Expression condition, Expression thenBranch, Expression elseBranch) implements Expression {}
        record While(Expression condition, Expression body) implements Expression {}
        record As(Expression expression, TypeExpression type) implements Expression {}
        record Is(Expression expression, TypeExpression type) implements Expression {}
        record Access(Expression expression, String field) implements Expression {}
        record Struct(Map<String, Expression> fields) implements Expression {}
        record Function(List<GenericDeclaration> generics, List<Function.Parameter> parameters, Expression body, TypeExpression returnType) implements Expression {
            public record Parameter(String name, TypeExpression type) {}
        }
        record Instantiation(String className, List<Expression> arguments) implements Expression {}

        record Break() implements Expression {}
        record Continue() implements Expression {}
        record Return(Expression value) implements Expression {}
    }

    record GenericDeclaration(String name, TypeExpression extendsType) {}

    sealed interface TypeExpression extends Bit {
        record Identifier(String name) implements TypeExpression {}
        record NumberLiteral(String value) implements TypeExpression {}
        record StringLiteral(String value) implements TypeExpression {}
        record BooleanLiteral(boolean value) implements TypeExpression {}
        record Struct(Map<String, TypeExpression> fields) implements TypeExpression {}
        record Minus(TypeExpression lhs, TypeExpression rhs) implements TypeExpression {}
        record Plus(TypeExpression lhs, TypeExpression rhs) implements TypeExpression {}
        record Multiply(TypeExpression lhs, TypeExpression rhs) implements TypeExpression {}
        record Divide(TypeExpression lhs, TypeExpression rhs) implements TypeExpression {}
        record Union(TypeExpression lhs, TypeExpression rhs) implements TypeExpression {}
        record Intersection(TypeExpression lhs, TypeExpression rhs) implements TypeExpression {}
        record Call(Identifier callee, List<TypeExpression> arguments) implements TypeExpression {}
        record Function(List<TypeExpression> parameters, TypeExpression returnType) implements TypeExpression {}
        record Match(TypeExpression expression, List<MatchCase> cases) implements TypeExpression {
            public record MatchCase(Pattern pattern, TypeExpression value) {}
            public sealed interface Pattern {
                record Expression(TypeExpression expression) implements Pattern {}
                record Else() implements Pattern {}
            }
        }
    }
}
