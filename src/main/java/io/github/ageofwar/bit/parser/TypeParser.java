package io.github.ageofwar.bit.parser;

import io.github.ageofwar.bit.lexer.Token;
import io.github.ageofwar.bit.lexer.TokenStream;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static io.github.ageofwar.bit.parser.Parsers.expect;
import static io.github.ageofwar.bit.parser.Parsers.matches;

public class TypeParser {
    private static final Map<Class<? extends Token>, Integer> OPERATOR_PRECEDENCES = Map.ofEntries(
            new AbstractMap.SimpleEntry<>(Token.Dot.class, 7),
            new AbstractMap.SimpleEntry<>(Token.Keyword.class, 6),
            new AbstractMap.SimpleEntry<>(Token.Asterisk.class, 5),
            new AbstractMap.SimpleEntry<>(Token.Slash.class, 5),
            new AbstractMap.SimpleEntry<>(Token.Minus.class, 4),
            new AbstractMap.SimpleEntry<>(Token.Plus.class, 4),
            new AbstractMap.SimpleEntry<>(Token.Equal.class, 2),
            new AbstractMap.SimpleEntry<>(Token.NotEqual.class, 2),
            new AbstractMap.SimpleEntry<>(Token.Ampersand.class, 1),
            new AbstractMap.SimpleEntry<>(Token.Pipe.class, 1)
    );

    private static final Map<Class<? extends Token>, Associativity> OPERATOR_ASSOCIATIVITY = Map.ofEntries(
            new AbstractMap.SimpleEntry<>(Token.Plus.class, Associativity.LEFT),
            new AbstractMap.SimpleEntry<>(Token.Minus.class, Associativity.LEFT),
            new AbstractMap.SimpleEntry<>(Token.Asterisk.class, Associativity.LEFT),
            new AbstractMap.SimpleEntry<>(Token.Slash.class, Associativity.LEFT),
            new AbstractMap.SimpleEntry<>(Token.Equal.class, Associativity.LEFT),
            new AbstractMap.SimpleEntry<>(Token.NotEqual.class, Associativity.LEFT),
            new AbstractMap.SimpleEntry<>(Token.Keyword.class, Associativity.LEFT),
            new AbstractMap.SimpleEntry<>(Token.Dot.class, Associativity.LEFT),
            new AbstractMap.SimpleEntry<>(Token.Ampersand.class, Associativity.LEFT),
            new AbstractMap.SimpleEntry<>(Token.Pipe.class, Associativity.LEFT)
    );

    private final BufferedTokenStream tokens;

    public TypeParser(BufferedTokenStream tokens) {
        this.tokens = tokens;
    }

    public TypeParser(TokenStream tokens) {
        this(new BufferedTokenStream(tokens));
    }

    public Bit.Declaration.Type nextTypeDeclaration() {
        skipNewLines();
        expect(tokens, Token.Keyword.Type.TYPE);
        var identifier = expect(tokens, Token.Identifier.class);
        var parameters = new ArrayList<Bit.Declaration.Type.TypeParameter>();
        if (tokens.peek() instanceof Token.LessThan) {
            expect(tokens, Token.LessThan.class);
            skipNewLines();
            while (!(tokens.peek() instanceof Token.GreaterThan)) {
                var paramIdentifier = expect(tokens, Token.Identifier.class);
                if (tokens.peek() instanceof Token.Colon) {
                    expect(tokens, Token.Colon.class);
                    var typeExpression = nextExpression();
                    parameters.add(new Bit.Declaration.Type.TypeParameter(paramIdentifier.name(), typeExpression));
                } else {
                    parameters.add(new Bit.Declaration.Type.TypeParameter(paramIdentifier.name(), new Bit.TypeExpression.Identifier("Any")));
                }
                skipNewLines();
                if (tokens.peek() instanceof Token.Comma) {
                    tokens.next();
                    skipNewLines();
                }
            }
            expect(tokens, Token.GreaterThan.class);
        }
        var peek = tokens.peek();
        if (!(peek instanceof Token.Assign)) {
            return new Bit.Declaration.Type(identifier.name(), parameters, null);
        }
        expect(tokens, Token.Assign.class);
        var typeExpression = nextExpression();
        return new Bit.Declaration.Type(identifier.name(), parameters, typeExpression);
    }

