package io.github.ageofwar;

import io.github.ageofwar.bit.interpreter.Interpreter;
import io.github.ageofwar.bit.packages.FilePackageResolver;
import io.github.ageofwar.bit.parser.Parser;
import io.github.ageofwar.bit.print.Printer;
import io.github.ageofwar.bit.resolver.Resolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws IOException {
        try (var reader = Files.newBufferedReader(Paths.get("test.bit"))) {
            var parser = new Parser(reader);
            var program = parser.nextProgram();
            var printer = new Printer();
            // System.out.println(printer.print(program));
            var resolver = new Resolver(new FilePackageResolver());
            var resolvedProgram = resolver.resolve(program);
            var interpreter = new Interpreter(new FilePackageResolver());
            interpreter.interpret(resolvedProgram, "main");
        }
    }
}