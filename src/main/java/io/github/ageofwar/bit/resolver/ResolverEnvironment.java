package io.github.ageofwar.bit.resolver;

import io.github.ageofwar.bit.types.Type;
import io.github.ageofwar.bit.types.TypeFunction;

import java.util.ArrayList;
import java.util.List;

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

    int variablesCount;
    int otherCount;

    public static ResolverEnvironment init() {
        var environment = new ResolverEnvironment(null);
        environment.declareValueType("print", function(none(), any()));
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
        variablesCount = parent != null ? parent.variablesCount : 0;
        otherCount = parent != null ? parent.otherCount : 0;
    }

    public int variables() {
        return variablesCount;
    }

    private void incrementVariablesCount() {
        if (parent != null) {
            parent.incrementVariablesCount();
        }
        variablesCount++;
    }

    private void incrementOtherCount() {
        if (parent != null) {
            parent.incrementOtherCount();
        }
        otherCount++;
    }

    public ResolvedBit.Symbol declareVariableType(String name, Type type) {
        var symbol = new ResolvedBit.Symbol(name, variablesCount);
        valueTypes.declare(name, new VariableType(symbol, type, true));
        incrementVariablesCount();
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
        var symbol = new ResolvedBit.Symbol(name, variablesCount);
        valueTypes.declare(name, new VariableType(symbol, type, false));
        incrementVariablesCount();
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
        var symbol = declareValueType(name, type);
        types.declare(name, new ValueType(symbol, type));
        incrementOtherCount();
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
        var value = new ResolvedBit.Symbol(name, otherCount);
        functionTypes.declare(name, new ValueTypeFunction(value, type));
        incrementOtherCount();
        return value;
    }

    public ValueTypeFunction getFunctionType(String name) {
        var value = functionTypes.resolve(name);
        if (value == null) {
            throw new RuntimeException("Function type not declared: " + name);
        }
        return value;
    }

    public ResolvedBit.Symbol declareConstructor(String name, Type type) {
        var symbol = new ResolvedBit.Symbol(name, variablesCount);
        constructors.declare(name, new ValueType(symbol, type));
        incrementVariablesCount();
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
        var symbol = new ResolvedBit.Symbol(name, variablesCount);
        var candidates = extensionTypes.getSymbols().computeIfAbsent(name, k -> new ArrayList<>());
        candidates.add(new ExtensionType(symbol, receiverType, type));
        incrementVariablesCount();
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