    public Bit.TypeExpression nextExpression() {
        return nextExpression(0);
    }

    private Bit.TypeExpression nextExpression(int minPrecedence) {
        var lhs = nextPrimaryExpression();
        while (true) {
            var operator = tokens.peek();
            if (operator == null) return lhs;
            var precedence = OPERATOR_PRECEDENCES.get(operator.getClass());
            if (precedence == null || precedence < minPrecedence) return lhs;
            var assoc = OPERATOR_ASSOCIATIVITY.get(operator.getClass());
            int nextMinPrecedence = assoc == Associativity.LEFT ? precedence + 1 : precedence;
            tokens.next();
            var rhs = nextExpression(nextMinPrecedence);
            lhs = switch (operator) {
                case Token.Plus token -> new Bit.TypeExpression.Plus(lhs, rhs);
                case Token.Minus token -> new Bit.TypeExpression.Minus(lhs, rhs);
                case Token.Asterisk token -> new Bit.TypeExpression.Multiply(lhs, rhs);
                case Token.Slash token -> new Bit.TypeExpression.Divide(lhs, rhs);
                case Token.Ampersand token -> new Bit.TypeExpression.Intersection(lhs, rhs);
                case Token.Pipe token -> new Bit.TypeExpression.Union(lhs, rhs);
                default -> throw new ParserException("Unexpected operator: " + operator);
            };
        }
    }

    private Bit.TypeExpression nextPrimaryExpression() {
        var peek = tokens.peek(3);
        var expression = switch (peek[0]) {
            case Token.Identifier token -> nextIdentifier();
            case Token.NumberLiteral token -> nextNumberLiteral();
            case Token.StringLiteral token -> nextStringLiteral();
            case Token.BooleanLiteral token -> nextBooleanLiteral();
            case Token.LeftParenthesis token -> peek[1] instanceof Token.RightParenthesis || peek[2] instanceof Token.Colon ? nextFunction() : nextGroupedExpression();
            case Token.LeftBracket token -> nextStruct();
            case Token.Keyword keyword -> switch (keyword.type()) {
                case Token.Keyword.Type.MATCH -> nextMatch();
                default -> throw new ParserException("Unexpected keyword: " + keyword.type());
            };
            default -> throw new ParserException("Expected type expression, but got: " + tokens.peek());
        };
        if (tokens.peek() instanceof Token.LeftParenthesis && expression instanceof Bit.TypeExpression.Identifier identifier) {
            tokens.next();
            var arguments = new ArrayList<Bit.TypeExpression>();
            skipNewLines();
            while (!(tokens.peek() instanceof Token.RightParenthesis)) {
                arguments.add(nextExpression());
                skipNewLines();
                if (tokens.peek() instanceof Token.Comma) {
                    tokens.next();
                    skipNewLines();
                }
            }
            expect(tokens, Token.RightParenthesis.class);
            return new Bit.TypeExpression.Call(identifier, arguments);
        } else {
            return expression;
        }
    }

