package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Computable;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Abstract base class for MCP tools providing common helper methods.
 * Extend this class to create new MCP tools with minimal boilerplate.
 */
public abstract class AbstractMcpTool implements McpTool {

    // ========== Result helpers ==========

    /**
     * Creates a successful result with the given message.
     */
    protected McpSchema.CallToolResult successResult(String message) {
        return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(message)), false);
    }

    /**
     * Creates an error result with the given message.
     */
    protected McpSchema.CallToolResult errorResult(String message) {
        return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(message)), true);
    }

    // ========== Threading helpers ==========

    /**
     * Runs the given computation in a read action.
     */
    protected <T> T runReadAction(Computable<T> computation) {
        return ApplicationManager.getApplication().runReadAction(computation);
    }

    /**
     * Runs the given computation in a read action and wraps the result in a CallToolResult.
     * Catches exceptions and returns an error result.
     */
    protected McpSchema.CallToolResult runReadActionWithResult(Supplier<McpSchema.CallToolResult> computation) {
        return ApplicationManager.getApplication().runReadAction(
                (Computable<McpSchema.CallToolResult>) computation::get
        );
    }

    /**
     * Schedules the given runnable to run on the Event Dispatch Thread (EDT).
     * Does not wait for completion.
     */
    protected void runOnEdt(Runnable runnable) {
        ApplicationManager.getApplication().invokeLater(runnable);
    }

    // ========== Project helpers ==========

    /**
     * Gets all open projects.
     */
    protected Project[] getOpenProjects() {
        return runReadAction(() -> ProjectManager.getInstance().getOpenProjects());
    }

    /**
     * Finds an open project by name.
     *
     * @param projectName the name of the project to find
     * @return Optional containing the project if found
     */
    protected Optional<Project> findProjectByName(String projectName) {
        Project[] projects = getOpenProjects();
        for (Project project : projects) {
            if (project.getName().equals(projectName)) {
                return Optional.of(project);
            }
        }
        return Optional.empty();
    }

    /**
     * Finds a project by name, or returns the first open project if name is null/empty.
     *
     * @param projectName the name of the project (optional)
     * @return Optional containing the project
     */
    protected Optional<Project> findProjectOrFirst(String projectName) {
        Project[] projects = getOpenProjects();

        if (projectName != null && !projectName.isEmpty()) {
            return findProjectByName(projectName);
        } else if (projects.length > 0) {
            return Optional.of(projects[0]);
        }

        return Optional.empty();
    }

    // ========== Argument helpers ==========

    /**
     * Gets a string argument from the arguments map.
     *
     * @param arguments the arguments map
     * @param key       the key to look up
     * @return Optional containing the string value if present and non-empty
     */
    protected Optional<String> getStringArg(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        if (value instanceof String str && !str.isEmpty()) {
            return Optional.of(str);
        }
        return Optional.empty();
    }

    /**
     * Gets a required string argument from the arguments map.
     *
     * @param arguments the arguments map
     * @param key       the key to look up
     * @return the string value
     * @throws IllegalArgumentException if the argument is missing or empty
     */
    protected String getRequiredStringArg(Map<String, Object> arguments, String key) {
        return getStringArg(arguments, key)
                .orElseThrow(() -> new IllegalArgumentException(key + " is required"));
    }

    /**
     * Gets an integer argument from the arguments map.
     *
     * @param arguments the arguments map
     * @param key       the key to look up
     * @return Optional containing the integer value if present
     */
    protected Optional<Integer> getIntegerArg(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        if (value instanceof Number number) {
            return Optional.of(number.intValue());
        }
        return Optional.empty();
    }

    /**
     * Gets a boolean argument from the arguments map.
     *
     * @param arguments the arguments map
     * @param key       the key to look up
     * @return Optional containing the boolean value if present
     */
    protected Optional<Boolean> getBooleanArg(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        if (value instanceof Boolean bool) {
            return Optional.of(bool);
        }
        return Optional.empty();
    }
}
