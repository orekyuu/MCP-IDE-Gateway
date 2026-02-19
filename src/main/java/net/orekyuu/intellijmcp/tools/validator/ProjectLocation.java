package net.orekyuu.intellijmcp.tools.validator;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Computable;

import java.util.Optional;

public record ProjectLocation(String projectPath) {

    public Optional<Project> resolve() {
        return ApplicationManager.getApplication().runReadAction((Computable<Optional<Project>>) () -> {
            Project[] projects = ProjectManager.getInstance().getOpenProjects();
            for (Project project : projects) {
                String basePath = project.getBasePath();
                if (basePath != null && normalizePath(basePath).equals(projectPath)) {
                    return Optional.of(project);
                }
            }
            return Optional.empty();
        });
    }

    public Validated<Project> resolveValidated() {
        Optional<Project> opt = resolve();
        if (opt.isEmpty()) {
            return new Validated.Invalid<>("projectPath", "Project not found at path: " + projectPath);
        }
        return new Validated.Valid<>(opt.get());
    }

    static String normalizePath(String path) {
        if (path == null) return null;
        while (path.endsWith("/") || path.endsWith("\\")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }
}
