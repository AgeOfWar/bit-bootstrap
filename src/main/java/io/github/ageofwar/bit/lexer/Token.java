package io.github.ageofwar.bit.lexer;

import java.util.HashMap;
import java.util.Map;

public sealed interface Token {
    record Identifier(String name) implements Token {}
    record RightParenthesis() implements Token {}
    record LeftParenthesis() implements Token {}
    record RightBracket() implements Token {}
    record LeftBracket() implements Token {}
    record RightBrace() implements Token {}
    record LeftBrace() implements Token {}
    record StringLiteral(String value) implements Token {}
    record BooleanLiteral(boolean value) implements Token {}
    record NumberLiteral(String value) implements Token {}
    record Assign() implements Token {}
    record Comma() implements Token {}
    record Colon() implements Token {}
    record NewLine() implements Token {}
    record Plus() implements Token {}
    record Minus() implements Token {}
    record Asterisk() implements Token {}
    record Slash() implements Token {}
    record Dot() implements Token {}
    record Ampersand() implements Token {}
    record Pipe() implements Token {}
    record GreaterThan() implements Token {}
    record LessThan() implements Token {}
    record GreaterThanOrEqual() implements Token {}
    record LessThanOrEqual() implements Token {}
    record Equal() implements Token {}
    record NotEqual() implements Token {}
    record Arrow() implements Token {}
    record Keyword(Type type) implements Token {
        public enum Type {
            IF("if"),
            ELSE("else"),
            WHILE("while"),
            FUNCTION("fun"),
            TYPE("type"),
            MATCH("match"),
            AS("as"),
            IS("is"),
            AND("and"),
            OR("or"),
            NOT("not"),
            CLASS("class"),
            NEW("new"),
            RETURN("return"),
            BREAK("break"),
            CONTINUE("continue"),
            PUBLIC("public"),
            PRIVATE("private"),
            VAR("var"),
            SET("s"),
            IMPLEMENT("impl"),
            FROM("from"),
            IMPORT("import");

            private static final Map<String, Type> byName = new HashMap<>();
            static {
                for (var type : Type.values()) {
                    byName.put(type.name, type);
                }
            }

            private final String name;

            public static Type fromName(String name) {
                return byName.get(name);
            }

            Type(String name) {
                this.name = name;
            }
        }
    }
}
