package net.orekyuu.intellijmcp.tools.validator;

import java.util.List;

public final class StringArrayArgBuilder {
    private final String key;
    private final String description;

    StringArrayArgBuilder(String key, String description) {
        this.key = key;
        this.description = description;
    }

    @SuppressWarnings("unchecked")
    public Arg<List<String>> optional() {
        return new Arg<>(key, description, false, List.of(), Arg.SchemaType.STRING_ARRAY, args -> {
            Object value = args.get(key);
            if (value == null) {
                return new Validated.Valid<>(List.of());
            }
            if (value instanceof List<?> list) {
                List<String> result = list.stream()
                        .filter(item -> item instanceof String)
                        .map(item -> (String) item)
                        .toList();
                return new Validated.Valid<>(result);
            }
            return new Validated.Invalid<>(key, key + " must be an array of strings");
        });
    }
}
