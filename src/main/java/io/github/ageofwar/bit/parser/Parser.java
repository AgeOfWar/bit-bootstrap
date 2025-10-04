package io.github.ageofwar.bit.parser;

import io.github.ageofwar.bit.lexer.Lexer;
import io.github.ageofwar.bit.lexer.Token;
import io.github.ageofwar.bit.lexer.TokenStream;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static io.github.ageofwar.bit.parser.Parsers.expect;
import static io.github.ageofwar.bit.parser.Parsers.matches;

public class Parser {
    private static Integer precedence(Token token) {
        return switch (token) {
            case Token.Dot t -> 8;
            case Token.LeftParenthesis t -> 7;
            case Token.Asterisk t -> 6;
            case Token.Slash t -> 6;
            case Token.Minus t -> 5;
            case Token.Plus t -> 5;
            case Token.Keyword(var type) -> switch (type) {
                case AS, IS, NOT -> 4;
                case AND, OR -> 1;
                default -> null;
            };
            case Token.GreaterThan t -> 3;
            case Token.GreaterThanOrEqual t -> 3;
            case Token.LessThan t -> 3;
            case Token.LessThanOrEqual t -> 3;
            case Token.Equal t -> 2;
            case Token.NotEqual t -> 2;
            case Token.Ampersand t -> 1;
            case Token.Pipe t -> 1;
            default -> null;
        };
    }

    private static Associativity associativity(Token token) {
        return Associativity.LEFT;
    }

    private final BufferedTokenStream tokens;
    private final TypeParser typeParser;

    public Parser(TokenStream tokens) {
        this.tokens = new BufferedTokenStream(tokens);
        this.typeParser = new TypeParser(this.tokens);
    }

    public Parser(Reader reader) {
        this(new Lexer(reader));
    }

    public Bit.Program nextProgram() {
        skipNewLines();
        var imports = new ArrayList<Bit.Program.Import>();
        while (matches(tokens, Token.Keyword.Type.FROM)) {
            imports.add(nextImport());
            skipNewLines();
        }
        var statements = new ArrayList<Bit.Declaration>();
        var peek = tokens.peek();
        while ((peek = tokens.peek()) != null) {
            switch (peek) {
                case Token.Keyword(Token.Keyword.Type type) -> {
                    switch (type) {
                        case FUNCTION -> statements.add(nextFunctionDeclaration());
                        case TYPE -> statements.add(typeParser.nextTypeDeclaration());
                        case CLASS -> statements.add(nextClassDeclaration());
                        case VAR -> statements.add(nextVariableDeclaration());
                        case IMPLEMENT -> statements.add(nextImplementation());
                        default -> throw error("Expected declaration");
                    }
                }
                case Token.Identifier identifier -> statements.add(nextValueDeclaration());
                default -> throw error("Expected declaration");
            }
            skipNewLines();
        }
        return new Bit.Program(imports, statements);
    }

    private Bit.Program.Import nextImport() {
        expect(tokens, Token.Keyword.Type.FROM);
        var path = new ArrayList<String>();
        while (true) {
            var identifier = expect(tokens, Token.Identifier.class);
            path.add(identifier.name());
            if (matches(tokens, Token.Dot.class)) {
                tokens.next();
            } else {
                break;
            }
        }
        expect(tokens, Token.Keyword.Type.IMPORT);
        if (matches(tokens, Token.Asterisk.class)) {
            tokens.next();
            return new Bit.Program.Import(path.toArray(new String[0]), new Bit.Program.Import.IdentifierSelector.All());
        }
        var identifiers = new ArrayList<String>();
        while (true) {
            var identifier = expect(tokens, Token.Identifier.class);
            identifiers.add(identifier.name());
            if (matches(tokens, Token.Comma.class)) {
                tokens.next();
                skipNewLines();
            } else {
                break;
            }
        }
        return new Bit.Program.Import(path.toArray(new String[0]), new Bit.Program.Import.IdentifierSelector.Only(identifiers));
    }

    private Bit.Expression.Identifier nextIdentifier() {
        var identifier = expect(tokens, Token.Identifier.class);
        return new Bit.Expression.Identifier(identifier.name());
    }

