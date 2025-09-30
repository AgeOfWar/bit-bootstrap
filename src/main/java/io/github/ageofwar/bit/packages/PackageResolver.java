package io.github.ageofwar.bit.packages;

import java.io.Reader;

public interface PackageResolver {
    Reader resolvePackage(String[] path);
}
