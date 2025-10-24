package io.github.ageofwar.bit.interpreter;

import io.github.ageofwar.bit.resolver.ResolvedBit;
import io.github.ageofwar.bit.types.Type;

import java.math.BigInteger;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.github.ageofwar.bit.types.Types.*;

public class Interpreter {
    private Object eos;

    @SuppressWarnings("unchecked")
    public void interpret(ResolvedBit.Program program, String mainFunctionName) {
        var environment = Environment.init(program.variables());
        interpret(program, environment);

        ResolvedBit.Symbol mainSymbol = null;
        for (var declaration : program.declarations()) {
            if (declaration.name() != null && declaration.name().name().equals(mainFunctionName)) {
                mainSymbol = declaration.name();
                break;
            }
        }
        if (mainSymbol == null) {
            throw new RuntimeException("Main function not found: " + mainFunctionName);
        }
        var main = (Function<List<Object>, Object>) environment.get(mainSymbol);
        main.apply(List.of());
    }

    public void interpret(ResolvedBit.Program program, Environment environment) {
        for (var declaration : program.declarations()) {
            interpret(declaration, environment);
        }
    }

    private void interpret(ResolvedBit.Declaration declaration, Environment environment) {
        switch (declaration) {
            case ResolvedBit.Declaration.Variable variable -> interpret(variable, environment);
            case ResolvedBit.Declaration.Function function -> interpret(function, environment);
            case ResolvedBit.Declaration.Value Object -> interpret(Object, environment);
            case ResolvedBit.Declaration.Type type -> interpret(type, environment);
            case ResolvedBit.Declaration.Class classDeclaration -> interpret(classDeclaration, environment);
            case ResolvedBit.Declaration.Implementation implementation -> interpret(implementation, environment);
        }
    }

    private void interpret(ResolvedBit.Declaration.Function function, Environment environment) {
        environment.assignVariable(function.name(), (Function<List<Object>, Object>) args -> {
            for (var i = 0; i < function.generics().size(); i++) {
                environment.assignVariable(function.generics().get(i).name(), args.get(i));
            }
            var offset = function.generics().size();
            for (var i = 0; i < function.parameters().size(); i++) {
                environment.assignVariable(function.parameters().get(i).name(), args.get(offset + i));
            }
            var result = eval(function.body(), environment);
            if (result instanceof Return(var value)) {
                return value;
            }
            return result;
        });
    }

    private void interpret(ResolvedBit.Declaration.Variable variable, Environment environment) {
        environment.assignVariable(variable.name(), eval(variable.value(), environment));
    }

    private void interpret(ResolvedBit.Declaration.VariableAssignment assignment, Environment environment) {
        environment.assignVariable(assignment.name(), eval(assignment.value(), environment));
    }

    private void interpret(ResolvedBit.Declaration.VariableFieldAssignment assignment, Environment environment) {
        ((Struct) eval(assignment.struct(), environment)).setField(assignment.name(), eval(assignment.value(), environment));
    }

    private void interpret(ResolvedBit.Declaration.Value value, Environment environment) {
        environment.assignVariable(value.name(), eval(value.value(), environment));
    }

    private void interpret(ResolvedBit.Declaration.Type type, Environment environment) {
        if (type.valueName() != null) {
            environment.assignVariable(type.valueName(), type.value());
            if (type.value() instanceof Type.Nominal(var name) && name.equals("EndOfSequence")) {
                eos = type.value();
            }
        }
    }

