package io.github.ageofwar.bit.parser;

import io.github.ageofwar.bit.lexer.Token;
import io.github.ageofwar.bit.lexer.TokenStream;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

public class BufferedTokenStream {
    private final TokenStream stream;
    private final Queue<Token> peek;
    private final List<Token> history;

    private Token lastToken;

    public BufferedTokenStream(TokenStream stream) {
        this.stream = stream;
        peek = new ArrayDeque<>();
        history = new ArrayList<>();
    }

    public Token next() {
        lastToken = peek.isEmpty() ? stream.nextToken() : peek.poll();
        history.add(lastToken);
        return lastToken;
    }

    public Token peek() {
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

    public void checkpoint() {
        history.clear();
    }

    public void rollback() {
        var oldPeek = peek.toArray(Token[]::new);
        peek.clear();
        peek.addAll(history);
        peek.addAll(List.of(oldPeek));
        history.clear();
    }

    public Token lastToken() {
        return lastToken;
    }
}
