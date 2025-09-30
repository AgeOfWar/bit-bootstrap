package io.github.ageofwar.bit.packages;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FilePackageResolver implements PackageResolver {
    @Override
    public Reader resolvePackage(String[] path) {
        try {
            var filePath = packageToPath(path);
            return Files.newBufferedReader(filePath);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path packageToPath(String[] path) {
        return Path.of(String.join("/", path) + ".bit");
    }
}
