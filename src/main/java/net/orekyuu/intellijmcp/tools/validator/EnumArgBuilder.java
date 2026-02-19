package net.orekyuu.intellijmcp.tools.validator;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

public final class EnumArgBuilder<E extends Enum<E>> {
    private final String key;
    private final String description;
    private final Class<E> enumClass;
    private final String valuesDescription;

    EnumArgBuilder(String key, String description, Class<E> enumClass) {
        this.key = key;
        this.enumClass = enumClass;
        String values = Arrays.stream(enumClass.getEnumConstants())
                .map(Enum::name)
                .collect(Collectors.joining(", "));
        this.valuesDescription = "(one of: " + values + ")";
        this.description = description + " " + this.valuesDescription;
    }

    public Arg<E> required() {
        Class<E> cls = this.enumClass;
        String desc = this.description;
        return new Arg<>(key, desc, true, null, Arg.SchemaType.STRING, args -> {
            Object value = args.get(key);
            if (value == null || value.toString().isBlank()) {
                return new Validated.Invalid<>(key, key + " is required");
            }
            return parseEnum(cls, value.toString());
        });
    }

    public Arg<Optional<E>> optional() {
        Class<E> cls = this.enumClass;
        String desc = this.description;
        return new Arg<>(key, desc, false, null, Arg.SchemaType.STRING, args -> {
            Object value = args.get(key);
            if (value == null || value.toString().isBlank()) {
                return new Validated.Valid<>(Optional.empty());
            }
            Validated<E> parsed = parseEnum(cls, value.toString());
            if (parsed instanceof Validated.Invalid<E> invalid) {
                return new Validated.Invalid<>(invalid.key(), invalid.message());
            }
            return new Validated.Valid<>(Optional.of(((Validated.Valid<E>) parsed).value()));
        });
    }

    public Arg<E> optional(E defaultValue) {
        Class<E> cls = this.enumClass;
        String desc = this.description + " (default: " + defaultValue.name() + ")";
        return new Arg<>(key, desc, false, defaultValue.name(), Arg.SchemaType.STRING, args -> {
            Object value = args.get(key);
            if (value == null || value.toString().isBlank()) {
                return new Validated.Valid<>(defaultValue);
            }
            return parseEnum(cls, value.toString());
        });
    }

    private Validated<E> parseEnum(Class<E> cls, String value) {
        try {
            return new Validated.Valid<>(Enum.valueOf(cls, value));
        } catch (IllegalArgumentException e) {
            String allowed = Arrays.stream(cls.getEnumConstants())
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));
            return new Validated.Invalid<>(key, key + " must be one of: " + allowed);
        }
    }
}
