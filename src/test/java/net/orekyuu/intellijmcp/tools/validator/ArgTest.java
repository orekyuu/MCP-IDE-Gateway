package net.orekyuu.intellijmcp.tools.validator;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ArgTest {

    @Nested
    class StringArgTest {

        @Test
        void requiredPresent() {
            Arg<String> arg = Arg.string("name", "desc").required();
            Validated<String> result = arg.extract(Map.of("name", "hello"));
            assertThat(result).isInstanceOf(Validated.Valid.class);
            assertThat(((Validated.Valid<String>) result).value()).isEqualTo("hello");
        }

        @Test
        void requiredMissing() {
            Arg<String> arg = Arg.string("name", "desc").required();
            Validated<String> result = arg.extract(Map.of());
            assertThat(result).isInstanceOf(Validated.Invalid.class);
            assertThat(((Validated.Invalid<String>) result).message()).contains("name");
        }

        @Test
        void requiredBlank() {
            Arg<String> arg = Arg.string("name", "desc").required();
            Validated<String> result = arg.extract(Map.of("name", "  "));
            assertThat(result).isInstanceOf(Validated.Invalid.class);
        }

        @Test
        void requiredNull() {
            Arg<String> arg = Arg.string("name", "desc").required();
            Map<String, Object> args = new HashMap<>();
            args.put("name", null);
            Validated<String> result = arg.extract(args);
            assertThat(result).isInstanceOf(Validated.Invalid.class);
        }

        @Test
        void optionalPresent() {
            Arg<Optional<String>> arg = Arg.string("name", "desc").optional();
            Validated<Optional<String>> result = arg.extract(Map.of("name", "hello"));
            assertThat(result).isInstanceOf(Validated.Valid.class);
            assertThat(((Validated.Valid<Optional<String>>) result).value()).hasValue("hello");
        }

        @Test
        void optionalMissing() {
            Arg<Optional<String>> arg = Arg.string("name", "desc").optional();
            Validated<Optional<String>> result = arg.extract(Map.of());
            assertThat(result).isInstanceOf(Validated.Valid.class);
            assertThat(((Validated.Valid<Optional<String>>) result).value()).isEmpty();
        }

        @Test
        void optionalWithDefault() {
            Arg<String> arg = Arg.string("name", "desc").optional("fallback");
            Validated<String> result = arg.extract(Map.of());
            assertThat(result).isInstanceOf(Validated.Valid.class);
            assertThat(((Validated.Valid<String>) result).value()).isEqualTo("fallback");
        }

        @Test
        void patternValid() {
            Arg<String> arg = Arg.string("name", "desc").pattern("^[a-z]+$", "must be lowercase").required();
            Validated<String> result = arg.extract(Map.of("name", "hello"));
            assertThat(result).isInstanceOf(Validated.Valid.class);
        }

        @Test
        void patternInvalid() {
            Arg<String> arg = Arg.string("name", "desc").pattern("^[a-z]+$", "must be lowercase").required();
            Validated<String> result = arg.extract(Map.of("name", "HELLO"));
            assertThat(result).isInstanceOf(Validated.Invalid.class);
            assertThat(((Validated.Invalid<String>) result).message()).isEqualTo("must be lowercase");
        }

        @Test
        void schemaProperties() {
            Arg<String> arg = Arg.string("name", "desc").required();
            assertThat(arg.key()).isEqualTo("name");
            assertThat(arg.description()).isEqualTo("desc");
            assertThat(arg.required()).isTrue();
            assertThat(arg.schemaType()).isEqualTo(Arg.SchemaType.STRING);
        }
    }

    @Nested
    class IntegerArgTest {

        @Test
        void requiredPresent() {
            Arg<Integer> arg = Arg.integer("count", "desc").required();
            Validated<Integer> result = arg.extract(Map.of("count", 42));
            assertThat(result).isInstanceOf(Validated.Valid.class);
            assertThat(((Validated.Valid<Integer>) result).value()).isEqualTo(42);
        }

        @Test
        void requiredMissing() {
            Arg<Integer> arg = Arg.integer("count", "desc").required();
            Validated<Integer> result = arg.extract(Map.of());
            assertThat(result).isInstanceOf(Validated.Invalid.class);
        }

        @Test
        void optionalPresent() {
            Arg<Optional<Integer>> arg = Arg.integer("count", "desc").optional();
            Validated<Optional<Integer>> result = arg.extract(Map.of("count", 5));
            assertThat(result).isInstanceOf(Validated.Valid.class);
            assertThat(((Validated.Valid<Optional<Integer>>) result).value()).hasValue(5);
        }

        @Test
        void optionalMissing() {
            Arg<Optional<Integer>> arg = Arg.integer("count", "desc").optional();
            Validated<Optional<Integer>> result = arg.extract(Map.of());
            assertThat(result).isInstanceOf(Validated.Valid.class);
            assertThat(((Validated.Valid<Optional<Integer>>) result).value()).isEmpty();
        }

        @Test
        void optionalWithDefault() {
            Arg<Integer> arg = Arg.integer("count", "desc").optional(10);
            Validated<Integer> result = arg.extract(Map.of());
            assertThat(result).isInstanceOf(Validated.Valid.class);
            assertThat(((Validated.Valid<Integer>) result).value()).isEqualTo(10);
        }

        @Test
        void minViolation() {
            Arg<Integer> arg = Arg.integer("count", "desc").min(1).required();
            Validated<Integer> result = arg.extract(Map.of("count", 0));
            assertThat(result).isInstanceOf(Validated.Invalid.class);
            assertThat(((Validated.Invalid<Integer>) result).message()).contains("at least 1");
        }

        @Test
        void maxViolation() {
            Arg<Integer> arg = Arg.integer("count", "desc").max(100).required();
            Validated<Integer> result = arg.extract(Map.of("count", 101));
            assertThat(result).isInstanceOf(Validated.Invalid.class);
            assertThat(((Validated.Invalid<Integer>) result).message()).contains("at most 100");
        }

        @Test
        void wrongType() {
            Arg<Integer> arg = Arg.integer("count", "desc").required();
            Validated<Integer> result = arg.extract(Map.of("count", "not a number"));
            assertThat(result).isInstanceOf(Validated.Invalid.class);
            assertThat(((Validated.Invalid<Integer>) result).message()).contains("integer");
        }

        @Test
        void acceptsDouble() {
            Arg<Integer> arg = Arg.integer("count", "desc").required();
            Validated<Integer> result = arg.extract(Map.of("count", 3.14));
            assertThat(result).isInstanceOf(Validated.Valid.class);
            assertThat(((Validated.Valid<Integer>) result).value()).isEqualTo(3);
        }
    }

    @Nested
    class BooleanArgTest {

        @Test
        void requiredPresent() {
            Arg<Boolean> arg = Arg.bool("flag", "desc").required();
            Validated<Boolean> result = arg.extract(Map.of("flag", true));
            assertThat(result).isInstanceOf(Validated.Valid.class);
            assertThat(((Validated.Valid<Boolean>) result).value()).isTrue();
        }

        @Test
        void requiredMissing() {
            Arg<Boolean> arg = Arg.bool("flag", "desc").required();
            Validated<Boolean> result = arg.extract(Map.of());
            assertThat(result).isInstanceOf(Validated.Invalid.class);
        }

        @Test
        void optionalWithDefault() {
            Arg<Boolean> arg = Arg.bool("flag", "desc").optional(false);
            Validated<Boolean> result = arg.extract(Map.of());
            assertThat(result).isInstanceOf(Validated.Valid.class);
            assertThat(((Validated.Valid<Boolean>) result).value()).isFalse();
        }

        @Test
        void wrongType() {
            Arg<Boolean> arg = Arg.bool("flag", "desc").required();
            Validated<Boolean> result = arg.extract(Map.of("flag", "yes"));
            assertThat(result).isInstanceOf(Validated.Invalid.class);
            assertThat(((Validated.Invalid<Boolean>) result).message()).contains("boolean");
        }
    }

    @Nested
    class StringArrayArgTest {

        @Test
        void present() {
            Arg<List<String>> arg = Arg.stringArray("items", "desc").optional();
            Validated<List<String>> result = arg.extract(Map.of("items", List.of("a", "b")));
            assertThat(result).isInstanceOf(Validated.Valid.class);
            assertThat(((Validated.Valid<List<String>>) result).value()).containsExactly("a", "b");
        }

        @Test
        void missing() {
            Arg<List<String>> arg = Arg.stringArray("items", "desc").optional();
            Validated<List<String>> result = arg.extract(Map.of());
            assertThat(result).isInstanceOf(Validated.Valid.class);
            assertThat(((Validated.Valid<List<String>>) result).value()).isEmpty();
        }

        @Test
        void filtersNonStrings() {
            Arg<List<String>> arg = Arg.stringArray("items", "desc").optional();
            Validated<List<String>> result = arg.extract(Map.of("items", List.of("a", 42, "b")));
            assertThat(result).isInstanceOf(Validated.Valid.class);
            assertThat(((Validated.Valid<List<String>>) result).value()).containsExactly("a", "b");
        }

        @Test
        void schemaProperties() {
            Arg<List<String>> arg = Arg.stringArray("items", "desc").optional();
            assertThat(arg.required()).isFalse();
            assertThat(arg.schemaType()).isEqualTo(Arg.SchemaType.STRING_ARRAY);
        }
    }

    @Nested
    class ProjectLocationArgTest {

        @Test
        void validPath() {
            Arg<ProjectLocation> arg = Arg.projectLocation("projectPath", "desc");
            Validated<ProjectLocation> result = arg.extract(Map.of("projectPath", "/some/path/"));
            assertThat(result).isInstanceOf(Validated.Valid.class);
            // trailing slash should be normalized
            assertThat(((Validated.Valid<ProjectLocation>) result).value().projectPath())
                    .isEqualTo("/some/path");
        }

        @Test
        void missingPath() {
            Arg<ProjectLocation> arg = Arg.projectLocation("projectPath", "desc");
            Validated<ProjectLocation> result = arg.extract(Map.of());
            assertThat(result).isInstanceOf(Validated.Invalid.class);
            assertThat(((Validated.Invalid<ProjectLocation>) result).message()).contains("projectPath");
        }

        @Test
        void alwaysRequired() {
            Arg<ProjectLocation> arg = Arg.projectLocation("projectPath", "desc");
            assertThat(arg.required()).isTrue();
        }
    }

    @Nested
    class ProjectRelativePathArgTest {

        @Test
        void validPath() {
            Arg<ProjectRelativePath> arg = Arg.projectRelativePath("filePath", "desc");
            Validated<ProjectRelativePath> result = arg.extract(Map.of("filePath", "src/Main.java"));
            assertThat(result).isInstanceOf(Validated.Valid.class);
            assertThat(((Validated.Valid<ProjectRelativePath>) result).value().relativePath())
                    .isEqualTo("src/Main.java");
        }

        @Test
        void traversalBlocked() {
            Arg<ProjectRelativePath> arg = Arg.projectRelativePath("filePath", "desc");
            Validated<ProjectRelativePath> result = arg.extract(Map.of("filePath", "../../etc/passwd"));
            assertThat(result).isInstanceOf(Validated.Invalid.class);
            assertThat(((Validated.Invalid<ProjectRelativePath>) result).message())
                    .contains("outside the project directory");
        }

        @Test
        void traversalBlockedNormalized() {
            Arg<ProjectRelativePath> arg = Arg.projectRelativePath("filePath", "desc");
            Validated<ProjectRelativePath> result = arg.extract(Map.of("filePath", "src/../../etc/passwd"));
            assertThat(result).isInstanceOf(Validated.Invalid.class);
        }

        @Test
        void missingPath() {
            Arg<ProjectRelativePath> arg = Arg.projectRelativePath("filePath", "desc");
            Validated<ProjectRelativePath> result = arg.extract(Map.of());
            assertThat(result).isInstanceOf(Validated.Invalid.class);
        }

        @Test
        void resolvesCombinedPath() {
            ProjectRelativePath rel = new ProjectRelativePath("src/Main.java");
            var resolved = rel.resolve("/project/root");
            // Normalize separators to make assertion OS-independent
            assertThat(resolved.toString().replace('\\','/')).isEqualTo("/project/root/src/Main.java");
        }
    }

    @Nested
    class EnumArgTest {

        enum Color { RED, GREEN, BLUE }

        @Test
        void requiredPresent() {
            Arg<Color> arg = Arg.enumArg("color", "desc", Color.class).required();
            Validated<Color> result = arg.extract(Map.of("color", "GREEN"));
            assertThat(result).isInstanceOf(Validated.Valid.class);
            assertThat(((Validated.Valid<Color>) result).value()).isEqualTo(Color.GREEN);
        }

        @Test
        void requiredMissing() {
            Arg<Color> arg = Arg.enumArg("color", "desc", Color.class).required();
            Validated<Color> result = arg.extract(Map.of());
            assertThat(result).isInstanceOf(Validated.Invalid.class);
            assertThat(((Validated.Invalid<Color>) result).message()).contains("color");
        }

        @Test
        void requiredInvalidValue() {
            Arg<Color> arg = Arg.enumArg("color", "desc", Color.class).required();
            Validated<Color> result = arg.extract(Map.of("color", "YELLOW"));
            assertThat(result).isInstanceOf(Validated.Invalid.class);
            assertThat(((Validated.Invalid<Color>) result).message()).contains("RED", "GREEN", "BLUE");
        }

        @Test
        void optionalPresent() {
            Arg<Optional<Color>> arg = Arg.enumArg("color", "desc", Color.class).optional();
            Validated<Optional<Color>> result = arg.extract(Map.of("color", "RED"));
            assertThat(result).isInstanceOf(Validated.Valid.class);
            assertThat(((Validated.Valid<Optional<Color>>) result).value()).hasValue(Color.RED);
        }

        @Test
        void optionalMissing() {
            Arg<Optional<Color>> arg = Arg.enumArg("color", "desc", Color.class).optional();
            Validated<Optional<Color>> result = arg.extract(Map.of());
            assertThat(result).isInstanceOf(Validated.Valid.class);
            assertThat(((Validated.Valid<Optional<Color>>) result).value()).isEmpty();
        }

        @Test
        void optionalWithDefault() {
            Arg<Color> arg = Arg.enumArg("color", "desc", Color.class).optional(Color.BLUE);
            Validated<Color> result = arg.extract(Map.of());
            assertThat(result).isInstanceOf(Validated.Valid.class);
            assertThat(((Validated.Valid<Color>) result).value()).isEqualTo(Color.BLUE);
        }

        @Test
        void schemaProperties() {
            Arg<Color> arg = Arg.enumArg("color", "desc", Color.class).required();
            assertThat(arg.key()).isEqualTo("color");
            assertThat(arg.description()).isEqualTo("desc (one of: RED, GREEN, BLUE)");
            assertThat(arg.required()).isTrue();
            assertThat(arg.schemaType()).isEqualTo(Arg.SchemaType.STRING);
        }
    }
}
