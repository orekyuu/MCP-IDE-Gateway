package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.rename.RenameProcessor;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * MCP tool that renames a symbol (class, method, field) by class name and optional member name.
 */
public class RenameSymbolTool extends AbstractMcpTool<RenameSymbolTool.RenameSymbolResponse> {

    private static final Logger LOG = Logger.getInstance(RenameSymbolTool.class);

    @Override
    public String getName() {
        return "rename_symbol";
    }

    @Override
    public String getDescription() {
        return "Rename a symbol (class, method, field) and update all references";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return JsonSchemaBuilder.object()
                .requiredString("className", "Fully qualified class name (e.g., 'com.example.MyClass')")
                .optionalString("memberName", "Method or field name. If not specified, renames the class itself")
                .requiredString("newName", "The new name for the symbol")
                .requiredString("projectPath", "Absolute path to the project root directory")
                .build();
    }

    @Override
    public Result<ErrorResponse, RenameSymbolResponse> execute(Map<String, Object> arguments) {
        try {
            // Get arguments
            String className;
            String newName;
            String projectPath;
            try {
                className = getRequiredStringArg(arguments, "className");
                newName = getRequiredStringArg(arguments, "newName");
                projectPath = getRequiredStringArg(arguments, "projectPath");
            } catch (IllegalArgumentException e) {
                return errorResult("Error: " + e.getMessage());
            }

            Optional<String> memberName = getStringArg(arguments, "memberName");

            // Find project
            Optional<Project> projectOpt = findProjectByPath(projectPath);
            if (projectOpt.isEmpty()) {
                return errorResult("Error: Project not found at path: " + projectPath);
            }
            Project project = projectOpt.get();

            // Resolve element
            PsiElementResolver.ResolveResult resolveResult = runReadAction(() ->
                    PsiElementResolver.resolve(project, className, memberName));

            return switch (resolveResult) {
                case PsiElementResolver.ResolveResult.ClassNotFound r ->
                        errorResult("Error: Class not found: " + r.className());
                case PsiElementResolver.ResolveResult.MemberNotFound r ->
                        errorResult("Error: Member '" + r.memberName() + "' not found in class: " + r.className());
                case PsiElementResolver.ResolveResult.Success r -> {
                    PsiElement namedElement = r.element();

                    String oldName = runReadAction(() -> {
                        if (namedElement instanceof PsiNamedElement named) {
                            return named.getName();
                        }
                        return null;
                    });

                    // Perform rename on EDT
                    CompletableFuture<Result<ErrorResponse, RenameSymbolResponse>> future = new CompletableFuture<>();

                    ApplicationManager.getApplication().invokeLater(() -> {
                        try {
                            WriteCommandAction.runWriteCommandAction(project, "Rename Symbol", null, () -> {
                                RenameProcessor processor = new RenameProcessor(
                                        project,
                                        namedElement,
                                        newName,
                                        false,  // searchInComments
                                        false   // searchInNonJavaFiles
                                );
                                processor.run();
                            });

                            future.complete(successResult(new RenameSymbolResponse(
                                    className,
                                    oldName,
                                    newName,
                                    true,
                                    "Symbol renamed successfully"
                            )));
                        } catch (Exception e) {
                            LOG.error("Error renaming symbol", e);
                            future.complete(errorResult("Error: " + e.getMessage()));
                        }
                    });

                    // Wait for completion with timeout
                    yield future.get(30, TimeUnit.SECONDS);
                }
            };

        } catch (Exception e) {
            LOG.error("Error in rename_symbol tool", e);
            return errorResult("Error: " + e.getMessage());
        }
    }

    public record RenameSymbolResponse(
            String className,
            String oldName,
            String newName,
            boolean success,
            String message
    ) {}
}
