package net.orekyuu.intellijmcp.tools.validator;

import java.util.function.BiFunction;
import java.util.function.Function;

public sealed interface Validated<T> {

    record Valid<T>(T value) implements Validated<T> {}

    record Invalid<T>(String key, String message) implements Validated<T> {}

    default <R> R fold(Function<T, R> onValid, BiFunction<String, String, R> onInvalid) {
        return switch (this) {
            case Valid<T> v -> onValid.apply(v.value());
            case Invalid<T> inv -> onInvalid.apply(inv.key(), inv.message());
        };
    }
}
