package io.github.ageofwar.bit.lexer;

import java.io.Reader;
import java.util.function.IntPredicate;

public class Lexer implements TokenStream {
    private final BufferedCharStream reader;

    public Lexer(Reader reader) {
        this.reader = new BufferedCharStream(reader);
    }

    @Override
    public Token nextToken() {
        var peek = reader.peek(2);
        return switch (peek[0]) {
            case -1 -> null;
            case '(' -> nextLeftParenthesis();
            case ')' -> nextRightParenthesis();
            case '[' -> nextLeftBracket();
            case ']' -> nextRightBracket();
            case '{' -> nextLeftBrace();
            case '}' -> nextRightBrace();
            case '"' -> nextStringLiteral();
            case '=' -> peek[1] == '=' ? nextEqual() : nextAssign();
            case ',' -> nextComma();
            case ':' -> nextColon();
            case '/' -> peek[1] == '/' ? nextComment() : nextSlash();
            case '*' -> nextAsterisk();
            case '&' -> nextAmpersand();
            case '|' -> nextPipe();
            case '.' -> nextDot();
            case '>' -> peek[1] == '=' ? nextGreaterThanOrEqual() : nextGreaterThan();
            case '<' -> peek[1] == '=' ? nextLessThanOrEqual() : nextLessThan();
            case '\n', '\r' -> nextNewLine();
            case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> nextNumber();
            case '-' -> {
                if (Character.isDigit(peek[1])) {
                    yield nextNumber();
                } else if (peek[1] == '>') {
                    yield nextArrow();
                } else {
                    yield nextMinus();
                }
            }
            case '+' -> {
                if (Character.isDigit(peek[1])) {
                    yield nextNumber();
                } else {
                    yield nextPlus();
                }
            }
            default -> {
                if (Character.isJavaIdentifierStart(peek[0])) {
                    yield nextIdentifierOrKeywordOrBoolean();
                } else if (Character.isWhitespace(peek[0])) {
                    do {
                        reader.next();
                    } while (Character.isWhitespace(reader.peek()));
                    yield nextToken();
                } else {
                    throw new LexerException("Unexpected character: '" + (char) peek[0] + "'");
                }
            }
        };
    }

    public Token nextComment() {
        expect('/');
        expect('/');
        int code;
        while ((code = reader.peek()) != '\n' && code != '\r' && code != -1) {
            reader.next();
        }

        reader.next();
        if (code == '\r' && reader.peek() == '\n') {
            reader.next();
        }

        return nextToken();
    }

    public Token.Identifier nextIdentifier() {
        var builder = new StringBuilder();
        builder.appendCodePoint(expect(Character::isJavaIdentifierStart, "Expected identifier"));
        while (Character.isJavaIdentifierPart(reader.peek())) {
            builder.appendCodePoint(reader.next());
        }
        var name = builder.toString();
        if (Token.Keyword.Type.fromName(name) != null || name.equals("true") || name.equals("false")) {
            throw new LexerException("Invalid identifier");
        }
        return new Token.Identifier(name);
    }

    public Token nextIdentifierOrKeywordOrBoolean() {
        var builder = new StringBuilder();
        builder.appendCodePoint(expect(Character::isJavaIdentifierStart, "Expected identifier"));
        while (Character.isJavaIdentifierPart(reader.peek())) {
            builder.appendCodePoint(reader.next());
        }
        var name = builder.toString();
        var keyword = Token.Keyword.Type.fromName(name);
        if (keyword != null) return new Token.Keyword(keyword);
        if (name.equals("true") || name.equals("false")) {
            return new Token.BooleanLiteral(Boolean.parseBoolean(name));
        }
        return new Token.Identifier(name);
    }

    public Token.StringLiteral nextStringLiteral() {
        expect('"');
        var builder = new StringBuilder();
        int code;
        while ((code = reader.peek()) != '"') {
            if (code == -1) {
                throw new LexerException("Unterminated string literal");
            }
            if (code == '\\') {
                reader.next();
                code = reader.next();
                switch (code) {
                    case 'n' -> builder.append('\n');
                    case 't' -> builder.append('\t');
                    case '"' -> builder.append('"');
                    case '\\' -> builder.append('\\');
                    case -1 -> throw new LexerException("Unterminated escape sequence");
                    default -> throw new LexerException("Unknown escape sequence: '\\" + (char) code + "'");
                }
            } else {
                builder.appendCodePoint(code);
                reader.next();
            }
        }
        expect('"');
        return new Token.StringLiteral(builder.toString());
    }

    public Token.NumberLiteral nextNumber() {
        var builder = new StringBuilder();
        int code = reader.peek();
        if (code == '-' || code == '+') {
            builder.appendCodePoint(reader.next());
            code = reader.peek();
        }
        if (Character.isDigit(code)) {
            do {
                builder.appendCodePoint(reader.next());
                code = reader.peek();
            } while (Character.isDigit(code));
        } else {
            throw new LexerException("Expected number");
        }
        return new Token.NumberLiteral(builder.toString());
    }