    private Bit.Declaration.Variable nextVariableDeclaration() {
        expect(tokens, Token.Keyword.Type.VAR);
        var identifier = expect(tokens, Token.Identifier.class);
        expect(tokens, Token.Colon.class);
        var type = typeParser.nextExpression();
        expect(tokens, Token.Assign.class);
        var value = nextExpression();
        return new Bit.Declaration.Variable(identifier.name(), value, type);
    }

    private Bit.Declaration.VariableAssignment nextVariableAssignment() {
        expect(tokens, Token.Keyword.Type.SET);
        var identifier = nextExpression();
        if (!(identifier instanceof Bit.Expression.Identifier) && !(identifier instanceof Bit.Expression.Access)) {
            throw error("Expected identifier for variable assignment, but got: " + identifier);
        }
        expect(tokens, Token.Assign.class);
        var value = nextExpression();
        return new Bit.Declaration.VariableAssignment(identifier, value);
    }

    private Bit.Declaration.Value nextValueDeclaration() {
        var identifier = expect(tokens, Token.Identifier.class);
        if (tokens.peek() instanceof Token.Colon) {
            tokens.next();
            var type = typeParser.nextExpression();
            expect(tokens, Token.Assign.class);
            var value = nextExpression();
            return new Bit.Declaration.Value(identifier.name(), value, type);
        }
        expect(tokens, Token.Assign.class);
        var value = nextExpression();
        return new Bit.Declaration.Value(identifier.name(), value, null);
    }

    private Bit.Expression.Block nextBlock() {
        expect(tokens, Token.LeftBrace.class);
        skipNewLines();
        var statements = new java.util.ArrayList<Bit>();
        while (!(tokens.peek() instanceof Token.RightBrace)) {
            statements.add(nextBlockElement());
            skipNewLines();
        }
        expect(tokens, Token.RightBrace.class);
        return new Bit.Expression.Block(statements);
    }

    private Bit.Declaration.Function nextFunctionDeclaration() {
        expect(tokens, Token.Keyword.Type.FUNCTION);
        var identifier = expect(tokens, Token.Identifier.class);

        var generics = new ArrayList<Bit.Declaration.GenericDeclaration>();
        if (matches(tokens, Token.LessThan.class)) {
            tokens.next();
            skipNewLines();
            while (!(tokens.peek() instanceof Token.GreaterThan)) {
                var genericName = expect(tokens, Token.Identifier.class);
                Bit.TypeExpression genericType = new Bit.TypeExpression.Identifier("Any");
                if (matches(tokens, Token.Colon.class)) {
                    tokens.next();
                    genericType = typeParser.nextExpression();
                }
                generics.add(new Bit.Declaration.GenericDeclaration(genericName.name(), genericType));
                if (matches(tokens, Token.Comma.class)) {
                    tokens.next();
                } else {
                    break;
                }
                skipNewLines();
            }
            expect(tokens, Token.GreaterThan.class);
        }

        expect(tokens, Token.LeftParenthesis.class);
        var parameters = new ArrayList<Bit.Declaration.Function.Parameter>();
        while (!(tokens.peek() instanceof Token.RightParenthesis)) {
            var param = expect(tokens, Token.Identifier.class);
            expect(tokens, Token.Colon.class);
            var type = typeParser.nextExpression();
            parameters.add(new Bit.Declaration.Function.Parameter(param.name(), type));
            var separator = tokens.peek();
            if (separator instanceof Token.Comma) {
                tokens.next();
            } else {
                break;
            }
        }
        expect(tokens, Token.RightParenthesis.class);

        Bit.TypeExpression returnType = new Bit.TypeExpression.Identifier("None");
        if (matches(tokens, Token.Colon.class)) {
            tokens.next();
            returnType = typeParser.nextExpression();
        }
        if (tokens.peek() instanceof Token.Assign) {
            tokens.next();
            skipNewLines();
            var body = nextExpression();
            return new Bit.Declaration.Function(identifier.name(), generics, parameters, body, returnType);
        } else {
            var body = nextBlock();
            return new Bit.Declaration.Function(identifier.name(), generics, parameters, body, returnType);
        }
    }

