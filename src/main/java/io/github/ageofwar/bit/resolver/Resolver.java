package io.github.ageofwar.bit.resolver;

import io.github.ageofwar.bit.parser.Bit;
import io.github.ageofwar.bit.types.Type;
import io.github.ageofwar.bit.types.TypeFunction;
import io.github.ageofwar.bit.types.Types;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;

import static io.github.ageofwar.bit.types.Types.*;

public class Resolver {
    public ResolvedBit.Program resolve(Bit.Program program) {
        var environment = ResolverEnvironment.init();
        var declarations = program.declarations().stream()
                .map(declaration -> resolve(declaration, environment))
                .toList();
        return new ResolvedBit.Program(declarations, environment);
    }

    private ResolvedBit.Declaration resolve(Bit.Declaration declaration, ResolverEnvironment environment) {
        return switch (declaration) {
            case Bit.Declaration.Variable variable -> resolve(variable, environment);
            case Bit.Declaration.VariableAssignment assignment -> resolve(assignment, environment);
            case Bit.Declaration.Value value -> resolve(value, environment);
            case Bit.Declaration.Function function -> resolve(function, environment);
            case Bit.Declaration.Type type -> resolve(type, environment);
            case Bit.Declaration.Class cls -> resolve(cls, environment);
            case Bit.Declaration.Implementation impl -> resolve(impl, environment);
        };
    }

    private ResolvedBit.Declaration.Variable resolve(Bit.Declaration.Variable declaration, ResolverEnvironment environment) {
        var resolvedExpression = resolve(declaration.value(), environment);
        var declaredType = resolve(declaration.type(), environment);
        if (!extend(resolvedExpression.type(), declaredType)) {
            throw new ResolverException("Type mismatch: expected " + declaredType + " but got " + resolvedExpression.type());
        }
        var symbol = environment.declareVariableType(declaration.name(), declaredType);
        return new ResolvedBit.Declaration.Variable(symbol, resolvedExpression, declaredType);
    }

    private ResolvedBit.Declaration.VariableAssignment resolve(Bit.Declaration.VariableAssignment assignment, ResolverEnvironment environment) {
        var entry = environment.getVariableType(assignment.name());
        if (entry == null) {
            throw new ResolverException("Variable '" + assignment.name() + "' is not defined in the current scope.");
        }
        var resolvedExpression = resolve(assignment.value(), environment);
        if (!extend(resolvedExpression.type(), entry.type())) {
            throw new ResolverException("Type mismatch: expected " + entry.type() + " but got " + resolvedExpression.type());
        }
        return new ResolvedBit.Declaration.VariableAssignment(entry.symbol(), resolvedExpression);
    }

    private ResolvedBit.Declaration.Value resolve(Bit.Declaration.Value declaration, ResolverEnvironment environment) {
        var resolvedExpression = resolve(declaration.value(), environment);
        if (declaration.type() == null) {
            var symbol = environment.declareValueType(declaration.name(), resolvedExpression.type());
            return new ResolvedBit.Declaration.Value(symbol, resolvedExpression, resolvedExpression.type());
        } else {
            var declaredType = resolve(declaration.type(), environment);
            if (!extend(resolvedExpression.type(), declaredType)) {
                throw new ResolverException("Type mismatch: expected " + declaredType + " but got " + resolvedExpression.type());
            }
            var symbol = environment.declareValueType(declaration.name(), declaredType);
            return new ResolvedBit.Declaration.Value(symbol, resolvedExpression, declaredType);
        }
    }

    private ResolvedBit.Declaration.Function resolve(Bit.Declaration.Function function, ResolverEnvironment environment) {
        var bodyEnvironment = new ResolverEnvironment(environment);
        var parameters = new ArrayList<ResolvedBit.Declaration.Function.Parameter>();
        for (int i = 0; i < function.parameters().size(); i++) {
            var param = function.parameters().get(i);
            var paramSymbol = bodyEnvironment.declareValueType(param.name(), resolve(param.type(), environment));
            var paramType = resolve(param.type(), environment);
            parameters.add(new ResolvedBit.Declaration.Function.Parameter(paramSymbol, paramType));
        }

        var returnType = function.returnType() == null ? none() : resolve(function.returnType(), environment);
        var functionType = function(returnType, parameters.stream().map(ResolvedBit.Declaration.Function.Parameter::type).toArray(Type[]::new));
        var symbol = environment.declareValueType(function.name(), functionType);

        var body = resolve(function.body(), bodyEnvironment);
        return new ResolvedBit.Declaration.Function(symbol, parameters, body, functionType);
    }