    public Token.BooleanLiteral nextBoolean() {
        var builder = new StringBuilder();
        builder.appendCodePoint(expect(Character::isJavaIdentifierStart, "Expected identifier"));
        while (Character.isJavaIdentifierPart(reader.peek())) {
            builder.appendCodePoint(reader.next());
        }
        var name = builder.toString();
        if (name.equals("true")) {
            return new Token.BooleanLiteral(true);
        } else if (name.equals("false")) {
            return new Token.BooleanLiteral(false);
        } else {
            throw new LexerException("Expected boolean literal");
        }
    }

    public Token.NewLine nextNewLine() {
        int code = reader.next();
        if (code == '\n') {
            return new Token.NewLine();
        } else if (code == '\r') {
            if (reader.peek() == '\n') reader.next();
            while (Character.isWhitespace(reader.peek())) reader.next();
            return new Token.NewLine();
        } else {
            throw new LexerException("Expected newline");
        }
    }

    public Token.Keyword nextKeyword() {
        var builder = new StringBuilder();
        builder.appendCodePoint(expect(Character::isJavaIdentifierStart, "Expected identifier"));
        while (Character.isJavaIdentifierPart(reader.peek())) {
            builder.appendCodePoint(reader.next());
        }
        var name = builder.toString();
        var keyword = Token.Keyword.Type.fromName(name);
        if (keyword == null) {
            throw new LexerException("Unknown keyword");
        }
        return new Token.Keyword(keyword);
    }

    public Token.Assign nextAssign() {
        expect('=');
        return new Token.Assign();
    }

    public Token.Comma nextComma() {
        expect(',');
        return new Token.Comma();
    }

    public Token.Colon nextColon() {
        expect(':');
        return new Token.Colon();
    }

    public Token.RightParenthesis nextRightParenthesis() {
        expect(')');
        return new Token.RightParenthesis();
    }

    public Token.LeftParenthesis nextLeftParenthesis() {
        expect('(');
        return new Token.LeftParenthesis();
    }

    public Token.RightBracket nextRightBracket() {
        expect(']');
        return new Token.RightBracket();
    }

    public Token.LeftBracket nextLeftBracket() {
        expect('[');
        return new Token.LeftBracket();
    }

    public Token.RightBrace nextRightBrace() {
        expect('}');
        return new Token.RightBrace();
    }

    public Token.LeftBrace nextLeftBrace() {
        expect('{');
        return new Token.LeftBrace();
    }

    public Token.Plus nextPlus() {
        expect('+');
        return new Token.Plus();
    }

    public Token.Minus nextMinus() {
        expect('-');
        return new Token.Minus();
    }

    public Token.Asterisk nextAsterisk() {
        expect('*');
        return new Token.Asterisk();
    }

    public Token.Slash nextSlash() {
        expect('/');
        return new Token.Slash();
    }

    public Token.Ampersand nextAmpersand() {
        expect('&');
        return new Token.Ampersand();
    }

    public Token.Pipe nextPipe() {
        expect('|');
        return new Token.Pipe();
    }

    public Token.Dot nextDot() {
        expect('.');
        return new Token.Dot();
    }

    public Token.GreaterThan nextGreaterThan() {
        expect('>');
        return new Token.GreaterThan();
    }

    public Token.LessThan nextLessThan() {
        expect('<');
        return new Token.LessThan();
    }

    public Token.GreaterThanOrEqual nextGreaterThanOrEqual() {
        expect('>');
        expect('=');
        return new Token.GreaterThanOrEqual();
    }

    public Token.LessThanOrEqual nextLessThanOrEqual() {
        expect('<');
        expect('=');
        return new Token.LessThanOrEqual();
    }

    public Token.Equal nextEqual() {
        expect('=');
        expect('=');
        return new Token.Equal();
    }

    public Token.NotEqual nextNotEqual() {
        expect('!');
        expect('=');
        return new Token.NotEqual();
    }

    public Token.Arrow nextArrow() {
        expect('-');
        expect('>');
        return new Token.Arrow();
    }
    
    private int expect(IntPredicate predicate, String message) {
        int codePoint = reader.next();
        if (!predicate.test(codePoint)) {
            throw new LexerException(message);
        }
        return codePoint;
    }

    private void expect(char expected) {
        expect((c) -> c == expected, "Expected '" + expected + "'");
    }

    private void expect(String expected) {
        for (char exp : expected.toCharArray()) {
            expect((c) -> c == exp, "Expected '" + expected + "'");
        }
    }

    private String errorMessage(String message) {
        var butGot = ", but got '" + (reader.lastCodePoint() == -1 ? "EOF" : (char) reader.lastCodePoint()) + "'";
        var at = " at (line " + reader.line() + ", column " + reader.column() + ")";
        return message.startsWith("Expected") ? message + butGot + at : message + at;
    }

    public class LexerException extends IllegalStateException {
        public LexerException(String message) {
            super(errorMessage(message));
        }
    }
}