    public void interpret(ResolvedBit.Declaration.Class classDeclaration, Environment environment) {
        var constructor = (Function<List<Object>, Object>) args -> {
            for (var i = 0; i < classDeclaration.constructor().parameters().size(); i++) {
                environment.assignVariable(classDeclaration.constructor().parameters().get(i).name(), args.get(i));
            }
            var fields = new java.util.HashMap<String, Object>();
            for (var declaration : classDeclaration.members()) {
                switch (declaration.declaration()) {
                    case ResolvedBit.Declaration.Variable v -> fields.put(v.name().name(), eval(v.value(), environment));
                    case ResolvedBit.Declaration.Value v -> fields.put(v.name().name(), eval(v.value(), environment));
                    case ResolvedBit.Declaration.Function f -> fields.put(f.name().name(), (Function<List<Object>, Object>) a -> {
                        environment.assignVariable(classDeclaration.thisSymbol(), new Struct(fields));
                        for (var i = 0; i < f.parameters().size(); i++) {
                            environment.assignVariable(f.parameters().get(i).name(), a.get(i));
                        }
                        var result = eval(f.body(), environment);
                        if (result instanceof Return(var value)) {
                            return value;
                        }
                        return result;
                    });
                    case ResolvedBit.Declaration.Type t -> interpret(t, environment);
                    case ResolvedBit.Declaration.Class c -> interpret(c, environment);
                    case ResolvedBit.Declaration.Implementation impl -> interpret(impl, environment);
                }
            }
            return new Struct(fields);
        };

        environment.assignVariable(classDeclaration.name(), constructor);
    }

    private void interpret(ResolvedBit.Declaration.Implementation implementation, Environment environment) {
        for (var function : implementation.extensions()) {
            environment.assignVariable(function.name(), (Function<List<Object>, Object>) args -> {
                environment.assignVariable(function.thisSymbol(), args.getFirst());
                for (var i = 0; i < implementation.generics().size(); i++) {
                    environment.assignVariable(implementation.generics().get(i).name(), args.get(i + 1));
                }
                var offset = implementation.generics().size() + 1;
                for (var i = 0; i < function.generics().size(); i++) {
                    environment.assignVariable(function.generics().get(i).name(), args.get(offset + i));
                }
                offset += function.generics().size();
                for (var i = 0; i < function.parameters().size(); i++) {
                    environment.assignVariable(function.parameters().get(i).name(), args.get(offset + i));
                }
                var result = eval(function.body(), environment);
                if (result instanceof Return(var value)) {
                    return value;
                }
                return result;
            });
        }
    }

    // evaluator methods

    private Object eval(ResolvedBit.Expression expression, Environment environment) {
        return switch (expression) {
            case ResolvedBit.Expression.Identifier identifier -> eval(identifier, environment);
            case ResolvedBit.Expression.Call call -> eval(call, environment);
            case ResolvedBit.Expression.Block block -> eval(block, environment);
            case ResolvedBit.Expression.NumberLiteral numberLiteral -> eval(numberLiteral);
            case ResolvedBit.Expression.StringLiteral stringLiteral -> eval(stringLiteral);
            case ResolvedBit.Expression.BooleanLiteral booleanLiteral -> eval(booleanLiteral);
            case ResolvedBit.Expression.Minus minus -> eval(minus, environment);
            case ResolvedBit.Expression.Plus plus -> eval(plus, environment);
            case ResolvedBit.Expression.Multiply multiply -> eval(multiply, environment);
            case ResolvedBit.Expression.Divide divide -> eval(divide, environment);
            case ResolvedBit.Expression.If ifExpression -> eval(ifExpression, environment);
            case ResolvedBit.Expression.While whileExpression -> eval(whileExpression, environment);
            case ResolvedBit.Expression.GreaterThan greaterThan -> eval(greaterThan, environment);
            case ResolvedBit.Expression.GreaterThanOrEqual greaterThanOrEqual -> eval(greaterThanOrEqual, environment);
            case ResolvedBit.Expression.LessThan lessThan -> eval(lessThan, environment);
            case ResolvedBit.Expression.LessThanOrEqual lessThanOrEqual -> eval(lessThanOrEqual, environment);
            case ResolvedBit.Expression.Equal equal -> eval(equal, environment);
            case ResolvedBit.Expression.NotEqual notEqual -> eval(notEqual, environment);
            case ResolvedBit.Expression.As asExpression -> eval(asExpression, environment);
            case ResolvedBit.Expression.Is isExpression -> eval(isExpression, environment);
            case ResolvedBit.Expression.Struct struct -> eval(struct, environment);
            case ResolvedBit.Expression.Array array -> eval(array, environment);
            case ResolvedBit.Expression.Access access -> eval(access, environment);
            case ResolvedBit.Expression.And and -> eval(and, environment);
            case ResolvedBit.Expression.Or or -> eval(or, environment);
            case ResolvedBit.Expression.Not not -> eval(not, environment);
            case ResolvedBit.Expression.Function function -> eval(function, environment);
            case ResolvedBit.Expression.Instantiation instantiation -> eval(instantiation, environment);
            case ResolvedBit.Expression.AccessExtension access -> eval(access, environment);
            case ResolvedBit.Expression.Break ignored -> Action.BREAK;
            case ResolvedBit.Expression.Continue ignored -> Action.CONTINUE;
            case ResolvedBit.Expression.Return returnExpression -> new Return(eval(returnExpression.value(), environment));
        };
    }

