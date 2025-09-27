package io.github.ageofwar.bit.print;

import io.github.ageofwar.bit.parser.Bit;

import java.util.Objects;

public class Printer {
    public String print(Bit bit) {
        return switch (bit) {
            case Bit.Program program -> printProgram(program);
            case Bit.Declaration declaration -> printDeclaration(declaration);
            case Bit.Expression expression -> printExpression(expression);
            case Bit.TypeExpression typeExpression -> printTypeExpression(typeExpression);
        };
    }

    private String printProgram(Bit.Program program) {
        var sb = new StringBuilder();
        for (Bit.Declaration declaration : program.declarations()) {
            sb.append(printDeclaration(declaration)).append("\n");
        }
        return sb.toString();
    }

    private String printDeclaration(Bit.Declaration declaration) {
        return switch (declaration) {
            case Bit.Declaration.Variable variable -> printVariableDeclaration(variable);
            case Bit.Declaration.VariableAssignment assignment -> printVariableAssignment(assignment);
            case Bit.Declaration.Value value -> printValueDeclaration(value);
            case Bit.Declaration.Function function -> printFunctionDeclaration(function);
            case Bit.Declaration.Type type -> printTypeDeclaration(type);
            case Bit.Declaration.Class classDecl -> printClassDeclaration(classDecl);
            case Bit.Declaration.Implementation implementation -> printImplementation(implementation);
        };
    }

    private String printVariableDeclaration(Bit.Declaration.Variable value) {
        if(value.type() != null) {
            return "var " + value.name() + ": " + printTypeExpression(value.type()) + " = " + printExpression(value.value());
        }
        return "var " + value.name() + " = " + printExpression(value.value());
    }

    private String printVariableAssignment(Bit.Declaration.VariableAssignment assignment) {
        return "set " + assignment.name() + " = " + printExpression(assignment.value());
    }

    private String printValueDeclaration(Bit.Declaration.Value value) {
        if(value.type() != null) {
            return value.name() + ": " + printTypeExpression(value.type()) + " = " + printExpression(value.value());
        }
        return value.name() + " = " + printExpression(value.value());
    }

    private String printFunctionDeclaration(Bit.Declaration.Function function) {
        var returnType = !(function.returnType() instanceof Bit.TypeExpression.Identifier(String name)) || !Objects.equals(name, "None") ? ": " + printTypeExpression(function.returnType()) : "";
        if (function.body() instanceof Bit.Expression.Block) {
            return "fun " + function.name() + "(" + String.join(", ", function.parameters().stream().map(p -> p.name() + ": " + printTypeExpression(p.type())).toList()) + ")" + returnType + " " + printExpression(function.body());
        }
        return "fun " + function.name() + "(" + String.join(", ", function.parameters().stream().map(p -> p.name() + ": " + printTypeExpression(p.type())).toList()) + ")" + returnType + " = " + printExpression(function.body());
    }

    private String printClassDeclaration(Bit.Declaration.Class classDecl) {
        var sb = new StringBuilder();
        sb.append("class ").append(classDecl.name()).append("(");
        for (int i = 0; i < classDecl.constructor().parameters().size(); i++) {
            var param = classDecl.constructor().parameters().get(i);
            sb.append(param.name()).append(": ").append(printTypeExpression(param.type()));
            if (i < classDecl.constructor().parameters().size() - 1) {
                sb.append(", ");
            }
        }
        sb.append(") {\n");
        for (var member : classDecl.members()) {
            sb.append("  ").append(member.visibility().name().toLowerCase()).append(" ").append(printDeclaration(member.declaration()).replace("\n", "\n  ")).append("\n");
        }
        sb.append("}");
        return sb.toString();
    }

    private String printImplementation(Bit.Declaration.Implementation implementation) {
        var sb = new StringBuilder();
        sb.append("implement ").append(implementation.name()).append(" {\n");
        for (var extension : implementation.extensions()) {
            sb.append("  ").append(printFunctionDeclaration(extension).replace("\n", "\n  ")).append("\n");
        }
        sb.append("}");
        return sb.toString();
    }