    private Bit.Declaration.Class nextClassDeclaration() {
        expect(tokens, Token.Keyword.Type.CLASS);
        var identifier = expect(tokens, Token.Identifier.class);

        List<Bit.Declaration.GenericDeclaration> typeGenerics = null;
        if (matches(tokens, Token.LessThan.class)) {
            typeGenerics = new ArrayList<>();
            tokens.next();
            skipNewLines();
            while (!(tokens.peek() instanceof Token.GreaterThan)) {
                var genericName = expect(tokens, Token.Identifier.class);
                Bit.TypeExpression genericType = new Bit.TypeExpression.Identifier("Any");
                if (matches(tokens, Token.Colon.class)) {
                    tokens.next();
                    genericType = typeParser.nextExpression();
                }
                typeGenerics.add(new Bit.Declaration.GenericDeclaration(genericName.name(), genericType));
                if (matches(tokens, Token.Comma.class)) {
                    tokens.next();
                } else {
                    break;
                }
                skipNewLines();
            }
            expect(tokens, Token.GreaterThan.class);
        }

        expect(tokens, Token.LeftParenthesis.class);
        skipNewLines();
        var parameters = new ArrayList<Bit.Declaration.Class.Constructor.Parameter>();
        var members = new ArrayList<Bit.Declaration.Class.Member>();
        while (!(tokens.peek() instanceof Token.RightParenthesis)) {
            Bit.Declaration.Class.Member.Visibility visibility = null;
            if (matches(tokens, Token.Keyword.class)) {
                visibility = switch (tokens.peek()) {
                    case Token.Keyword(var type) -> switch (type) {
                        case PRIVATE -> Bit.Declaration.Class.Member.Visibility.PRIVATE;
                        case PUBLIC -> Bit.Declaration.Class.Member.Visibility.PUBLIC;
                        default -> throw error("Expected visibility modifier, but got: " + tokens.peek());
                    };
                    default -> throw error("Expected visibility modifier or identifier, but got: " + tokens.peek());
                };
                tokens.next();
            }
            var param = expect(tokens, Token.Identifier.class);
            expect(tokens, Token.Colon.class);
            skipNewLines();
            var type = typeParser.nextExpression();
            parameters.add(new Bit.Declaration.Class.Constructor.Parameter(param.name(), type));
            if (visibility != null) {
                members.add(new Bit.Declaration.Class.Member(new Bit.Declaration.Value(param.name(), new Bit.Expression.Identifier(param.name()), type), visibility));
            }
            skipNewLines();
            var separator = tokens.peek();
            if (separator instanceof Token.Comma) {
                tokens.next();
            } else {
                break;
            }
        }
        skipNewLines();
        expect(tokens, Token.RightParenthesis.class);
        skipNewLines();
        expect(tokens, Token.LeftBrace.class);
        skipNewLines();
        while (!(tokens.peek() instanceof Token.RightBrace)) {
            var visibility = Bit.Declaration.Class.Member.Visibility.PUBLIC;
            if (matches(tokens, Token.Keyword.Type.PRIVATE)) {
                visibility = Bit.Declaration.Class.Member.Visibility.PRIVATE;
                tokens.next();
                var declaration = nextDeclaration();
                members.add(new Bit.Declaration.Class.Member(declaration, visibility));
            } else if (matches(tokens, Token.Keyword.Type.PUBLIC)) {
                tokens.next();
                var declaration = nextDeclaration();
                members.add(new Bit.Declaration.Class.Member(declaration, visibility));
            } else {
                var declaration = nextDeclaration();
                if (declaration instanceof Bit.Declaration.Value) visibility = Bit.Declaration.Class.Member.Visibility.PRIVATE;
                members.add(new Bit.Declaration.Class.Member(declaration, visibility));
            }
            skipNewLines();
        }
        expect(tokens, Token.RightBrace.class);
        return new Bit.Declaration.Class(identifier.name(), typeGenerics, new Bit.Declaration.Class.Constructor(parameters), members);
    }

    private Bit.Declaration.Implementation nextImplementation() {
        expect(tokens, Token.Keyword.Type.IMPLEMENT);
        var identifier = expect(tokens, Token.Identifier.class);
        expect(tokens, Token.LeftBrace.class);
        skipNewLines();
        var extensions = new ArrayList<Bit.Declaration.Function>();
        while (!(tokens.peek() instanceof Token.RightBrace)) {
            var extension = nextFunctionDeclaration();
            extensions.add(extension);
            skipNewLines();
        }
        expect(tokens, Token.RightBrace.class);
        return new Bit.Declaration.Implementation(identifier.name(), extensions);
    }

