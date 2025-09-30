package io.github.ageofwar.bit.resolver;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScopedTable<T> {
    private ScopedTable<T> parent;
    private final Map<String, T> symbols;

    public ScopedTable(ScopedTable<T> parent) {
        this.parent = parent;
        this.symbols = new HashMap<>();
    }

    public ScopedTable() {
        this(null);
    }

    public T declare(String name, T symbol) {
        if (symbols.containsKey(name)) {
            throw new RuntimeException("Variable already declared: " + name);
        }
        symbols.put(name, symbol);
        return symbol;
    }

    public T resolve(String name) {
        if (symbols.containsKey(name)) {
            return symbols.get(name);
        } else if (parent != null) {
            return parent.resolve(name);
        } else {
            return null;
        }
    }

    public List<T> resolveAll(String name) {
        var results = new java.util.ArrayList<T>();
        if (symbols.containsKey(name)) {
            results.add(symbols.get(name));
        }
        if (parent != null) {
            results.addAll(parent.resolveAll(name));
        }
        return results;
    }

    public void skipParent() {
        if (parent == null) {
            throw new IllegalStateException("No parent to skip");
        }

        this.parent = parent.parent;
    }

    public void setParent(ScopedTable<T> parent) {
        this.parent = parent;
    }

    public Map<String, T> getSymbols() {
        return symbols;
    }

    @Override
    public String toString() {
        return "ScopedTable{" +
                "symbols=" + symbols +
                ", parent=" + parent +
                '}';
    }
}