    private Object eval(ResolvedBit.Expression.Identifier identifier, Environment environment) {
        return environment.get(identifier.name());
    }

    @SuppressWarnings("unchecked")
    private Object eval(ResolvedBit.Expression.Call call, Environment environment) {
        var callee = (Function<List<Object>, Object>) eval(call.callee(), environment);
        return callee.apply(Stream.concat(call.generics().stream(), call.arguments().stream().map(a -> eval(a, environment))).toList());
    }

    private Object eval(ResolvedBit.Expression.Block block, Environment environment) {
        Object result = null;
        for (var statement : block.statements()) {
            switch (statement) {
                case ResolvedBit.Expression expression -> {
                    result = eval(expression, environment);
                    if (result == Action.BREAK || result == Action.CONTINUE || result instanceof Return) return result;
                }
                case ResolvedBit.Declaration declaration -> interpret(declaration, environment);
                case ResolvedBit.Declaration.VariableAssignment assignment -> interpret(assignment, environment);
                case ResolvedBit.Declaration.VariableFieldAssignment assignment -> interpret(assignment, environment);
                default -> throw new IllegalStateException("Unexpected statement " + statement);
            }
        }
        return result;
    }

    private Object eval(ResolvedBit.Expression.NumberLiteral numberLiteral) {
        try {
            return new BigInteger(numberLiteral.value());
        } catch (NumberFormatException e) {
            throw new RuntimeException("Invalid number literal: " + numberLiteral.value(), e);
        }
    }

    private Object eval(ResolvedBit.Expression.StringLiteral stringLiteral) {
        return stringLiteral.value();
    }

    private Object eval(ResolvedBit.Expression.BooleanLiteral booleanLiteral) {
        return booleanLiteral.value();
    }

    private Object eval(ResolvedBit.Expression.Minus minus, Environment environment) {
        var lhs = (BigInteger) eval(minus.lhs(), environment);
        var rhs = (BigInteger) eval(minus.rhs(), environment);
        return lhs.subtract(rhs);
    }

    private Object eval(ResolvedBit.Expression.Plus plus, Environment environment) {
        var lhs = (BigInteger) eval(plus.lhs(), environment);
        var rhs = (BigInteger) eval(plus.rhs(), environment);
        return lhs.add(rhs);
    }

    private Object eval(ResolvedBit.Expression.Multiply multiply, Environment environment) {
        var lhs = (BigInteger) eval(multiply.lhs(), environment);
        var rhs = (BigInteger) eval(multiply.rhs(), environment);
        return lhs.multiply(rhs);
    }

    private Object eval(ResolvedBit.Expression.Divide divide, Environment environment) {
        var lhs = (BigInteger) eval(divide.lhs(), environment);
        var rhs = (BigInteger) eval(divide.rhs(), environment);
        if (rhs.equals(BigInteger.ZERO)) {
            throw new ArithmeticException("Division by zero");
        }
        return lhs.divide(rhs);
    }

    private Object eval(ResolvedBit.Expression.If ifExpression, Environment environment) {
        var condition = (boolean) eval(ifExpression.condition(), environment);
        if (condition) {
            return eval(ifExpression.thenBranch(), environment);
        } else {
            return ifExpression.elseBranch() != null ? eval(ifExpression.elseBranch(), environment) : none();
        }
    }