    private Bit.Expression.Call nextCall(Bit.Expression expression) {
        List<Bit.TypeExpression> generics = null;
        if (matches(tokens, Token.LessThan.class)) {
            generics = new ArrayList<>();
            tokens.next();
            skipNewLines();
            while (!(tokens.peek() instanceof Token.GreaterThan)) {
                var genericType = typeParser.nextExpression();
                generics.add(genericType);
                if (matches(tokens, Token.Comma.class)) {
                    tokens.next();
                } else {
                    break;
                }
                skipNewLines();
            }
            expect(tokens, Token.GreaterThan.class);
        }

        expect(tokens, Token.LeftParenthesis.class);
        var arguments = new ArrayList<Bit.Expression>();
        while (!(tokens.peek() instanceof Token.RightParenthesis)) {
            arguments.add(nextExpression());
            if (tokens.peek() instanceof Token.Comma) tokens.next();
        }
        expect(tokens, Token.RightParenthesis.class);
        return new Bit.Expression.Call(expression, arguments, generics);
    }

    public Bit.Expression nextExpression() {
        return nextExpression(0);
    }

    private Bit.Expression nextExpression(int minPrecedence) {
        var lhs = nextPrimaryExpression();
        while (true) {
            var operator = tokens.peek();
            if (operator == null) return lhs;
            var precedence = precedence(operator);
            if (precedence == null || precedence < minPrecedence) return lhs;
            var assoc = associativity(operator);
            int nextMinPrecedence = assoc != Associativity.RIGHT ? precedence + 1 : precedence;
            if (matches(tokens, Token.Keyword.Type.AS) || matches(tokens, Token.Keyword.Type.IS)) {
                tokens.next();
                var rhs = typeParser.nextExpression();
                lhs = switch (operator) {
                    case Token.Keyword(var type) -> switch (type) {
                        case AS -> new Bit.Expression.As(lhs, rhs);
                        case IS -> new Bit.Expression.Is(lhs, rhs);
                        default -> throw error("Unexpected keyword: " + type);
                    };
                    default -> throw error("Expected 'as' or 'is' after operator: " + operator);
                };
                continue;
            }
            if (matches(tokens, Token.LeftParenthesis.class) || matches(tokens, Token.LessThan.class)) {
                try {
                    lhs = nextCall(lhs);
                    continue;
                } catch (ParserException ignored) {
                    ignored.printStackTrace();
                }
            }
            tokens.next();
            var rhs = nextExpression(nextMinPrecedence);
            lhs = switch (operator) {
                case Token.Plus token -> new Bit.Expression.Plus(lhs, rhs);
                case Token.Minus token -> new Bit.Expression.Minus(lhs, rhs);
                case Token.Asterisk token -> new Bit.Expression.Multiply(lhs, rhs);
                case Token.Slash token -> new Bit.Expression.Divide(lhs, rhs);
                case Token.GreaterThan token -> new Bit.Expression.GreaterThan(lhs, rhs);
                case Token.GreaterThanOrEqual token -> new Bit.Expression.GreaterThanOrEqual(lhs, rhs);
                case Token.LessThan token -> new Bit.Expression.LessThan(lhs, rhs);
                case Token.LessThanOrEqual token -> new Bit.Expression.LessThanOrEqual(lhs, rhs);
                case Token.Equal token -> new Bit.Expression.Equal(lhs, rhs);
                case Token.NotEqual token -> new Bit.Expression.NotEqual(lhs, rhs);
                case Token.Keyword(var type) -> switch (type) {
                    case AND -> new Bit.Expression.And(lhs, rhs);
                    case OR -> new Bit.Expression.Or(lhs, rhs);
                    default -> throw error("Unexpected keyword: " + type);
                };
                case Token.Dot token -> {
                    if (!(rhs instanceof Bit.Expression.Identifier)) {
                        throw error("Expected identifier after dot, but got: " + rhs);
                    }
                    var field = ((Bit.Expression.Identifier) rhs).name();
                    yield new Bit.Expression.Access(lhs, field);
                }
                default -> throw error("Unexpected operator: " + operator);
            };
        }
    }

