package net.orekyuu.intellijmcp.comment;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Startup activity that ensures the InlineCommentEditorListener service is initialized
 * when a project is opened.
 */
public class InlineCommentStartupActivity implements ProjectActivity {

    @Nullable
    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        // Force initialization of the editor listener service
        InlineCommentEditorListener.getInstance(project);
        return Unit.INSTANCE;
    }
}
