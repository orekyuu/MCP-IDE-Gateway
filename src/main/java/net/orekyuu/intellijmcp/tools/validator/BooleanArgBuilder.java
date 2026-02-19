package net.orekyuu.intellijmcp.tools.validator;

import java.util.Optional;

public final class BooleanArgBuilder {
    private final String key;
    private final String description;

    BooleanArgBuilder(String key, String description) {
        this.key = key;
        this.description = description;
    }

    public Arg<Boolean> required() {
        return new Arg<>(key, description, true, null, Arg.SchemaType.BOOLEAN, args -> {
            Object value = args.get(key);
            if (value == null) {
                return new Validated.Invalid<>(key, key + " is required");
            }
            if (value instanceof Boolean b) {
                return new Validated.Valid<>(b);
            }
            return new Validated.Invalid<>(key, key + " must be a boolean");
        });
    }

    public Arg<Optional<Boolean>> optional() {
        return new Arg<>(key, description, false, null, Arg.SchemaType.BOOLEAN, args -> {
            Object value = args.get(key);
            if (value == null) {
                return new Validated.Valid<>(Optional.empty());
            }
            if (value instanceof Boolean b) {
                return new Validated.Valid<>(Optional.of(b));
            }
            return new Validated.Invalid<>(key, key + " must be a boolean");
        });
    }

    public Arg<Boolean> optional(boolean defaultValue) {
        String desc = description + " (default: " + defaultValue + ")";
        return new Arg<>(key, desc, false, defaultValue, Arg.SchemaType.BOOLEAN, args -> {
            Object value = args.get(key);
            if (value == null) {
                return new Validated.Valid<>(defaultValue);
            }
            if (value instanceof Boolean b) {
                return new Validated.Valid<>(b);
            }
            return new Validated.Invalid<>(key, key + " must be a boolean");
        });
    }
}
