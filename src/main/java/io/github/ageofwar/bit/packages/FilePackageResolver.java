package io.github.ageofwar.bit.packages;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FilePackageResolver implements PackageResolver {
    private static final String STDLIB_PATH = System.getenv("BIT_PATH");

    @Override
    public Reader resolvePackage(String[] path) {
        try {
            var filePath = packageToPath(path);
            return Files.newBufferedReader(filePath);
        } catch (IOException e) {
            try {
                if (STDLIB_PATH == null) throw new UncheckedIOException("Missing BIT_PATH environment variable", e);
                var stdlibPath = Path.of(STDLIB_PATH, "stdlib").resolve(packageToPath(path));
                return Files.newBufferedReader(stdlibPath);
            } catch (IOException ex) {
                throw new UncheckedIOException("Failed to resolve package: " + String.join(".", path), ex);
            }
        }
    }

    private Path packageToPath(String[] path) {
        return Path.of(String.join("/", path) + ".bit");
    }
}
