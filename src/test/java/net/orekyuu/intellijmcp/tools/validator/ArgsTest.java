package net.orekyuu.intellijmcp.tools.validator;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ArgsTest {

    @Nested
    class SchemaTest {

        @Test
        void generatesSchemaWithRequiredAndOptional() {
            Arg<String> className = Arg.string("className", "The class name").required();
            Arg<ProjectLocation> project = Arg.projectLocation("projectPath", "Project root");
            Arg<Boolean> includeLibs = Arg.bool("includeLibraries", "Include libs").optional(false);

            McpSchema.JsonSchema schema = Args.schema(project, className, includeLibs);

            assertThat(schema.type()).isEqualTo("object");
            assertThat(schema.required()).containsExactly("projectPath", "className");
            assertThat(schema.properties()).containsKeys("projectPath", "className", "includeLibraries");
        }

        @Test
        void generatesSchemaWithStringArray() {
            Arg<List<String>> items = Arg.stringArray("inspectionNames", "Names").optional();
            Arg<String> path = Arg.string("projectPath", "Project").required();

            McpSchema.JsonSchema schema = Args.schema(path, items);

            assertThat(schema.required()).containsExactly("projectPath");
            assertThat(schema.properties()).containsKeys("projectPath", "inspectionNames");
        }

        @Test
        void emptyArgsProducesEmptySchema() {
            McpSchema.JsonSchema schema = Args.schema();
            assertThat(schema.type()).isEqualTo("object");
            assertThat(schema.properties()).isNull();
            assertThat(schema.required()).isNull();
        }

        @Test
        void integerSchemaType() {
            Arg<Integer> count = Arg.integer("maxResults", "Max results").optional(100);
            McpSchema.JsonSchema schema = Args.schema(count);

            assertThat(schema.required()).isNull();
            @SuppressWarnings("unchecked")
            Map<String, Object> prop = (Map<String, Object>) schema.properties().get("maxResults");
            assertThat(prop.get("type")).isEqualTo("integer");
        }
    }

    @Nested
    class ValidateTest {

        @Test
        void validate1Success() {
            Arg<String> name = Arg.string("name", "desc").required();
            String result = Args.validate(Map.of("name", "hello"), name)
                    .mapN(v -> v)
                    .orElseErrors(errors -> "fail");
            assertThat(result).isEqualTo("hello");
        }

        @Test
        void validate1Error() {
            Arg<String> name = Arg.string("name", "desc").required();
            String result = Args.validate(Map.of(), name)
                    .mapN(v -> v)
                    .orElseErrors(errors -> "fail: " + errors.get("name"));
            assertThat(result).startsWith("fail:");
        }

        @Test
        void validate2Success() {
            Arg<String> a = Arg.string("a", "desc").required();
            Arg<Integer> b = Arg.integer("b", "desc").required();

            String result = Args.validate(Map.of("a", "hello", "b", 42), a, b)
                    .mapN((va, vb) -> va + ":" + vb)
                    .orElseErrors(errors -> "fail");
            assertThat(result).isEqualTo("hello:42");
        }

        @Test
        void validate3Success() {
            Arg<String> a = Arg.string("a", "desc").required();
            Arg<String> b = Arg.string("b", "desc").required();
            Arg<Boolean> c = Arg.bool("c", "desc").optional(true);

            String result = Args.validate(Map.of("a", "x", "b", "y"), a, b, c)
                    .mapN((va, vb, vc) -> va + ":" + vb + ":" + vc)
                    .orElseErrors(errors -> "fail");
            assertThat(result).isEqualTo("x:y:true");
        }

        @Test
        void multipleErrorsAggregated() {
            Arg<String> a = Arg.string("a", "desc").required();
            Arg<String> b = Arg.string("b", "desc").required();
            Arg<Integer> c = Arg.integer("c", "desc").required();

            AtomicReference<Map<String, String>> captured = new AtomicReference<>();
            Args.validate(Map.of(), a, b, c)
                    .mapN((va, vb, vc) -> "ok")
                    .orElseErrors(errors -> {
                        captured.set(errors);
                        return "fail";
                    });

            assertThat(captured.get()).containsKeys("a", "b", "c");
        }

        @Test
        void partialErrorsAggregated() {
            Arg<String> a = Arg.string("a", "desc").required();
            Arg<String> b = Arg.string("b", "desc").required();

            AtomicReference<Map<String, String>> captured = new AtomicReference<>();
            Args.validate(Map.of("a", "hello"), a, b)
                    .mapN((va, vb) -> "ok")
                    .orElseErrors(errors -> {
                        captured.set(errors);
                        return "fail";
                    });

            assertThat(captured.get()).containsKey("b");
            assertThat(captured.get()).doesNotContainKey("a");
        }

        @Test
        void validate4Success() {
            Arg<String> a = Arg.string("a", "d").required();
            Arg<String> b = Arg.string("b", "d").required();
            Arg<String> c = Arg.string("c", "d").required();
            Arg<String> d = Arg.string("d", "d").required();

            String result = Args.validate(Map.of("a", "1", "b", "2", "c", "3", "d", "4"), a, b, c, d)
                    .mapN((va, vb, vc, vd) -> va + vb + vc + vd)
                    .orElseErrors(errors -> "fail");
            assertThat(result).isEqualTo("1234");
        }

        @Test
        void validate5Success() {
            Arg<String> a = Arg.string("a", "d").required();
            Arg<String> b = Arg.string("b", "d").required();
            Arg<String> c = Arg.string("c", "d").required();
            Arg<String> d = Arg.string("d", "d").required();
            Arg<String> e = Arg.string("e", "d").required();

            String result = Args.validate(Map.of("a", "1", "b", "2", "c", "3", "d", "4", "e", "5"),
                            a, b, c, d, e)
                    .mapN((va, vb, vc, vd, ve) -> va + vb + vc + vd + ve)
                    .orElseErrors(errors -> "fail");
            assertThat(result).isEqualTo("12345");
        }

        @Test
        void validateWithOptionalArgs() {
            Arg<String> name = Arg.string("name", "desc").required();
            Arg<Optional<String>> nick = Arg.string("nick", "desc").optional();

            String result = Args.validate(Map.of("name", "Alice"), name, nick)
                    .mapN((n, nk) -> n + ":" + nk.orElse("none"))
                    .orElseErrors(errors -> "fail");
            assertThat(result).isEqualTo("Alice:none");
        }

        @Test
        void validateWithDomainTypes() {
            Arg<ProjectLocation> project = Arg.projectLocation("projectPath", "desc");
            Arg<ProjectRelativePath> file = Arg.projectRelativePath("filePath", "desc");

            AtomicReference<ProjectLocation> capturedProject = new AtomicReference<>();
            AtomicReference<ProjectRelativePath> capturedFile = new AtomicReference<>();

            Args.validate(Map.of("projectPath", "/my/project/", "filePath", "src/Main.java"),
                            project, file)
                    .mapN((p, f) -> {
                        capturedProject.set(p);
                        capturedFile.set(f);
                        return "ok";
                    })
                    .orElseErrors(errors -> "fail");

            assertThat(capturedProject.get().projectPath()).isEqualTo("/my/project");
            assertThat(capturedFile.get().relativePath()).isEqualTo("src/Main.java");
        }

        @Test
        void traversalErrorInValidation() {
            Arg<ProjectLocation> project = Arg.projectLocation("projectPath", "desc");
            Arg<ProjectRelativePath> file = Arg.projectRelativePath("filePath", "desc");

            AtomicReference<Map<String, String>> captured = new AtomicReference<>();
            Args.validate(Map.of("projectPath", "/my/project", "filePath", "../../etc/passwd"),
                            project, file)
                    .mapN((p, f) -> "ok")
                    .orElseErrors(errors -> {
                        captured.set(errors);
                        return "fail";
                    });

            assertThat(captured.get()).containsKey("filePath");
            assertThat(captured.get().get("filePath")).contains("outside the project directory");
        }
    }
}