    private ResolvedBit.Declaration.Class resolve(Bit.Declaration.Class classDeclaration, ResolverEnvironment environment) {
        var membersEnvironment = new ResolverEnvironment(environment);
        var constructorParameters = new ArrayList<ResolvedBit.Declaration.Class.Constructor.Parameter>();
        for (var param : classDeclaration.constructor().parameters()) {
            var symbol = membersEnvironment.declareValueType(param.name(), resolve(param.type(), environment));
            constructorParameters.add(new ResolvedBit.Declaration.Class.Constructor.Parameter(symbol, resolve(param.type(), environment)));
        }

        var constructor = new ResolvedBit.Declaration.Class.Constructor(constructorParameters);

        var bodyEnvironment = new ResolverEnvironment(membersEnvironment);
        var resolvedMembers = new ArrayList<ResolvedBit.Declaration.Class.Member>();
        var thisType = new HashMap<String, Type>();
        var publicType = new HashMap<String, Type>();
        var thisSymbol = bodyEnvironment.declareValueType("this", struct(thisType));
        for (var member : classDeclaration.members()) {
            var resolvedDeclaration = resolve(member.declaration(), bodyEnvironment);
            var visibility = member.visibility() == Bit.Declaration.Class.Member.Visibility.PUBLIC ? ResolvedBit.Declaration.Class.Member.Visibility.PUBLIC : ResolvedBit.Declaration.Class.Member.Visibility.PRIVATE;
            resolvedMembers.add(new ResolvedBit.Declaration.Class.Member(resolvedDeclaration, visibility));
            if (resolvedDeclaration instanceof ResolvedBit.Declaration.Class || resolvedDeclaration instanceof ResolvedBit.Declaration.Type) {
                continue;
            }
            var memberType = switch (resolvedDeclaration) {
                case ResolvedBit.Declaration.Variable variable -> variable.type();
                case ResolvedBit.Declaration.Value value -> value.type();
                case ResolvedBit.Declaration.Function function -> function.type();
                default -> throw new AssertionError(resolvedDeclaration);
            };
            thisType.put(member.declaration().name(), memberType);
            if (visibility == ResolvedBit.Declaration.Class.Member.Visibility.PUBLIC) {
                publicType.put(member.declaration().name(), memberType);
            }
        }

        var returnType = struct(publicType);
        var parameterTypes = constructorParameters.stream()
                .map(ResolvedBit.Declaration.Class.Constructor.Parameter::type)
                .toArray(Type[]::new);

        var valueSymbol = environment.declareType(classDeclaration.name(), returnType);
        var symbol = environment.declareConstructor(classDeclaration.name(), function(returnType, parameterTypes));
        return new ResolvedBit.Declaration.Class(symbol, valueSymbol, thisSymbol, constructor, resolvedMembers, returnType);
    }

    private ResolvedBit.Declaration.Implementation resolve(Bit.Declaration.Implementation implementation, ResolverEnvironment environment) {
        var entry = environment.getType(implementation.name());
        if (entry == null) {
            throw new ResolverException("Type '" + implementation.name() + "' is not defined in the current scope.");
        }
        var receiver = entry.type();

        var extensions = new ArrayList<ResolvedBit.Declaration.Implementation.Function>();
        for (var func : implementation.extensions()) {
            var bodyEnvironment = new ResolverEnvironment(environment);
            var parameters = new ArrayList<ResolvedBit.Declaration.Implementation.Function.Parameter>();

            var thisSymbol = bodyEnvironment.declareValueType("this", receiver);

            for (int i = 0; i < func.parameters().size(); i++) {
                var param = func.parameters().get(i);
                var paramSymbol = bodyEnvironment.declareValueType(param.name(), resolve(param.type(), environment));
                var paramType = resolve(param.type(), environment);
                parameters.add(new ResolvedBit.Declaration.Implementation.Function.Parameter(paramSymbol, paramType));
            }

            var returnType = func.returnType() == null ? none() : resolve(func.returnType(), environment);
            var functionType = function(returnType, parameters.stream().map(ResolvedBit.Declaration.Implementation.Function.Parameter::type).toArray(Type[]::new));
            var symbol = environment.declareExtensionType(func.name(), receiver, functionType);

            var body = resolve(func.body(), bodyEnvironment);
            if (!extend(body.type(), returnType)) {
                throw new ResolverException("Type mismatch: expected " + returnType + " but got " + body.type());
            }
            extensions.add(new ResolvedBit.Declaration.Implementation.Function(symbol, thisSymbol, parameters, body, functionType));
        }

        return new ResolvedBit.Declaration.Implementation(entry.symbol(), receiver, extensions);
    }

