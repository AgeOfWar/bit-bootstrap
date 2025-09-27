package io.github.ageofwar.bit.lexer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;

public class BufferedCharStream {
    private final BufferedReader reader;
    private int lastCodePoint = -1;
    private int line = 1;
    private int column = 1;

    public BufferedCharStream(Reader reader) {
        this.reader = reader instanceof BufferedReader ? (BufferedReader) reader : new BufferedReader(reader);
    }

    public int peek() {
        try {
            reader.mark(1);
            int codePoint = reader.read();
            reader.reset();
            return codePoint;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public int[] peek(int size) {
        var result = new int[size];
        try {
            reader.mark(size);
            for (int i = 0; i < size; i++) {
                result[i] = reader.read();
            }
            reader.reset();
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public int next() {
        try {
            int codePoint = reader.read();
            updatePosition(codePoint);
            return codePoint;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public int line() {
        return line;
    }

    public int column() {
        return column;
    }

    public int lastCodePoint() {
        return lastCodePoint;
    }

    private void updatePosition(int codePoint) {
        if (codePoint == '\r' || (codePoint == '\n' && lastCodePoint != '\r')) {
            line++;
            column = 1;
        } else if (lastCodePoint != -1 && codePoint != '\n') {
            column++;
        }
        lastCodePoint = codePoint;
    }
}