    private Object eval(ResolvedBit.Expression.While whileExpression, Environment environment) {
        while ((boolean) eval(whileExpression.condition(), environment)) {
            var iterationResult = eval(whileExpression.body(), environment);
            if (iterationResult == Action.BREAK || iterationResult instanceof Return) break;
        }
        return none();
    }

    private Object eval(ResolvedBit.Expression.GreaterThan greaterThan, Environment environment) {
        var lhs = (BigInteger) eval(greaterThan.lhs(), environment);
        var rhs = (BigInteger) eval(greaterThan.rhs(), environment);
        return lhs.compareTo(rhs) > 0;
    }

    private Object eval(ResolvedBit.Expression.GreaterThanOrEqual greaterThanOrEqual, Environment environment) {
        var lhs = (BigInteger) eval(greaterThanOrEqual.lhs(), environment);
        var rhs = (BigInteger) eval(greaterThanOrEqual.rhs(), environment);
        return lhs.compareTo(rhs) >= 0;
    }

    private Object eval(ResolvedBit.Expression.LessThan lessThan, Environment environment) {
        var lhs = (BigInteger) eval(lessThan.lhs(), environment);
        var rhs = (BigInteger) eval(lessThan.rhs(), environment);
        return lhs.compareTo(rhs) < 0;
    }

    private Object eval(ResolvedBit.Expression.LessThanOrEqual lessThanOrEqual, Environment environment) {
        var lhs = (BigInteger) eval(lessThanOrEqual.lhs(), environment);
        var rhs = (BigInteger) eval(lessThanOrEqual.rhs(), environment);
        return lhs.compareTo(rhs) <= 0;
    }

    private Object eval(ResolvedBit.Expression.Equal equal, Environment environment) {
        var lhs = eval(equal.lhs(), environment);
        var rhs = eval(equal.rhs(), environment);
        return Objects.equals(lhs, rhs);
    }

    private Object eval(ResolvedBit.Expression.NotEqual equal, Environment environment) {
        var lhs = eval(equal.lhs(), environment);
        var rhs = eval(equal.rhs(), environment);
        return !Objects.equals(lhs, rhs);
    }