    private ResolvedBit.Declaration.Type resolve(Bit.Declaration.Type declaration, ResolverEnvironment environment) {
        var typeParameters = declaration.parameters().stream()
                .map(param -> param.type() == null ? null : new ResolvedBit.Declaration.Type.TypeParameter(resolve(param.type(), environment)))
                .toList();
        if (typeParameters.isEmpty()) {
            if (declaration.value() == null) {
                var type = nominal(declaration.name());
                var symbol = environment.declareType(declaration.name(), type);
                var valueSymbol = environment.declareValueType(declaration.name(), type);
                return new ResolvedBit.Declaration.Type(symbol, valueSymbol, List.of(), type);
            } else {
                var resolvedType = resolve(declaration.value(), environment);
                var symbol = environment.declareType(declaration.name(), resolvedType);
                var valueSymbol = environment.declareValueType(declaration.name(), resolvedType);
                return new ResolvedBit.Declaration.Type(symbol, valueSymbol, List.of(), resolvedType);
            }
        } else {
            if (declaration.value() == null) {
                var typeFunction = new TypeFunction((args) -> nominal(declaration.name()));
                var symbol = environment.declareFunctionType(declaration.name(), typeFunction);
                return new ResolvedBit.Declaration.Type(symbol, null, typeParameters, nominal(declaration.name()));
            } else {
                var typeFunction = new TypeFunction((args) -> {
                    var newEnvironment = new ResolverEnvironment(environment);
                    for (int i = 0; i < typeParameters.size(); i++) {
                        var typeParameter = typeParameters.get(i);
                        if (typeParameter == null) {
                            continue;
                        }
                        newEnvironment.declareType(declaration.parameters().get(i).name(), args[i]);
                    }
                    return resolve(declaration.value(), newEnvironment);
                });
                var symbol = environment.declareFunctionType(declaration.name(), typeFunction);
                return new ResolvedBit.Declaration.Type(symbol, null, typeParameters, null);
            }
        }
    }

    // expressions

    private ResolvedBit.Expression resolve(Bit.Expression expression, ResolverEnvironment environment) {
        return switch (expression) {
            case Bit.Expression.Identifier identifier -> resolve(identifier, environment);
            case Bit.Expression.Call call -> resolve(call, environment);
            case Bit.Expression.Block block -> resolve(block, environment);
            case Bit.Expression.NumberLiteral numberLiteral -> resolve(numberLiteral, environment);
            case Bit.Expression.StringLiteral stringLiteral -> resolve(stringLiteral, environment);
            case Bit.Expression.BooleanLiteral booleanLiteral -> resolve(booleanLiteral, environment);
            case Bit.Expression.Minus minus -> resolve(minus, environment);
            case Bit.Expression.Plus plus -> resolve(plus, environment);
            case Bit.Expression.Multiply multiply -> resolve(multiply, environment);
            case Bit.Expression.Divide divide -> resolve(divide, environment);
            case Bit.Expression.GreaterThan greaterThan -> resolve(greaterThan, environment);
            case Bit.Expression.GreaterThanOrEqual greaterThanOrEqual -> resolve(greaterThanOrEqual, environment);
            case Bit.Expression.LessThan lessThan -> resolve(lessThan, environment);
            case Bit.Expression.LessThanOrEqual lessThanOrEqual -> resolve(lessThanOrEqual, environment);
            case Bit.Expression.Equal equal -> resolve(equal, environment);
            case Bit.Expression.NotEqual notEqual -> resolve(notEqual, environment);
            case Bit.Expression.And and -> resolve(and, environment);
            case Bit.Expression.Or or -> resolve(or, environment);
            case Bit.Expression.Not not -> resolve(not, environment);
            case Bit.Expression.If ifExpr -> resolve(ifExpr, environment);
            case Bit.Expression.As as -> resolve(as, environment);
            case Bit.Expression.Is is -> resolve(is, environment);
            case Bit.Expression.Access access -> resolve(access, environment);
            case Bit.Expression.Struct struct -> resolve(struct, environment);
            case Bit.Expression.Function function -> resolve(function, environment);
            case Bit.Expression.Instantiation instantiation -> resolve(instantiation, environment);
        };
    }

    private ResolvedBit.Expression resolve(Bit.Expression.Identifier identifier, ResolverEnvironment environment) {
        var entry = environment.getValueType(identifier.name());
        return new ResolvedBit.Expression.Identifier(entry.symbol(), entry.type());
    }

    private ResolvedBit.Expression resolve(Bit.Expression.Call call, ResolverEnvironment environment) {
        var callee = resolve(call.callee(), environment);
        var argumentTypes = call.arguments().stream()
                .map(arg -> resolve(arg, environment))
                .toList();
        if (!(callee.type() instanceof Type.Function(Type returnType, Type[] parameters))) {
            throw new ResolverException("Type '" + callee.type() + "' is not a function type.");
        }
        if (parameters.length != argumentTypes.size()) {
            throw new ResolverException("Function expected " + parameters.length + " arguments but got " + argumentTypes.size() + ".");
        }
        for (int i = 0; i < parameters.length; i++) {
            var expectedType = parameters[i];
            var actualType = argumentTypes.get(i).type();
            if (!extend(actualType, expectedType)) {
                throw new ResolverException("Argument " + (i + 1) + " type mismatch: expected " + expectedType + " but got " + actualType);
            }
        }
        return new ResolvedBit.Expression.Call(callee, argumentTypes, returnType);
    }