    private String printExpression(Bit.Expression expression) {
        return switch (expression) {
            case Bit.Expression.Identifier identifier -> identifier.name();
            case Bit.Expression.Call call -> printCallExpression(call);
            case Bit.Expression.Block block -> printBlockExpression(block);
            case Bit.Expression.NumberLiteral numberLiteral -> numberLiteral.value();
            case Bit.Expression.StringLiteral stringLiteral -> "\"" + stringLiteral.value() + "\"";
            case Bit.Expression.BooleanLiteral booleanLiteral -> String.valueOf(booleanLiteral.value());
            case Bit.Expression.Minus minus -> printBinaryExpression("-", minus.lhs(), minus.rhs());
            case Bit.Expression.Plus plus -> printBinaryExpression("+", plus.lhs(), plus.rhs());
            case Bit.Expression.Multiply multiply -> printBinaryExpression("*", multiply.lhs(), multiply.rhs());
            case Bit.Expression.Divide divide -> printBinaryExpression("/", divide.lhs(), divide.rhs());
            case Bit.Expression.If ifExpression -> printIf(ifExpression);
            case Bit.Expression.GreaterThan greaterThan -> printGreaterThan(greaterThan);
            case Bit.Expression.LessThan lessThan -> printLessThan(lessThan);
            case Bit.Expression.GreaterThanOrEqual greaterThanOrEqual -> printGreaterThanOrEqual(greaterThanOrEqual);
            case Bit.Expression.LessThanOrEqual lessThanOrEqual -> printLessThanOrEqual(lessThanOrEqual);
            case Bit.Expression.Equal equal -> printEqual(equal);
            case Bit.Expression.As asExpression -> printExpression(asExpression.expression()) + " as " + printTypeExpression(asExpression.type());
            case Bit.Expression.Is isExpression -> printExpression(isExpression.expression()) + " is " + printTypeExpression(isExpression.type());
            case Bit.Expression.NotEqual notEqual -> printBinaryExpression("!=", notEqual.lhs(), notEqual.rhs());
            case Bit.Expression.Struct struct -> printStruct(struct);
            case Bit.Expression.Access access -> printAccess(access);
            case Bit.Expression.And and -> printBinaryExpression("&&", and.lhs(), and.rhs());
            case Bit.Expression.Or or -> printBinaryExpression("||", or.lhs(), or.rhs());
            case Bit.Expression.Not not -> "!" + printExpression(not.expression());
            case Bit.Expression.Function function -> printFunction(function);
            case Bit.Expression.Instantiation instantiation -> "new " + instantiation.className() + "(" + String.join(", ", instantiation.arguments().stream().map(this::printExpression).toList()) + ")";
        };
    }

    private String printCallExpression(Bit.Expression.Call call) {
        var sb = new StringBuilder();
        sb.append(printExpression(call.callee())).append("(");
        for (int i = 0; i < call.arguments().size(); i++) {
            sb.append(printExpression(call.arguments().get(i)));
            if (i < call.arguments().size() - 1) {
                sb.append(", ");
            }
        }
        sb.append(")");
        return sb.toString();
    }

    private String printBlockExpression(Bit.Expression.Block block) {
        var sb = new StringBuilder();
        sb.append("{\n");
        for (Bit statement : block.statements()) {
            sb.append("  ").append(print(statement).replace("\n", "\n  ")).append("\n");
        }
        sb.append("}");
        return sb.toString();
    }

    private String printBinaryExpression(String operator, Bit.Expression lhs, Bit.Expression rhs) {
        return printExpression(lhs) + " " + operator + " " + printExpression(rhs);
    }

    private String printTypeExpression(Bit.TypeExpression expression) {
        return switch (expression) {
            case Bit.TypeExpression.Identifier identifier -> identifier.name();
            case Bit.TypeExpression.NumberLiteral numberLiteral -> String.valueOf(numberLiteral.value());
            case Bit.TypeExpression.StringLiteral stringLiteral -> "\"" + stringLiteral.value() + "\"";
            case Bit.TypeExpression.BooleanLiteral booleanLiteral -> String.valueOf(booleanLiteral.value());
            case Bit.TypeExpression.Plus plus -> printTypeBinaryExpression("+", plus.lhs(), plus.rhs());
            case Bit.TypeExpression.Minus minus -> printTypeBinaryExpression("-", minus.lhs(), minus.rhs());
            case Bit.TypeExpression.Multiply multiply -> printTypeBinaryExpression("*", multiply.lhs(), multiply.rhs());
            case Bit.TypeExpression.Divide divide -> printTypeBinaryExpression("/", divide.lhs(), divide.rhs());
            case Bit.TypeExpression.Union union -> printTypeBinaryExpression("|", union.lhs(), union.rhs());
            case Bit.TypeExpression.Intersection intersection -> printTypeBinaryExpression("&", intersection.lhs(), intersection.rhs());
            case Bit.TypeExpression.Struct struct -> printTypeStruct(struct);
            case Bit.TypeExpression.Call call -> printTypeCall(call);
            case Bit.TypeExpression.Match match -> printTypeMatch(match);
            case Bit.TypeExpression.Function function -> printTypeFunction(function);
        };
    }

    private String printTypeBinaryExpression(String operator, Bit.TypeExpression lhs, Bit.TypeExpression rhs) {
        return printTypeExpression(lhs) + " " + operator + " " + printTypeExpression(rhs);
    }

