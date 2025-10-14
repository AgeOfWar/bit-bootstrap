package io.github.ageofwar.bit.resolver;

import io.github.ageofwar.bit.types.Type;
import io.github.ageofwar.bit.types.TypeFunction;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.ageofwar.bit.types.Types.*;
import static io.github.ageofwar.bit.types.Types._boolean;
import static io.github.ageofwar.bit.types.Types.any;
import static io.github.ageofwar.bit.types.Types.integer;
import static io.github.ageofwar.bit.types.Types.never;
import static io.github.ageofwar.bit.types.Types.none;
import static io.github.ageofwar.bit.types.Types.string;

public class ResolverEnvironment {
    private final ResolverEnvironment parent;
    private final ScopedTable<VariableType> valueTypes;
    private final ScopedTable<ValueType> types;
    private final ScopedTable<ValueTypeFunction> functionTypes;
    private final ScopedTable<ValueType> constructors;
    private final ScopedTable<List<ExtensionType>> extensionTypes;

    private AtomicInteger variablesCount;
    private int typesCount;

    public static ResolverEnvironment init() {
        var environment = new ResolverEnvironment(null);
        environment.declareValueType("__read_stdin", function(string()));
        environment.declareValueType("__write_stdout", function(none(), string()));
        environment.declareValueType("__file_open_read", function(any(), string()));
        environment.declareValueType("__file_open_write", function(any(), string()));
        environment.declareValueType("__file_close", function(none(), any()));
        environment.declareValueType("__file_read", function(string(), any()));
        environment.declareValueType("__file_write", function(none(), any(), string()));

        environment.declareExtensionType("toString", integer(), function(string()));

        environment.declareType("Any", any());
        environment.declareType("Never", never());
        environment.declareType("Integer", integer());
        environment.declareType("Boolean", _boolean());
        environment.declareType("String", string());
        environment.declareType("None", none());

        return environment;
    }

    public ResolverEnvironment(ResolverEnvironment parent) {
        this.parent = parent;
        this.valueTypes = new ScopedTable<>(parent != null ? parent.valueTypes : null);
        this.types = new ScopedTable<>(parent != null ? parent.types : null);
        this.functionTypes = new ScopedTable<>(parent != null ? parent.functionTypes : null);
        this.extensionTypes = new ScopedTable<>(parent != null ? parent.extensionTypes : null);
        this.constructors = new ScopedTable<>(parent != null ? parent.constructors : null);
        variablesCount = parent != null ? parent.variablesCount : new AtomicInteger();
        typesCount = parent != null ? parent.typesCount : 0;
    }

    public int variables() {
        return variablesCount.get();
    }

    public void incrementTypesCount() {
        if (parent != null) {
            parent.incrementTypesCount();
        }
        typesCount++;
    }

    public ResolvedBit.Symbol declareVariableType(String name, Type type) {
        var symbol = new ResolvedBit.Symbol(name, variablesCount.getAndIncrement());
        valueTypes.declare(name, new VariableType(symbol, type, true));
        return symbol;
    }

    public ValueType getVariableType(String name) {
        var value = valueTypes.resolve(name);
        if (value == null) {
            throw new RuntimeException("Variable not declared: " + name);
        }
        if (!value.variable()) {
            throw new RuntimeException("Not a variable: " + name);
        }
        return new ValueType(value.symbol, value.type);
    }

    public ResolvedBit.Symbol declareValueType(String name, Type type) {
        var alreadyDeclared = valueTypes.resolve(name);
        if (alreadyDeclared != null && alreadyDeclared.variable()) {
            throw new RuntimeException("Variable with same name already declared: " + name);
        }

        var symbol = new ResolvedBit.Symbol(name, variablesCount.getAndIncrement());
        valueTypes.declare(name, new VariableType(symbol, type, false));
        return symbol;
    }

    public ValueType getValueType(String name) {
        var value = valueTypes.resolve(name);
        if (value == null) {
            throw new RuntimeException("Variable not declared: " + name);
        }
        return new ValueType(value.symbol, value.type);
    }

    public ResolvedBit.Symbol declareType(String name, Type type) {
        var alreadyDeclared = types.resolve(name);
        if (alreadyDeclared != null) {
            throw new RuntimeException("Type already declared: " + name);
        }

        var symbol = new ResolvedBit.Symbol(name, typesCount);
        types.declare(name, new ValueType(symbol, type));
        incrementTypesCount();
        return symbol;
    }

