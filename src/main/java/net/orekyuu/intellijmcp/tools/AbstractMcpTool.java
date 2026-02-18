package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Computable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Abstract base class for MCP tools providing common helper methods.
 * Extend this class to create new MCP tools with minimal boilerplate.
 */
public abstract class AbstractMcpTool<RESPONSE> implements McpTool<RESPONSE> {

    // ========== Result helpers ==========

    /**
     * Creates a successful result with the given message.
     */
    protected Result<ErrorResponse, RESPONSE> successResult(RESPONSE message) {
        return Result.success(message);
    }

    /**
     * Creates an error result with the given message.
     */
    protected Result<ErrorResponse, RESPONSE> errorResult(String message) {
        return Result.error(new ErrorResponse(message));
    }

    // ========== Threading helpers ==========

    /**
     * Runs the given computation in a read action.
     */
    protected <T> T runReadAction(Computable<T> computation) {
        return ApplicationManager.getApplication().runReadAction(computation);
    }

    /**
     * Runs the given computation in a read action and returns the result.
     * Catches exceptions and returns an error result.
     */
    protected Result<ErrorResponse, RESPONSE> runReadActionWithResult(Supplier<Result<ErrorResponse, RESPONSE>> computation) {
        return ApplicationManager.getApplication().runReadAction(
                (Computable<Result<ErrorResponse, RESPONSE>>) computation::get
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
     * Finds an open project by its base path.
     *
     * @param projectPath the absolute path to the project root directory
     * @return Optional containing the project if found
     */
    protected Optional<Project> findProjectByPath(String projectPath) {
        if (projectPath == null || projectPath.isEmpty()) {
            return Optional.empty();
        }
        String normalizedPath = normalizePath(projectPath);
        Project[] projects = getOpenProjects();
        for (Project project : projects) {
            String basePath = project.getBasePath();
            if (basePath != null && normalizePath(basePath).equals(normalizedPath)) {
                return Optional.of(project);
            }
        }
        return Optional.empty();
    }

    /**
     * Normalizes a path by removing trailing slashes.
     *
     * @param path the path to normalize
     * @return the normalized path
     */
    private String normalizePath(String path) {
        if (path == null) return null;
        while (path.endsWith("/") || path.endsWith("\\")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
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

    /**
     * Gets a string list argument from the arguments map.
     *
     * @param arguments the arguments map
     * @param key       the key to look up
     * @return List containing string values (empty list if not present)
     */
    protected List<String> getStringListArg(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(item -> item instanceof String)
                    .map(item -> (String) item)
                    .toList();
        }
        return List.of();
    }
}
