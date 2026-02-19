package net.orekyuu.intellijmcp.tools.validator;

import java.util.Optional;
import java.util.regex.Pattern;

public final class StringArgBuilder {
    private final String key;
    private final String description;
    private Pattern pattern;
    private String patternMessage;

    StringArgBuilder(String key, String description) {
        this.key = key;
        this.description = description;
    }

    public StringArgBuilder pattern(String regex, String message) {
        this.pattern = Pattern.compile(regex);
        this.patternMessage = message;
        return this;
    }

    public Arg<String> required() {
        Pattern p = this.pattern;
        String msg = this.patternMessage;
        return new Arg<>(key, description, true, null, Arg.SchemaType.STRING, args -> {
            Object value = args.get(key);
            if (value == null || value.toString().isBlank()) {
                return new Validated.Invalid<>(key, key + " is required");
            }
            String s = value.toString();
            if (p != null && !p.matcher(s).matches()) {
                return new Validated.Invalid<>(key, msg);
            }
            return new Validated.Valid<>(s);
        });
    }

    public Arg<Optional<String>> optional() {
        Pattern p = this.pattern;
        String msg = this.patternMessage;
        return new Arg<>(key, description, false, null, Arg.SchemaType.STRING, args -> {
            Object value = args.get(key);
            if (value == null || value.toString().isBlank()) {
                return new Validated.Valid<>(Optional.empty());
            }
            String s = value.toString();
            if (p != null && !p.matcher(s).matches()) {
                return new Validated.Invalid<>(key, msg);
            }
            return new Validated.Valid<>(Optional.of(s));
        });
    }

    public Arg<String> optional(String defaultValue) {
        Pattern p = this.pattern;
        String msg = this.patternMessage;
        String desc = description + " (default: " + defaultValue + ")";
        return new Arg<>(key, desc, false, defaultValue, Arg.SchemaType.STRING, args -> {
            Object value = args.get(key);
            if (value == null || value.toString().isBlank()) {
                return new Validated.Valid<>(defaultValue);
            }
            String s = value.toString();
            if (p != null && !p.matcher(s).matches()) {
                return new Validated.Invalid<>(key, msg);
            }
            return new Validated.Valid<>(s);
        });
    }
}