    private ResolvedBit.Expression resolve(Bit.Expression.Block block, ResolverEnvironment environment) {
        var blockEnvironment = new ResolverEnvironment(environment);
        var statements = block.statements().stream()
                .map(statement -> {
                    if (statement instanceof Bit.Declaration declaration) {
                        return resolve(declaration, blockEnvironment);
                    } else if (statement instanceof Bit.Expression expression) {
                        return resolve(expression, blockEnvironment);
                    } else {
                        throw new AssertionError(statement);
                    }
                })
                .toList();
        var type = statements.isEmpty() ? none() : ((statements.getLast() instanceof ResolvedBit.Expression expr) ? expr.type() : none());
        return new ResolvedBit.Expression.Block(statements, type);
    }

    private ResolvedBit.Expression resolve(Bit.Expression.NumberLiteral numberLiteral, ResolverEnvironment environment) {
        return new ResolvedBit.Expression.NumberLiteral(numberLiteral.value(), integer(numberLiteral.value()));
    }

    private ResolvedBit.Expression resolve(Bit.Expression.StringLiteral stringLiteral, ResolverEnvironment environment) {
        return new ResolvedBit.Expression.StringLiteral(stringLiteral.value(), string(stringLiteral.value()));
    }

    private ResolvedBit.Expression resolve(Bit.Expression.BooleanLiteral booleanLiteral, ResolverEnvironment environment) {
        return new ResolvedBit.Expression.BooleanLiteral(booleanLiteral.value(), booleanLiteral.value() ? _true() : _false());
    }

    private ResolvedBit.Expression resolve(Bit.Expression.Minus minus, ResolverEnvironment environment) {
        var lhs = resolve(minus.lhs(), environment);
        var rhs = resolve(minus.rhs(), environment);
        if (!extend(lhs.type(), integer()) || !extend(rhs.type(), integer())) {
            throw new ResolverException("Type mismatch: expected integer but got " + lhs.type() + " and " + rhs.type());
        }
        return new ResolvedBit.Expression.Minus(lhs, rhs, subtract(lhs.type(), rhs.type()));
    }

    private ResolvedBit.Expression resolve(Bit.Expression.Plus plus, ResolverEnvironment environment) {
        var lhs = resolve(plus.lhs(), environment);
        var rhs = resolve(plus.rhs(), environment);
        if (!extend(lhs.type(), integer()) || !extend(rhs.type(), integer())) {
            throw new ResolverException("Type mismatch: expected integer or string but got " + lhs.type() + " and " + rhs.type());
        }
        return new ResolvedBit.Expression.Plus(lhs, rhs, add(lhs.type(), rhs.type()));
    }

    private ResolvedBit.Expression resolve(Bit.Expression.Multiply multiply, ResolverEnvironment environment) {
        var lhs = resolve(multiply.lhs(), environment);
        var rhs = resolve(multiply.rhs(), environment);
        if (!extend(lhs.type(), integer()) || !extend(rhs.type(), integer())) {
            throw new ResolverException("Type mismatch: expected integer but got " + lhs.type() + " and " + rhs.type());
        }
        return new ResolvedBit.Expression.Multiply(lhs, rhs, multiply(lhs.type(), rhs.type()));
    }

    private ResolvedBit.Expression resolve(Bit.Expression.Divide divide, ResolverEnvironment environment) {
        var lhs = resolve(divide.lhs(), environment);
        var rhs = resolve(divide.rhs(), environment);
        if (!extend(lhs.type(), integer()) || !extend(rhs.type(), integer())) {
            throw new ResolverException("Type mismatch: expected integer but got " + lhs.type() + " and " + rhs.type());
        }
        return new ResolvedBit.Expression.Divide(lhs, rhs, divide(lhs.type(), rhs.type()));
    }

    private ResolvedBit.Expression resolve(Bit.Expression.GreaterThan greaterThan, ResolverEnvironment environment) {
        var lhs = resolve(greaterThan.lhs(), environment);
        var rhs = resolve(greaterThan.rhs(), environment);
        if (!extend(lhs.type(), integer()) || !extend(rhs.type(), integer())) {
            throw new ResolverException("Type mismatch: expected integer but got " + lhs.type() + " and " + rhs.type());
        }
        return new ResolvedBit.Expression.GreaterThan(lhs, rhs, greaterThan(lhs.type(), rhs.type()));
    }

    private ResolvedBit.Expression resolve(Bit.Expression.GreaterThanOrEqual greaterThanOrEqual, ResolverEnvironment environment) {
        var lhs = resolve(greaterThanOrEqual.lhs(), environment);
        var rhs = resolve(greaterThanOrEqual.rhs(), environment);
        if (!extend(lhs.type(), integer()) || !extend(rhs.type(), integer())) {
            throw new ResolverException("Type mismatch: expected integer but got " + lhs.type() + " and " + rhs.type());
        }
        return new ResolvedBit.Expression.GreaterThanOrEqual(lhs, rhs, greaterThanOrEqual(lhs.type(), rhs.type()));
    }

