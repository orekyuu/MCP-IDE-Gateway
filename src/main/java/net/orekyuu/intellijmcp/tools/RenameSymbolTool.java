package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.rename.RenameProcessor;
import io.modelcontextprotocol.spec.McpSchema;
import net.orekyuu.intellijmcp.tools.validator.Arg;
import net.orekyuu.intellijmcp.tools.validator.Args;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * MCP tool that renames a symbol (class, method, field) by class name and optional member name.
 */
public class RenameSymbolTool extends AbstractProjectMcpTool<RenameSymbolTool.RenameSymbolResponse> {

    private static final Logger LOG = Logger.getInstance(RenameSymbolTool.class);
    private static final Arg<String> CLASS_NAME = Arg.string("className", "Fully qualified class name (e.g., 'com.example.MyClass')").required();
    private static final Arg<Optional<String>> MEMBER_NAME = Arg.string("memberName", "Method or field name. If not specified, renames the class itself").optional();
    private static final Arg<String> NEW_NAME = Arg.string("newName", "The new name for the symbol").required();
    private static final Arg<Project> PROJECT = Arg.project();

    @Override
    public String getDescription() {
        return "Rename a symbol (class, method, field) and update all references";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return Args.schema(CLASS_NAME, MEMBER_NAME, NEW_NAME, PROJECT);
    }

    @Override
    public Result<ErrorResponse, RenameSymbolResponse> doExecute(Map<String, Object> arguments) {
        return Args.validate(arguments, CLASS_NAME, MEMBER_NAME, NEW_NAME, PROJECT)
                .mapN((className, memberName, newName, project) -> {
                    try {
                        // Resolve element
                        PsiElementResolver.ResolveResult resolveResult = runReadAction(() ->
                                PsiElementResolver.resolve(project, className, memberName.orElse(null)));

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
                })
                .orElseErrors(errors -> errorResult("Error: " + Args.formatErrors(errors)));
    }

    public record RenameSymbolResponse(
            String className,
            String oldName,
            String newName,
            boolean success,
            String message
    ) {}
}