    private Bit.TypeExpression.Struct nextStruct() {
        expect(tokens, Token.LeftBracket.class);
        skipNewLines();
        var fields = new HashMap<String, Bit.TypeExpression>();
        while (!(tokens.peek() instanceof Token.RightBracket)) {
            if (matches(tokens, Token.Keyword.Type.FUNCTION)) {
                tokens.next();
                var name = expect(tokens, Token.Identifier.class);
                expect(tokens, Token.LeftParenthesis.class);
                var parameters = new ArrayList<Bit.TypeExpression>();
                skipNewLines();
                while (!(tokens.peek() instanceof Token.RightParenthesis)) {
                    parameters.add(nextExpression());
                    skipNewLines();
                    if (tokens.peek() instanceof Token.Comma) {
                        tokens.next();
                        skipNewLines();
                    }
                }
                expect(tokens, Token.RightParenthesis.class);
                expect(tokens, Token.Colon.class);
                var returnType = nextExpression();
                fields.put(name.name(), new Bit.TypeExpression.Function(parameters, returnType));
            } else {
                var identifier = nextIdentifier();
                expect(tokens, Token.Colon.class);
                var typeExpression = nextExpression();
                fields.put(identifier.name(), typeExpression);
            }
            skipNewLines();
            if (tokens.peek() instanceof Token.Comma) {
                tokens.next();
                skipNewLines();
            }
        }
        expect(tokens, Token.RightBracket.class);
        return new Bit.TypeExpression.Struct(fields);
    }

    private Bit.TypeExpression nextGroupedExpression() {
        expect(tokens, Token.LeftParenthesis.class);
        var expression = nextExpression();
        expect(tokens, Token.RightParenthesis.class);
        return expression;
    }

    private Bit.TypeExpression.Identifier nextIdentifier() {
        var identifier = expect(tokens, Token.Identifier.class);
        return new Bit.TypeExpression.Identifier(identifier.name());
    }

    private Bit.TypeExpression.NumberLiteral nextNumberLiteral() {
        var number = expect(tokens, Token.NumberLiteral.class);
        return new Bit.TypeExpression.NumberLiteral(number.value());
    }

    private Bit.TypeExpression.StringLiteral nextStringLiteral() {
        var string = expect(tokens, Token.StringLiteral.class);
        return new Bit.TypeExpression.StringLiteral(string.value());
    }

    private Bit.TypeExpression.BooleanLiteral nextBooleanLiteral() {
        var booleanLiteral = expect(tokens, Token.BooleanLiteral.class);
        return new Bit.TypeExpression.BooleanLiteral(booleanLiteral.value());
    }

    private Bit.TypeExpression.Match nextMatch() {
        expect(tokens, Token.Keyword.Type.MATCH);
        var expression = nextExpression();
        expect(tokens, Token.LeftBrace.class);
        skipNewLines();
        var cases = new ArrayList<Bit.TypeExpression.Match.MatchCase>();
        while (!(tokens.peek() instanceof Token.RightBrace)) {
            var pattern = nextMatchPattern();
            expect(tokens, Token.Assign.class);
            expect(tokens, Token.GreaterThan.class);
            var value = nextExpression();
            cases.add(new Bit.TypeExpression.Match.MatchCase(pattern, value));
            skipNewLines();
            if (tokens.peek() instanceof Token.Comma) {
                tokens.next();
                skipNewLines();
            }
        }
        expect(tokens, Token.RightBrace.class);
        return new Bit.TypeExpression.Match(expression, cases);
    }

    private Bit.TypeExpression.Match.Pattern nextMatchPattern() {
        var token = tokens.peek();
        return switch (token) {
            case Token.Keyword(Token.Keyword.Type type) when type == Token.Keyword.Type.ELSE -> {
                tokens.next();
                yield new Bit.TypeExpression.Match.Pattern.Else();
            }
            default -> new Bit.TypeExpression.Match.Pattern.Expression(nextExpression());
        };
    }

    private Bit.TypeExpression.Function nextFunction() {
        expect(tokens, Token.LeftParenthesis.class);
        skipNewLines();
        var parameters = new ArrayList<Bit.TypeExpression>();
        while (!(tokens.peek() instanceof Token.RightParenthesis)) {
            parameters.add(nextExpression());
            skipNewLines();
            if (tokens.peek() instanceof Token.Comma) {
                tokens.next();
                skipNewLines();
            }
        }
        expect(tokens, Token.RightParenthesis.class);
        expect(tokens, Token.Arrow.class);
        var returnType = nextExpression();
        return new Bit.TypeExpression.Function(parameters, returnType);
    }

    private void skipNewLines() {
        while (tokens.peek() instanceof Token.NewLine) tokens.next();
    }
}