    private ResolvedBit.Expression resolve(Bit.Expression.LessThan lessThan, ResolverEnvironment environment) {
        var lhs = resolve(lessThan.lhs(), environment);
        var rhs = resolve(lessThan.rhs(), environment);
        if (!extend(lhs.type(), integer()) || !extend(rhs.type(), integer())) {
            throw new ResolverException("Type mismatch: expected integer but got " + lhs.type() + " and " + rhs.type());
        }
        return new ResolvedBit.Expression.LessThan(lhs, rhs, lessThan(lhs.type(), rhs.type()));
    }

    private ResolvedBit.Expression resolve(Bit.Expression.LessThanOrEqual lessThanOrEqual, ResolverEnvironment environment) {
        var lhs = resolve(lessThanOrEqual.lhs(), environment);
        var rhs = resolve(lessThanOrEqual.rhs(), environment);
        if (!extend(lhs.type(), integer()) || !extend(rhs.type(), integer())) {
            throw new ResolverException("Type mismatch: expected integer but got " + lhs.type() + " and " + rhs.type());
        }
        return new ResolvedBit.Expression.LessThanOrEqual(lhs, rhs, lessThanOrEqual(lhs.type(), rhs.type()));
    }

    private ResolvedBit.Expression resolve(Bit.Expression.Equal equal, ResolverEnvironment environment) {
        var lhs = resolve(equal.lhs(), environment);
        var rhs = resolve(equal.rhs(), environment);
        return new ResolvedBit.Expression.Equal(lhs, rhs, equal(lhs.type(), rhs.type()));
    }

    private ResolvedBit.Expression resolve(Bit.Expression.NotEqual notEqual, ResolverEnvironment environment) {
        var lhs = resolve(notEqual.lhs(), environment);
        var rhs = resolve(notEqual.rhs(), environment);
        return new ResolvedBit.Expression.NotEqual(lhs, rhs, notEqual(lhs.type(), rhs.type()));
    }

    private ResolvedBit.Expression resolve(Bit.Expression.And and, ResolverEnvironment environment) {
        var lhs = resolve(and.lhs(), environment);
        var rhsEnvironment = new ResolverEnvironment(environment);
        doRefine(lhs, rhsEnvironment, null);
        var rhs = resolve(and.rhs(), environment);
        if (!extend(lhs.type(), _boolean()) || !extend(rhs.type(), _boolean())) {
            throw new ResolverException("Type mismatch: expected boolean but got " + lhs.type() + " and " + rhs.type());
        }
        return new ResolvedBit.Expression.And(lhs, rhs, and(lhs.type(), rhs.type()));
    }

    private ResolvedBit.Expression resolve(Bit.Expression.Or or, ResolverEnvironment environment) {
        var lhs = resolve(or.lhs(), environment);
        var rhsEnvironment = new ResolverEnvironment(environment);
        doRefine(lhs, null, rhsEnvironment);
        var rhs = resolve(or.rhs(), environment);
        if (!extend(lhs.type(), _boolean()) || !extend(rhs.type(), _boolean())) {
            throw new ResolverException("Type mismatch: expected boolean but got " + lhs.type() + " and " + rhs.type());
        }
        return new ResolvedBit.Expression.Or(lhs, rhs, or(lhs.type(), rhs.type()));
    }

    private ResolvedBit.Expression resolve(Bit.Expression.Not not, ResolverEnvironment environment) {
        var expr = resolve(not.expression(), environment);
        if (!extend(expr.type(), _boolean())) {
            throw new ResolverException("Type mismatch: expected boolean but got " + expr.type());
        }
        return new ResolvedBit.Expression.Not(expr, not(expr.type()));
    }

    private ResolvedBit.Expression resolve(Bit.Expression.If ifExpr, ResolverEnvironment environment) {
        var condition = resolve(ifExpr.condition(), environment);
        if (!extend(condition.type(), _boolean())) {
            throw new ResolverException("Type mismatch: expected boolean but got " + condition.type());
        }

        var thenEnvironment = new ResolverEnvironment(environment);
        var elseEnvironment = new ResolverEnvironment(environment);
        doRefine(condition, thenEnvironment, elseEnvironment);

        var thenBranch = resolve(ifExpr.thenBranch(), thenEnvironment);
        var elseBranch = ifExpr.elseBranch() != null ? resolve(ifExpr.elseBranch(), elseEnvironment) : null;

        var type = elseBranch == null ? union(thenBranch.type(), none()) : union(thenBranch.type(), elseBranch.type());
        return new ResolvedBit.Expression.If(condition, thenBranch, elseBranch, type);
    }

