package io.github.ageofwar.bit.types;

import java.util.function.Function;

public record TypeFunction(Function<Type[], Type> function) {
}
