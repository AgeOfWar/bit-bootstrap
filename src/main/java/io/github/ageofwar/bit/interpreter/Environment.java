package io.github.ageofwar.bit.interpreter;

import io.github.ageofwar.bit.resolver.ResolvedBit;

import java.util.List;
import java.util.function.Function;

import static io.github.ageofwar.bit.types.Types.*;

public class Environment {
    private final Object[] variables;

    public static Environment init(int variablesSize) {
        var environment = new Environment(variablesSize);
        environment.assignVariable(new ResolvedBit.Symbol("print", 0), (Function<List<Object>, Object>) args -> {
            System.out.println(args.getFirst());
            return none();
        });
        environment.assignVariable(new ResolvedBit.Symbol("Any", 1), any());
        environment.assignVariable(new ResolvedBit.Symbol("Never", 2), never());
        environment.assignVariable(new ResolvedBit.Symbol("Integer", 3), integer());
        environment.assignVariable(new ResolvedBit.Symbol("Boolean", 4), _boolean());
        environment.assignVariable(new ResolvedBit.Symbol("String", 5), string());
        environment.assignVariable(new ResolvedBit.Symbol("None", 6), none());
        return environment;
    }

    public Environment(int variablesSize) {
        this.variables = new Object[variablesSize];
    }

    public void assignVariable(ResolvedBit.Symbol name, Object value) {
        variables[name.id()] = value;
    }

    public Object get(ResolvedBit.Symbol name) {
        return variables[name.id()];
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Environment:\n");
        sb.append("Variables:\n");
        for (int i = 0; i < variables.length; i++) {
            sb.append("  ").append(i).append(": ").append(variables[i]).append("\n");
        }
        return sb.toString();
    }
}
