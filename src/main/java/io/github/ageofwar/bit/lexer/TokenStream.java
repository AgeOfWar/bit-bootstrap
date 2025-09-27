package io.github.ageofwar.bit.lexer;

@FunctionalInterface
public interface TokenStream {
    Token nextToken();
}
