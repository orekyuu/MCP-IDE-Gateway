package net.orekyuu.intellijmcp.tools.validator;

import com.intellij.openapi.project.Project;

import java.nio.file.Path;
import java.nio.file.Paths;

public record ProjectRelativePath(String relativePath) {

    public Path resolve(Project project) {
        return resolve(project.getBasePath());
    }

    public Path resolve(ProjectLocation projectLocation) {
        return resolve(projectLocation.projectPath());
    }

    public Path resolve(String projectPath) {
        return Paths.get(projectPath).resolve(relativePath).normalize();
    }

    static boolean isTraversal(String path) {
        return Paths.get(path).normalize().startsWith("..");
    }
}