    private Object eval(ResolvedBit.Expression.Struct struct, Environment environment) {
        var fields = struct.fields().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> eval(entry.getValue(), environment)));
        return new Struct(fields);
    }

    private Object eval(ResolvedBit.Expression.Array array, Environment environment) {
        var value = array.elements().stream()
                .map(element -> eval(element, environment))
                .toArray(Object[]::new);
        return new Struct(Map.of(
                "size", (Function<List<Object>, Object>) args -> BigInteger.valueOf(value.length),
                "get", (Function<List<Object>, Object>) args -> {
                    var index = (BigInteger) args.getFirst();
                    if (index.compareTo(BigInteger.ZERO) < 0 || index.compareTo(BigInteger.valueOf(value.length)) >= 0) {
                        return none();
                    }
                    return value[index.intValue()];
                },
                "set", (Function<List<Object>, Object>) args -> {
                    var index = (BigInteger) args.get(0);
                    if (index.compareTo(BigInteger.ZERO) < 0 || index.compareTo(BigInteger.valueOf(value.length)) >= 0) {
                        throw new IndexOutOfBoundsException("Index out of bounds: " + index);
                    }
                    var prev = value[index.intValue()];
                    value[index.intValue()] = args.get(1);
                    return prev;
                },
                "toString", (Function<List<Object>, Object>) args -> {
                    return "[" + Stream.of(value).map(Objects::toString).collect(Collectors.joining(", ")) + "]";
                },
                "sequence", (Function<List<Object>, Object>) args -> {
                    var iterator = Arrays.stream(value).iterator();
                    return new Struct(Map.of(
                            "next", (Function<List<Object>, Object>) a -> {
                                return iterator.hasNext() ? iterator.next() : eos;
                            }
                    ));
                }
        ));
    }

    private Object eval(ResolvedBit.Expression.As asExpression, Environment environment) {
        var value = eval(asExpression.expression(), environment);
        var type = asExpression.type();
        if (!isAssignable(value, type, environment)) {
            throw new RuntimeException("Cannot cast " + value + " to type " + type);
        }
        return value;
    }

    private Object eval(ResolvedBit.Expression.Is isExpression, Environment environment) {
        var value = eval(isExpression.expression(), environment);
        var type = isExpression.checkType();
        return isAssignable(value, type, environment);
    }

    private Object eval(ResolvedBit.Expression.Access access, Environment environment) {
        var struct = (Struct) eval(access.expression(), environment);
        var fieldValue = struct.getField(access.field());
        if (fieldValue == null) {
            throw new RuntimeException("Field '" + access.field() + "' not found in struct: " + struct);
        }
        return fieldValue;
    }

    private Object eval(ResolvedBit.Expression.AccessExtension access, Environment environment) {
        var value = eval(access.expression(), environment);
        @SuppressWarnings("unchecked") var function = (Function<List<Object>, Object>) environment.get(access.name());
        return (Function<List<Object>, Object>) args -> {
            var newArgs = new ArrayList<>();
            newArgs.add(value);
            newArgs.addAll(access.generics());
            newArgs.addAll(args);
            return function.apply(newArgs);
        };
    }

    private Object eval(ResolvedBit.Expression.And and, Environment environment) {
        var lhs = (boolean) eval(and.lhs(), environment);
        if (!lhs) return false;
        return eval(and.rhs(), environment);
    }

    private Object eval(ResolvedBit.Expression.Or or, Environment environment) {
        var lhs = (boolean) eval(or.lhs(), environment);
        if (lhs) return true;
        return eval(or.rhs(), environment);
    }

    private Object eval(ResolvedBit.Expression.Not not, Environment environment) {
        var value = (boolean) eval(not.expression(), environment);
        return !value;
    }

    private Object eval(ResolvedBit.Expression.Function function, Environment environment) {
        return (Function<List<Object>, Object>) args -> {
            for (var i = 0; i < function.generics().size(); i++) {
                environment.assignVariable(function.generics().get(i).name(), args.get(i));
            }
            var offset = function.generics().size();
            for (var i = 0; i < function.parameters().size(); i++) {
                environment.assignVariable(function.parameters().get(i).name(), args.get(offset + i));
            }
            var result = eval(function.body(), environment);
            if (result instanceof Return(var value)) {
                return value;
            }
            return result;
        };
    }

    private Object eval(ResolvedBit.Expression.Instantiation instantiation, Environment environment) {
        @SuppressWarnings("unchecked") var constructor = (Function<List<Object>, Object>) environment.get(instantiation.className());
        var args = instantiation.arguments().stream().map(a -> eval(a, environment)).toList();
        return constructor.apply(args);
    }

    // is assignable

    public static boolean isAssignable(Object value, Type type, Environment environment) {
        if (type == any()) return true;
        if (type == never()) return false;

        if (type instanceof Type.TypeVariable typeVariable) {
            return isAssignable(value, (Type) environment.get(typeVariable.name()), environment);
        }

        if (value instanceof BigInteger bigInteger) {
            return extend(integer(bigInteger), type);
        }

        if (value instanceof String string) {
            return extend(string(string), type);
        }

        if (value instanceof Boolean bool) {
            return extend(bool ? _true() : _false(), type);
        }

        if (value instanceof Struct(var fields)) {
            if (!(type instanceof Type.Struct(var typeFields))) return false;
            for (var entry : fields.entrySet()) {
                var fieldType = typeFields.get(entry.getKey());
                if (fieldType != null && !isAssignable(entry.getValue(), fieldType, environment)) {
                    return false;
                }
            }
            return true;
        }

        if (value instanceof Type.Nominal nominal) {
            return extend(nominal, type);
        }

        if (value instanceof Function) {
            return extend(type, function(any())) || extend(type, function(any(), none())) || extend(type, function(any(), none(), none())) || extend(type, function(any(), none(), none(), none())) || extend(type, function(any(), none(), none(), none(), none()));
        }

        return false;
    }

    private enum Action { BREAK, CONTINUE }
    private record Return(Object value) {}
}
