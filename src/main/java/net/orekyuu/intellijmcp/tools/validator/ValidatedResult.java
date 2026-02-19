package net.orekyuu.intellijmcp.tools.validator;

import java.util.Map;
import java.util.function.Function;

public sealed interface ValidatedResult<T> {

    record Success<T>(T value) implements ValidatedResult<T> {}

    record Failure<T>(Map<String, String> errors) implements ValidatedResult<T> {}

    default <R> R fold(Function<T, R> onSuccess, Function<Map<String, String>, R> onFailure) {
        return switch (this) {
            case Success<T> s -> onSuccess.apply(s.value());
            case Failure<T> f -> onFailure.apply(f.errors());
        };
    }
}