    public ValueType getType(String name) {
        var value = types.resolve(name);
        if (value == null) {
            throw new RuntimeException("Type not declared: " + name);
        }
        return value;
    }

    public ResolvedBit.Symbol declareFunctionType(String name, TypeFunction type) {
        var alreadyDeclared = functionTypes.resolve(name);
        if (alreadyDeclared != null) {
            throw new RuntimeException("Function type already declared: " + name);
        }

        var symbol = new ResolvedBit.Symbol(name, typesCount);
        functionTypes.declare(name, new ValueTypeFunction(symbol, type));
        incrementTypesCount();
        return symbol;
    }

    public ValueTypeFunction getFunctionType(String name) {
        var value = functionTypes.resolve(name);
        if (value == null) {
            throw new RuntimeException("Function type not declared: " + name);
        }
        return value;
    }

    public ResolvedBit.Symbol declareConstructor(String name, Type type) {
        var symbol = new ResolvedBit.Symbol(name, variablesCount.getAndIncrement());
        constructors.declare(name, new ValueType(symbol, type));
        return symbol;
    }

    public ValueType getConstructor(String name) {
        var value = constructors.resolve(name);
        if (value == null) {
            throw new RuntimeException("Constructor not declared: " + name);
        }
        return value;
    }

    public ResolvedBit.Symbol declareExtensionType(String name, Type receiverType, Type type) {
        var symbol = new ResolvedBit.Symbol(name, variablesCount.getAndIncrement());
        var candidates = extensionTypes.getSymbols().computeIfAbsent(name, k -> new ArrayList<>());
        candidates.add(new ExtensionType(symbol, receiverType, type));
        return symbol;
    }

    public List<ExtensionType> getExtensionTypes(String name) {
        return extensionTypes.resolveAll(name).stream().flatMap(List::stream).toList();
    }

    public void refineValueType(ResolvedBit.Symbol oldSymbol, Type type) {
        var existingType = valueTypes.resolve(oldSymbol.name());
        var newType = intersection(existingType.type(), type);
        valueTypes.declare(oldSymbol.name(), new VariableType(existingType.symbol, newType, false));
    }

    public void mergeFrom(ResolverEnvironment other) {
        for (var valueType : other.valueTypes.getSymbols().entrySet()) {
            var existing = this.valueTypes.getSymbols().get(valueType.getKey());
            if (existing != null) throw new RuntimeException("Duplicate value type: " + valueType.getKey());
            valueTypes.declare(valueType.getKey(), valueType.getValue());
        }
        for (var type : other.types.getSymbols().entrySet()) {
            var existing = this.types.getSymbols().get(type.getKey());
            if (existing != null) throw new RuntimeException("Duplicate type: " + type.getKey());
            types.declare(type.getKey(), type.getValue());
        }
        for (var functionType : other.functionTypes.getSymbols().entrySet()) {
            var existing = this.functionTypes.getSymbols().get(functionType.getKey());
            if (existing != null) throw new RuntimeException("Duplicate function type: " + functionType.getKey());
            functionTypes.declare(functionType.getKey(), functionType.getValue());
        }
        for (var constructor : other.constructors.getSymbols().entrySet()) {
            var existing = this.constructors.getSymbols().get(constructor.getKey());
            if (existing != null) throw new RuntimeException("Duplicate constructor: " + constructor.getKey());
            constructors.declare(constructor.getKey(), constructor.getValue());
        }
        for (var extensionType : other.extensionTypes.getSymbols().entrySet()) {
            var existing = this.extensionTypes.getSymbols().get(extensionType.getKey());
            if (existing != null) throw new RuntimeException("Duplicate extension function: " + extensionType.getKey());
            extensionTypes.declare(extensionType.getKey(), extensionType.getValue());
        }
    }

    public record ValueTypeFunction(ResolvedBit.Symbol symbol, TypeFunction type) {

    }
    public record ValueType(ResolvedBit.Symbol symbol, Type type) {

    }
    public record VariableType(ResolvedBit.Symbol symbol, Type type, boolean variable) {

    }
    public record ExtensionType(ResolvedBit.Symbol symbol, Type receiverType, Type type) {

    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ResolverEnvironment:\n");
        sb.append("Value Types: ");
        sb.append(valueTypes);
        sb.append("\nTypes: ");
        sb.append(types);
        sb.append("\nFunction Types: ");
        sb.append(functionTypes);
        sb.append("\nConstructors: ");
        sb.append(constructors);
        sb.append("\nExtension Types: ");
        sb.append(extensionTypes);
        return sb.toString();
    }
}
