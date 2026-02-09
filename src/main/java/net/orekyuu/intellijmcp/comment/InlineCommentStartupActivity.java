package net.orekyuu.intellijmcp.comment;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

/**
 * Startup activity that ensures the InlineCommentEditorListener service is initialized
 * when a project is opened.
 */
public class InlineCommentStartupActivity implements StartupActivity {

    @Override
    public void runActivity(@NotNull Project project) {
        // Force initialization of the editor listener service
        InlineCommentEditorListener.getInstance(project);
    }
}