    private String printTypeDeclaration(Bit.Declaration.Type type) {
        var params = "(" + String.join(", ", type.parameters().stream().map(e -> e.name() + (e.type() instanceof Bit.TypeExpression.Identifier(String name) && name.equals("Any") ? "" : ": " + printTypeExpression(e.type()))).toArray(String[]::new)) + ")";
        if (type.value() == null) return "type " + type.name() + (type.parameters().isEmpty() ? "" : params);
        return "type " + type.name() + (type.parameters().isEmpty() ? "" : params) + " = " + printTypeExpression(type.value());
    }

    private String printTypeStruct(Bit.TypeExpression.Struct struct) {
        var sb = new StringBuilder("[\n");
        for (var entry : struct.fields().entrySet()) {
            sb.append("  ").append(entry.getKey()).append(": ").append(printTypeExpression(entry.getValue())).append("\n");
        }
        sb.append("]");
        return sb.toString();
    }

    private String printTypeCall(Bit.TypeExpression.Call call) {
        var sb = new StringBuilder();
        sb.append(printTypeExpression(call.callee())).append("(");
        for (int i = 0; i < call.arguments().size(); i++) {
            sb.append(printTypeExpression(call.arguments().get(i)));
            if (i < call.arguments().size() - 1) {
                sb.append(", ");
            }
        }
        sb.append(")");
        return sb.toString();
    }

    private String printTypeMatch(Bit.TypeExpression.Match match) {
        var sb = new StringBuilder("match " + printTypeExpression(match.expression()) + " {\n");
        for (var matchCase : match.cases()) {
            sb.append("  ");
            switch (matchCase.pattern()) {
                case Bit.TypeExpression.Match.Pattern.Expression expression -> sb.append(printTypeExpression(expression.expression()));
                case Bit.TypeExpression.Match.Pattern.Else elsePattern -> sb.append("else");
            }
            sb.append(" => ").append(printTypeExpression(matchCase.value())).append("\n");
        }
        sb.append("}");
        return sb.toString();
    }

    private String printTypeFunction(Bit.TypeExpression.Function function) {
        var sb = new StringBuilder();
        sb.append("(");
        for (int i = 0; i < function.parameters().size(); i++) {
            sb.append(printTypeExpression(function.parameters().get(i)));
            if (i < function.parameters().size() - 1) {
                sb.append(", ");
            }
        }
        sb.append(") -> ").append(printTypeExpression(function.returnType()));
        return sb.toString();
    }

    private String printGreaterThan(Bit.Expression.GreaterThan greaterThan) {
        return printBinaryExpression(">", greaterThan.lhs(), greaterThan.rhs());
    }

    private String printLessThan(Bit.Expression.LessThan lessThan) {
        return printBinaryExpression("<", lessThan.lhs(), lessThan.rhs());
    }

    private String printGreaterThanOrEqual(Bit.Expression.GreaterThanOrEqual greaterThanOrEqual) {
        return printBinaryExpression(">=", greaterThanOrEqual.lhs(), greaterThanOrEqual.rhs());
    }

    private String printLessThanOrEqual(Bit.Expression.LessThanOrEqual lessThanOrEqual) {
        return printBinaryExpression("<=", lessThanOrEqual.lhs(), lessThanOrEqual.rhs());
    }

    private String printEqual(Bit.Expression.Equal equal) {
        return printBinaryExpression("==", equal.lhs(), equal.rhs());
    }

    private String printAccess(Bit.Expression.Access access) {
        return printExpression(access.expression()) + "." + access.field();
    }

    private String printIf(Bit.Expression.If ifExpression) {
        var sb = new StringBuilder();
        sb.append("if (").append(printExpression(ifExpression.condition())).append(") ");
        sb.append(printExpression(ifExpression.thenBranch()));
        if (ifExpression.elseBranch() != null) {
            sb.append(" else ").append(printExpression(ifExpression.elseBranch()));
        }
        return sb.toString();
    }

    private String printStruct(Bit.Expression.Struct struct) {
        var sb = new StringBuilder("[\n");
        for (var entry : struct.fields().entrySet()) {
            sb.append("  ").append(entry.getKey()).append(": ").append(printExpression(entry.getValue())).append("\n");
        }
        sb.append("]");
        return sb.toString();
    }
    
    private String printFunction(Bit.Expression.Function function) {
        var sb = new StringBuilder();
        sb.append("(");
        for (int i = 0; i < function.parameters().size(); i++) {
            var param = function.parameters().get(i);
            sb.append(param.name()).append(": ").append(printTypeExpression(param.type()));
            if (i < function.parameters().size() - 1) {
                sb.append(", ");
            }
        }
        sb.append(")");
        if (function.returnType() != null) {
            sb.append(": ").append(printTypeExpression(function.returnType()));
        }
        sb.append(" -> ");
        sb.append(printExpression(function.body()));
        return sb.toString();
    }
}