    private ResolvedBit.Expression resolve(Bit.Expression.As as, ResolverEnvironment environment) {
        var expr = resolve(as.expression(), environment);
        var type = resolve(as.type(), environment);
        if (!extend(type, expr.type()) && !extend(expr.type(), type)) {
            throw new ResolverException("Type mismatch: cannot cast " + expr.type() + " to " + type);
        }
        return new ResolvedBit.Expression.As(expr, type);
    }

    private ResolvedBit.Expression resolve(Bit.Expression.Is is, ResolverEnvironment environment) {
        var expr = resolve(is.expression(), environment);
        var type = resolve(is.type(), environment);
        if (extend(expr.type(), type)) {
            new ResolvedBit.Expression.Is(expr, _true());
        }
        if (!extend(type, expr.type())) {
            new ResolvedBit.Expression.Is(expr, _false());
        }
        return new ResolvedBit.Expression.Is(expr, type);
    }

    private ResolvedBit.Expression resolve(Bit.Expression.Access access, ResolverEnvironment environment) {
        var expr = resolve(access.expression(), environment);
        if (!(expr.type() instanceof Type.Struct(var fields))) {
            return resolveExtensions(access, environment);
        }

        var fieldType = fields.get(access.field());
        if (fieldType == null) {
            return resolveExtensions(access, environment);
        }
        return new ResolvedBit.Expression.Access(expr, access.field(), fieldType);
    }

    private ResolvedBit.Expression resolveExtensions(Bit.Expression.Access access, ResolverEnvironment environment) {
        var expr = resolve(access.expression(), environment);
        var extensions = environment.getExtensionTypes(access.field());
        if (extensions.isEmpty()) {
            throw new ResolverException("Type '" + expr.type() + "' does not have method '" + access.field() + "'.");
        }
        var functionTypes = extensions.stream()
                .filter(funcType -> extend(expr.type(), funcType.receiverType()))
                .toList();
        if (functionTypes.isEmpty()) {
            throw new ResolverException("Type '" + expr.type() + "' does not have method '" + access.field() + "'.");
        }
        if (functionTypes.size() > 1) {
            throw new ResolverException("Ambiguous method call: type '" + expr.type() + "' has multiple methods named '" + access.field() + "'.");
        }
        var functionType = functionTypes.getFirst();
        return new ResolvedBit.Expression.AccessExtension(expr, functionType.symbol(), functionType.type());
    }

    private ResolvedBit.Expression resolve(Bit.Expression.Struct struct, ResolverEnvironment environment) {
        var fieldTypes = new HashMap<String, Type>();
        var resolvedFields = new HashMap<String, ResolvedBit.Expression>();
        for (var entry : struct.fields().entrySet()) {
            var fieldName = entry.getKey();
            var fieldExpr = resolve(entry.getValue(), environment);
            fieldTypes.put(fieldName, fieldExpr.type());
            resolvedFields.put(fieldName, fieldExpr);
        }
        return new ResolvedBit.Expression.Struct(resolvedFields, struct(fieldTypes));
    }

    private ResolvedBit.Expression resolve(Bit.Expression.Function function, ResolverEnvironment environment) {
        var bodyEnvironment = new ResolverEnvironment(environment);
        var parameters = new ArrayList<ResolvedBit.Expression.Function.Parameter>();
        for (int i = 0; i < function.parameters().size(); i++) {
            var param = function.parameters().get(i);
            var paramSymbol = bodyEnvironment.declareValueType(param.name(), resolve(param.type(), environment));
            var paramType = resolve(param.type(), environment);
            parameters.add(new ResolvedBit.Expression.Function.Parameter(paramSymbol, paramType));
        }

        var returnType = function.returnType() == null ? none() : resolve(function.returnType(), environment);
        var functionType = function(returnType, parameters.stream().map(ResolvedBit.Expression.Function.Parameter::type).toArray(Type[]::new));

        var body = resolve(function.body(), bodyEnvironment);
        if (!extend(body.type(), returnType)) {
            throw new ResolverException("Type mismatch: expected " + returnType + " but got " + body.type());
        }
        return new ResolvedBit.Expression.Function(parameters, body, functionType);
    }

    private ResolvedBit.Expression resolve(Bit.Expression.Instantiation instantiation, ResolverEnvironment environment) {
        var entry = environment.getConstructor(instantiation.className());
        if (entry == null) {
            throw new ResolverException("Constructor '" + instantiation.className() + "' is not defined in the current scope.");
        }
        if (!(entry.type() instanceof Type.Function(Type returnType, Type[] parameters))) {
            throw new ResolverException("Type '" + entry.type() + "' is not a function type.");
        }
        var argumentTypes = instantiation.arguments().stream()
                .map(arg -> resolve(arg, environment))
                .toList();
        if (parameters.length != argumentTypes.size()) {
            throw new ResolverException("Constructor expected " + parameters.length + " arguments but got " + argumentTypes.size() + ".");
        }
        for (int i = 0; i < parameters.length; i++) {
            var expectedType = parameters[i];
            var actualType = argumentTypes.get(i).type();
            if (!extend(actualType, expectedType)) {
                throw new ResolverException("Argument " + (i + 1) + " type mismatch: expected " + expectedType + " but got " + actualType);
            }
        }
        return new ResolvedBit.Expression.Instantiation(entry.symbol(), argumentTypes, returnType);
    }

