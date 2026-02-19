package net.orekyuu.intellijmcp.tools.validator;

import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;

public final class IntegerArgBuilder {
    private final String key;
    private final String description;
    private Integer min;
    private Integer max;

    IntegerArgBuilder(String key, String description) {
        this.key = key;
        this.description = description;
    }

    public IntegerArgBuilder min(int n) {
        this.min = n;
        return this;
    }

    public IntegerArgBuilder max(int n) {
        this.max = n;
        return this;
    }

    private Validated<Integer> validateRange(int value) {
        if (min != null && value < min) {
            return new Validated.Invalid<>(key, key + " must be at least " + min);
        }
        if (max != null && value > max) {
            return new Validated.Invalid<>(key, key + " must be at most " + max);
        }
        return new Validated.Valid<>(value);
    }

    private Validated.Invalid<Integer> typeError() {
        return new Validated.Invalid<>(key, key + " must be an integer");
    }

    public Arg<Integer> required() {
        return new Arg<>(key, buildDescription(null), true, null, Arg.SchemaType.INTEGER, args -> {
            Object value = args.get(key);
            if (value == null) {
                return new Validated.Invalid<>(key, key + " is required");
            }
            if (value instanceof Number n) {
                return validateRange(n.intValue());
            }
            return typeError();
        });
    }

    public Arg<Optional<Integer>> optional() {
        return new Arg<>(key, buildDescription(null), false, null, Arg.SchemaType.INTEGER, args -> {
            Object value = args.get(key);
            if (value == null) {
                return new Validated.Valid<>(Optional.empty());
            }
            if (value instanceof Number n) {
                Validated<Integer> rangeCheck = validateRange(n.intValue());
                return switch (rangeCheck) {
                    case Validated.Valid<Integer> v -> new Validated.Valid<>(Optional.of(v.value()));
                    case Validated.Invalid<Integer> inv -> new Validated.Invalid<>(inv.key(), inv.message());
                };
            }
            return new Validated.Invalid<>(key, key + " must be an integer");
        });
    }

    public Arg<Integer> optional(int defaultValue) {
        return new Arg<>(key, buildDescription(defaultValue), false, defaultValue, Arg.SchemaType.INTEGER, args -> {
            Object value = args.get(key);
            if (value == null) {
                return new Validated.Valid<>(defaultValue);
            }
            if (value instanceof Number n) {
                return validateRange(n.intValue());
            }
            return typeError();
        });
    }

    private String buildDescription(Integer defaultValue) {
        var builder = new StringBuilder(description);
        var list = new ArrayList<String>();
        if (defaultValue != null) {
            list.add("default: " + defaultValue);
        }
        if (min != null) {
            list.add("min: " + min);
        }
        if (max != null) {
            list.add("max: " + max);
        }
        if (!list.isEmpty()) {
            builder.append(list.stream().collect(Collectors.joining(", ", "(", ")")));
        }
        return builder.toString();
    }
}