    private Bit.Expression nextPrimaryExpression() {
        var peek = tokens.peek(3);
        return switch (peek[0]) {
            case Token.Keyword(var type) -> switch (type) {
                case NOT -> new Bit.Expression.Not(nextExpression(precedence(peek[0])));
                case IF -> nextIfExpression();
                case WHILE -> nextWhileExpression();
                case NEW -> nextInstantiation();
                case BREAK -> nextBreak();
                case CONTINUE -> nextContinue();
                case RETURN -> nextReturn();
                default -> throw error("Unexpected keyword: " + type);
            };
            case Token.Identifier token -> nextIdentifier();
            case Token.NumberLiteral token -> nextNumber();
            case Token.StringLiteral token -> nextString();
            case Token.BooleanLiteral token -> nextBoolean();
            case Token.LeftParenthesis token -> peek[1] instanceof Token.RightParenthesis || peek[2] instanceof Token.Colon ? nextFunction() : nextGroupExpression();
            case Token.LeftBracket token ->  nextStruct();
            case Token.LeftBrace token -> nextBlock();
            case Token.LessThan token -> nextFunction();
            default -> throw error("Expected expression, but got: " + tokens.peek());
        };
    }

    private Bit.Expression.Instantiation nextInstantiation() {
        expect(tokens, Token.Keyword.Type.NEW);
        var className = expect(tokens, Token.Identifier.class).name();
        List<Bit.TypeExpression> generics = null;
        if (matches(tokens, Token.LessThan.class)) {
            generics = new ArrayList<>();
            tokens.next();
            skipNewLines();
            while (!(tokens.peek() instanceof Token.GreaterThan)) {
                var genericType = typeParser.nextExpression();
                generics.add(genericType);
                if (matches(tokens, Token.Comma.class)) {
                    tokens.next();
                } else {
                    break;
                }
                skipNewLines();
            }
            expect(tokens, Token.GreaterThan.class);
        }
        expect(tokens, Token.LeftParenthesis.class);
        var arguments = new ArrayList<Bit.Expression>();
        while (!(tokens.peek() instanceof Token.RightParenthesis)) {
            arguments.add(nextExpression());
            if (tokens.peek() instanceof Token.Comma) tokens.next();
        }
        expect(tokens, Token.RightParenthesis.class);
        return new Bit.Expression.Instantiation(className, arguments, generics);
    }

    private Bit.Expression nextGroupExpression() {
        expect(tokens, Token.LeftParenthesis.class);
        skipNewLines();
        var expression = nextExpression();
        skipNewLines();
        expect(tokens, Token.RightParenthesis.class);
        return expression;
    }

    private Bit.Expression.If nextIfExpression() {
        expect(tokens, Token.Keyword.Type.IF);
        expect(tokens, Token.LeftParenthesis.class);
        var condition = nextExpression();
        expect(tokens, Token.RightParenthesis.class);
        skipNewLines();
        var thenBranch = nextExpression();
        Bit.Expression elseBranch = null;
        skipNewLines();
        if (matches(tokens, Token.Keyword.Type.ELSE)) {
            tokens.next();
            skipNewLines();
            elseBranch = nextExpression();
        }
        return new Bit.Expression.If(condition, thenBranch, elseBranch);
    }

    private Bit.Expression.While nextWhileExpression() {
        expect(tokens, Token.Keyword.Type.WHILE);
        expect(tokens, Token.LeftParenthesis.class);
        var condition = nextExpression();
        expect(tokens, Token.RightParenthesis.class);
        skipNewLines();
        var body = nextExpression();
        return new Bit.Expression.While(condition, body);
    }

    private Bit.Expression.Struct nextStruct() {
        expect(tokens, Token.LeftBracket.class);
        skipNewLines();
        var fields = new HashMap<String, Bit.Expression>();
        while (!(tokens.peek() instanceof Token.RightBracket)) {
            var identifier = expect(tokens, Token.Identifier.class);
            expect(tokens, Token.Colon.class);
            var value = nextExpression();
            fields.put(identifier.name(), value);
            if (matches(tokens, Token.Comma.class) || matches(tokens, Token.NewLine.class)) {
                tokens.next();
            }
            skipNewLines();
        }
        expect(tokens, Token.RightBracket.class);
        return new Bit.Expression.Struct(fields);
    }