    // type expressions

    public Type resolve(Bit.TypeExpression expression, ResolverEnvironment environment) {
        return switch (expression) {
            case Bit.TypeExpression.Identifier identifier -> resolve(identifier, environment);
            case Bit.TypeExpression.Union union -> resolve(union, environment);
            case Bit.TypeExpression.Intersection intersection -> resolve(intersection, environment);
            case Bit.TypeExpression.NumberLiteral numberLiteral -> integer(numberLiteral.value());
            case Bit.TypeExpression.StringLiteral stringLiteral -> string(stringLiteral.value());
            case Bit.TypeExpression.BooleanLiteral booleanLiteral -> booleanLiteral.value() ? _true() : _false();
            case Bit.TypeExpression.Struct struct -> resolve(struct, environment);
            case Bit.TypeExpression.Minus minus ->
                    subtract(resolve(minus.lhs(), environment), resolve(minus.rhs(), environment));
            case Bit.TypeExpression.Plus plus ->
                    add(resolve(plus.lhs(), environment), resolve(plus.rhs(), environment));
            case Bit.TypeExpression.Multiply multiply ->
                    multiply(resolve(multiply.lhs(), environment), resolve(multiply.rhs(), environment));
            case Bit.TypeExpression.Divide divide ->
                    divide(resolve(divide.lhs(), environment), resolve(divide.rhs(), environment));
            case Bit.TypeExpression.Call call -> resolve(call, environment);
            case Bit.TypeExpression.Match match -> resolve(match, environment);
            case Bit.TypeExpression.Function function -> resolve(function, environment);
        };
    }

    private Type resolve(Bit.TypeExpression.Identifier identifier, ResolverEnvironment environment) {
        var entry = environment.getType(identifier.name());
        if (entry == null) {
            throw new ResolverException("Type '" + identifier.name() + "' is not defined in the current scope.");
        }
        return entry.type();
    }

    private Type resolve(Bit.TypeExpression.Union union, ResolverEnvironment environment) {
        return union(resolve(union.lhs(), environment), resolve(union.rhs(), environment));
    }

