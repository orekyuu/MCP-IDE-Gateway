package net.orekyuu.intellijmcp.tools.validator;

import com.intellij.openapi.project.Project;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static net.orekyuu.intellijmcp.tools.validator.Validated.Invalid;
import static net.orekyuu.intellijmcp.tools.validator.Validated.Valid;

public record Arg<T>(
        String key,
        String description,
        boolean required,
        Object defaultValue,
        SchemaType schemaType,
        Function<Map<String, Object>, Validated<T>> extractor
) {

    public enum SchemaType {
        STRING("string"),
        INTEGER("integer"),
        BOOLEAN("boolean"),
        STRING_ARRAY("array");

        private final String jsonType;

        SchemaType(String jsonType) {
            this.jsonType = jsonType;
        }

        public String jsonType() {
            return jsonType;
        }
    }

    public Validated<T> extract(Map<String, Object> arguments) {
        return extractor.apply(arguments);
    }

    public static StringArgBuilder string(String key, String description) {
        return new StringArgBuilder(key, description);
    }

    public static IntegerArgBuilder integer(String key, String description) {
        return new IntegerArgBuilder(key, description);
    }

    public static BooleanArgBuilder bool(String key, String description) {
        return new BooleanArgBuilder(key, description);
    }

    public static StringArrayArgBuilder stringArray(String key, String description) {
        return new StringArrayArgBuilder(key, description);
    }

    public static <E extends Enum<E>> EnumArgBuilder<E> enumArg(String key, String description, Class<E> enumClass) {
        return new EnumArgBuilder<>(key, description, enumClass);
    }

    public static Arg<Project> project() {
        String key = "projectPath";
        return new Arg<>(key, "Absolute path to the project root directory. Get this value from list_projects if unknown.", true, null, SchemaType.STRING, args -> {
            Object value = args.get(key);
            if (value == null || value.toString().isBlank()) {
                return new Validated.Invalid<>(key, key + " is required");
            }
            String path = ProjectLocation.normalizePath(value.toString());
            ProjectLocation location = new ProjectLocation(path);
            Optional<Project> projectOpt = location.resolve();
            if (projectOpt.isEmpty()) {
                return new Validated.Invalid<>(key, "Project not found at path: " + path);
            }
            return new Validated.Valid<>(projectOpt.get());
        });
    }

    public static Arg<ProjectLocation> projectLocation(String key, String description) {
        return new Arg<>(key, description, true, null, SchemaType.STRING, args -> {
            Object value = args.get(key);
            if (value == null || value.toString().isBlank()) {
                return new Validated.Invalid<>(key, key + " is required");
            }
            String path = ProjectLocation.normalizePath(value.toString());
            return new Validated.Valid<>(new ProjectLocation(path));
        });
    }

    public static Arg<ProjectRelativePath> projectRelativePath(String key, String description) {
        return new Arg<>(key, description, true, null, SchemaType.STRING, args -> {
            Object value = args.get(key);
            if (value == null || value.toString().isBlank()) {
                return new Invalid<>(key, key + " is required");
            }
            String path = value.toString();
            if (ProjectRelativePath.isTraversal(path)) {
                return new Invalid<>(key, "Path is outside the project directory");
            }
            return new Valid<>(new ProjectRelativePath(path));
        });
    }

    public static Arg<Path> absolutePath(String key, String description) {
        return new Arg<>(key, description, true, null, SchemaType.STRING, args -> {
            Object value = args.get(key);
            if (value == null || value.toString().isBlank()) {
                return new Invalid<>(key, key + " is required");
            }
            var path = Path.of(value.toString());
            if (!path.isAbsolute()) {
                return new Invalid<>(key, key + " is not absolute");
            }
            return new Valid<>(path);
        });
    }

    public static Arg<Optional<ProjectRelativePath>> optionalProjectRelativePath(String key, String description) {
        return new Arg<>(key, description, false, null, SchemaType.STRING, args -> {
            Object value = args.get(key);
            if (value == null || value.toString().isBlank()) {
                return new Valid<>(Optional.empty());
            }
            String path = value.toString();
            if (ProjectRelativePath.isTraversal(path)) {
                return new Invalid<>(key, "Path is outside the project directory");
            }
            return new Valid<>(Optional.of(new ProjectRelativePath(path)));
        });
    }
}
