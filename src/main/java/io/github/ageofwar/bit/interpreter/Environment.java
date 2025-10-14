package io.github.ageofwar.bit.interpreter;

import io.github.ageofwar.bit.resolver.ResolvedBit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Function;

import static io.github.ageofwar.bit.types.Types.*;

public class Environment {
    private final Object[] variables;

    public static Environment init(int variablesSize) {
        var environment = new Environment(variablesSize);
        var i = 0;
        environment.assignVariable(new ResolvedBit.Symbol("__read_stdin", i++), (Function<List<Object>, Object>) args -> {;
            try {
                var c = System.in.read();
                if (c == -1) return "";
                return String.valueOf((char) c);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        environment.assignVariable(new ResolvedBit.Symbol("__write_stdout", i++), (Function<List<Object>, Object>) args -> {
            System.out.print(args.getFirst());
            return none();
        });
        environment.assignVariable(new ResolvedBit.Symbol("__file_open_read", i++), (Function<List<Object>, Object>) args -> {
            try {
                return Files.newBufferedReader(Paths.get((String) args.getFirst()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        environment.assignVariable(new ResolvedBit.Symbol("__file_open_write", i++), (Function<List<Object>, Object>) args -> {
            try {
                return Files.newBufferedWriter(Paths.get((String) args.getFirst()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        environment.assignVariable(new ResolvedBit.Symbol("__file_close", i++), (Function<List<Object>, Object>) args -> {
            try {
                ((AutoCloseable) args.getFirst()).close();
                return none();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        environment.assignVariable(new ResolvedBit.Symbol("__file_read", i++), (Function<List<Object>, Object>) args -> {
            try {
                var reader = (java.io.BufferedReader) args.getFirst();
                int c = reader.read();
                if (c == -1) return "";
                return String.valueOf((char) c);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        environment.assignVariable(new ResolvedBit.Symbol("__file_write", i++), (Function<List<Object>, Object>) args -> {
            try {
                var writer = (java.io.BufferedWriter) args.getFirst();
                writer.write((String) args.get(1));
                writer.flush();
                return none();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        environment.assignVariable(new ResolvedBit.Symbol("toString", i++), (Function<List<Object>, Object>) args -> {
            return args.getFirst().toString();
        });
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
