package io.github.ageofwar.bit.parser;

import io.github.ageofwar.bit.lexer.Token;

import java.util.function.Predicate;

public class Parsers {
    private Parsers() {
    }

    public static Token expect(BufferedTokenStream tokens, Predicate<Token> predicate, String message) {
        var token = tokens.next();
        if (!predicate.test(token)) {
            throw new ParserException(errorMessage(tokens, message));
        }
        return token;
    }

    @SuppressWarnings("unchecked")
    public static  <T extends Token> T expect(BufferedTokenStream tokens, Class<T> tokenType) {
        return (T) expect(tokens, tokenType::isInstance, "Expected " + tokenType.getSimpleName());
    }

    public static void expect(BufferedTokenStream tokens, Token.Keyword.Type keyword) {
        var token = expect(tokens, Token.Keyword.class);
        if (token.type() != keyword) {
            throw new ParserException("Expected keyword '" + keyword + "'");
        }
    }

    private static boolean matches(BufferedTokenStream tokens, Predicate<Token> predicate) {
        var token = tokens.peek();
        return token != null && predicate.test(token);
    }

    public static boolean matches(BufferedTokenStream tokens, Class<? extends Token> tokenType) {
        return matches(tokens, tokenType::isInstance);
    }

    public static boolean matches(BufferedTokenStream tokens, Token.Keyword.Type keyword) {
        return matches(tokens, token -> token instanceof Token.Keyword && ((Token.Keyword) token).type() == keyword);
    }

    private static String errorMessage(BufferedTokenStream tokens, String message) {
        var butGot = ", but got " + tokens.lastToken();
        return message.startsWith("Expected") ? message + butGot : message;
    }
}
