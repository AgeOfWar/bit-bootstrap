package io.github.ageofwar.bit.parser;

import io.github.ageofwar.bit.lexer.Token;
import io.github.ageofwar.bit.lexer.TokenStream;

import java.util.ArrayDeque;
import java.util.Queue;

public class BufferedTokenStream {
    private final TokenStream stream;
    private final Queue<Token> peek;

    private Token lastToken;

    public BufferedTokenStream(TokenStream stream) {
        this.stream = stream;
        peek = new ArrayDeque<>();
    }

    public Token next() {
        lastToken = peek.isEmpty() ? stream.nextToken() : peek.poll();
        return lastToken;
    }

    public  Token peek() {
        if (!peek.isEmpty()) {
            return peek.peek();
        }
        var token = stream.nextToken();
        if (token != null) peek.add(token);
        return token;
    }

    public Token[] peek(int count) {
        while (peek.size() < count) {
            var token = stream.nextToken();
            if (token == null) {
                break;
            }
            peek.add(token);
        }
        return peek.toArray(new Token[count]);
    }

    public Token lastToken() {
        return lastToken;
    }
}