    private Bit nextBlockElement() {
        var tokens = this.tokens.peek(2);
        return switch (tokens[0]) {
            case Token.Keyword(var type) -> switch (type) {
                case RETURN -> nextReturn();
                case BREAK -> nextBreak();
                case CONTINUE -> nextContinue();
                case IF, WHILE, NEW -> nextExpression();
                case SET -> nextVariableAssignment();
                default -> nextDeclaration();
            };
            case Token.Identifier identifier -> tokens[1] instanceof Token.Assign || tokens[1] instanceof Token.Colon ? nextValueDeclaration() : nextExpression();
            default -> nextExpression();
        };
    }

    private Bit.Declaration nextDeclaration() {
        return switch (tokens.peek()) {
            case Token.Keyword(var type) -> switch (type) {
                case FUNCTION -> nextFunctionDeclaration();
                case TYPE -> typeParser.nextTypeDeclaration();
                case CLASS -> nextClassDeclaration();
                case VAR -> nextVariableDeclaration();
                case IMPLEMENT -> nextImplementation();
                default -> throw error("Expected declaration");
            };
            case Token.Identifier identifier -> nextValueDeclaration();
            default -> throw error("Expected declaration");
        };
    }

    private Bit.Expression.NumberLiteral nextNumber() {
        var token = expect(tokens, Token.NumberLiteral.class);
        return new Bit.Expression.NumberLiteral(token.value());
    }

    private Bit.Expression.StringLiteral nextString() {
        var token = expect(tokens, Token.StringLiteral.class);
        return new Bit.Expression.StringLiteral(token.value());
    }

    private Bit.Expression.BooleanLiteral nextBoolean() {
        var token = expect(tokens, Token.BooleanLiteral.class);
        return new Bit.Expression.BooleanLiteral(token.value());
    }

    private Bit.Expression.Function nextFunction() {
        List<Bit.Declaration.GenericDeclaration> generics = null;
        if (matches(tokens, Token.LessThan.class)) {
            generics = new ArrayList<>();
            tokens.next();
            skipNewLines();
            while (!(tokens.peek() instanceof Token.GreaterThan)) {
                var genericName = expect(tokens, Token.Identifier.class);
                Bit.TypeExpression genericType = new Bit.TypeExpression.Identifier("Any");
                if (matches(tokens, Token.Colon.class)) {
                    tokens.next();
                    genericType = typeParser.nextExpression();
                }
                generics.add(new Bit.Declaration.GenericDeclaration(genericName.name(), genericType));
                if (matches(tokens, Token.Comma.class)) {
                    tokens.next();
                } else {
                    break;
                }
                skipNewLines();
            }
            expect(tokens, Token.GreaterThan.class);
        }

        expect(tokens, Token.LeftParenthesis.class);
        var parameters = new ArrayList<Bit.Expression.Function.Parameter>();
        while (!(tokens.peek() instanceof Token.RightParenthesis)) {
            var param = expect(tokens, Token.Identifier.class);
            expect(tokens, Token.Colon.class);
            var type = typeParser.nextExpression();
            parameters.add(new Bit.Expression.Function.Parameter(param.name(), type));
            if (tokens.peek() instanceof Token.Comma) {
                tokens.next();
            } else {
                break;
            }
        }
        expect(tokens, Token.RightParenthesis.class);
        Bit.TypeExpression returnType = null;
        if (matches(tokens, Token.Colon.class)) {
            tokens.next();
            returnType = typeParser.nextExpression();
        }
        expect(tokens, Token.Arrow.class);
        var body = nextExpression();
        return new Bit.Expression.Function(generics, parameters, body, returnType);
    }

    private Bit.Expression.Break nextBreak() {
        expect(tokens, Token.Keyword.Type.BREAK);
        return new Bit.Expression.Break();
    }

    private Bit.Expression.Continue nextContinue() {
        expect(tokens, Token.Keyword.Type.CONTINUE);
        return new Bit.Expression.Continue();
    }

    private Bit.Expression.Return nextReturn() {
        expect(tokens, Token.Keyword.Type.RETURN);
        if (tokens.peek() instanceof Token.NewLine || tokens.peek() instanceof Token.RightBrace) {
            return new Bit.Expression.Return(new Bit.Expression.Identifier("None"));
        }
        var value = nextExpression();
        return new Bit.Expression.Return(value);
    }

    private void skipNewLines() {
        while (tokens.peek() instanceof Token.NewLine) tokens.next();
    }
    
    private ParserException error(String message) {
        return new ParserException(message + " at token: " + tokens.peek());
    }
}