    private Type resolve(Bit.TypeExpression.Struct struct, ResolverEnvironment environment) {
        var fields = struct.fields().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        java.util.Map.Entry::getKey,
                        entry -> resolve(entry.getValue(), environment)
                ));
        return struct(fields);
    }

    private Type resolve(Bit.TypeExpression.Intersection intersection, ResolverEnvironment environment) {
        return intersection(resolve(intersection.lhs(), environment), resolve(intersection.rhs(), environment));
    }

    private Type resolve(Bit.TypeExpression.Call call, ResolverEnvironment environment) {
        var functionType = resolveFunction(call.callee(), environment);
        var argumentTypes = call.arguments().stream()
                .map(arg -> resolve(arg, environment))
                .toArray(Type[]::new);
        return functionType.function().apply(argumentTypes);
    }

    private TypeFunction resolveFunction(Bit.TypeExpression.Identifier identifier, ResolverEnvironment environment) {
        var entry = environment.getFunctionType(identifier.name());
        if (entry == null) {
            throw new ResolverException("Function '" + identifier.name() + "' is not defined in the current scope or is not a function type.");
        }
        return entry.type();
    }

    private Type resolve(Bit.TypeExpression.Match match, ResolverEnvironment environment) {
        var outcomes = match.cases().stream()
                .map(caseItem -> resolve(caseItem.value(), environment))
                .toArray(Type[]::new);
        return union(outcomes);
    }

    private Type resolve(Bit.TypeExpression.Function function, ResolverEnvironment environment) {
        var parameterTypes = function.parameters().stream()
                .map(param -> resolve(param, environment))
                .toArray(Type[]::new);
        var returnType = resolve(function.returnType(), environment);
        return function(returnType, parameterTypes);
    }

    // refine

    private void doRefine(ResolvedBit.Expression condition, ResolverEnvironment thenEnvironment, ResolverEnvironment elseEnvironment) {
        refine(condition, thenEnvironment, elseEnvironment).forEach(refinement -> {
            if (refinement.environment() != null) {
                refinement.environment().refineValueType(refinement.symbol(), refinement.type());
            }
        });
    }

    private Stream<Refinement> refine(ResolvedBit.Expression condition, ResolverEnvironment thenEnvironment, ResolverEnvironment elseEnvironment) {
        return switch (condition) {
            case ResolvedBit.Expression.Equal cond -> refine(cond, thenEnvironment, elseEnvironment);
            case ResolvedBit.Expression.NotEqual cond -> refine(cond, thenEnvironment, elseEnvironment);
            case ResolvedBit.Expression.Not cond -> refine(cond, elseEnvironment, thenEnvironment);
            case ResolvedBit.Expression.Is cond -> refine(cond, thenEnvironment, elseEnvironment);
            case ResolvedBit.Expression.And cond -> refine(cond, thenEnvironment, elseEnvironment);
            case ResolvedBit.Expression.Or cond -> refine(cond, thenEnvironment, elseEnvironment);
            default -> Stream.empty();
        };
    }

    private Stream<Refinement> refine(ResolvedBit.Expression.Equal condition, ResolverEnvironment thenEnvironment, ResolverEnvironment elseEnvironment) {
        return Stream.concat(
                refine(condition.lhs(), condition.rhs().type(), thenEnvironment, elseEnvironment),
                refine(condition.rhs(), condition.lhs().type(), thenEnvironment, elseEnvironment)
        );
    }

    private Stream<Refinement> refine(ResolvedBit.Expression.NotEqual condition, ResolverEnvironment thenEnvironment, ResolverEnvironment elseEnvironment) {
        return Stream.concat(
                refine(condition.lhs(), condition.rhs().type(), elseEnvironment, thenEnvironment),
                refine(condition.rhs(), condition.lhs().type(), elseEnvironment, thenEnvironment)
        );
    }

    private Stream<Refinement> refine(ResolvedBit.Expression.Is condition, ResolverEnvironment thenEnvironment, ResolverEnvironment elseEnvironment) {
        return refine(condition.expression(), condition.type(), thenEnvironment, elseEnvironment);
    }

    private Stream<Refinement> refine(ResolvedBit.Expression.Not condition, ResolverEnvironment thenEnvironment, ResolverEnvironment elseEnvironment) {
        return refine(condition.expression(), elseEnvironment, thenEnvironment);
    }

    private Stream<Refinement> refine(ResolvedBit.Expression.And condition, ResolverEnvironment thenEnvironment, ResolverEnvironment elseEnvironment) {
        return Stream.concat(
                refine(condition.lhs(), thenEnvironment, elseEnvironment),
                refine(condition.rhs(), thenEnvironment, elseEnvironment)
        );
    }

    private Stream<Refinement> refine(ResolvedBit.Expression.Or condition, ResolverEnvironment thenEnvironment, ResolverEnvironment elseEnvironment) {
        var lhsRefinements = refine(condition.lhs(), thenEnvironment, elseEnvironment);
        var rhsRefinements = refine(condition.rhs(), thenEnvironment, elseEnvironment).toList();
        var refinements = new ArrayList<Refinement>();
        lhsRefinements.forEach(refinement -> {
            var rhsRefinement = rhsRefinements.stream()
                    .filter(r -> r.symbol() == refinement.symbol() && r.environment.equals(refinement.environment))
                    .map(r -> r.type)
                    .reduce(any(), Types::intersection);
            refinements.add(new Refinement(refinement.symbol(), union(refinement.type, rhsRefinement), refinement.environment));
        });
        return refinements.stream();
    }

    private Stream<Refinement> refine(ResolvedBit.Expression expression, Type type, ResolverEnvironment thenEnvironment, ResolverEnvironment elseEnvironment) {
        return switch (expression) {
            case ResolvedBit.Expression.Identifier identifier ->
                    Stream.of(new Refinement(identifier.name(), type, thenEnvironment));
            case ResolvedBit.Expression.Plus plus -> Stream.concat(
                    refine(plus.lhs(), subtract(type, plus.rhs().type()), thenEnvironment, elseEnvironment),
                    refine(plus.rhs(), subtract(type, plus.lhs().type()), thenEnvironment, elseEnvironment)
            );
            case ResolvedBit.Expression.Minus minus -> Stream.concat(
                    refine(minus.lhs(), add(type, minus.rhs().type()), thenEnvironment, elseEnvironment),
                    refine(minus.rhs(), negate(subtract(minus.lhs().type(), type)), thenEnvironment, elseEnvironment)
            );
            case ResolvedBit.Expression.Multiply multiply -> Stream.concat(
                    refine(multiply.lhs(), divideExact(type, multiply.rhs().type()), thenEnvironment, elseEnvironment),
                    refine(multiply.rhs(), divideExact(type, multiply.lhs().type()), thenEnvironment, elseEnvironment)
            );
            // divide impossible to refine due to reminder and no Range type
            default -> Stream.empty();
        };
    }

    public static class ResolverException extends RuntimeException {
        public ResolverException(String message) {
            super(message);
        }
    }

    private record Refinement(ResolvedBit.Symbol symbol, Type type, ResolverEnvironment environment) {
    }
}
