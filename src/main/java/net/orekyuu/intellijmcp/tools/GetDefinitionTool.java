package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.*;
import io.modelcontextprotocol.spec.McpSchema;

import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;

/**
 * MCP tool that retrieves the definition location of a symbol at a given position.
 * Returns the file path, line range, and symbol information where the symbol is defined.
 */
public class GetDefinitionTool extends AbstractMcpTool<GetDefinitionTool.GetDefinitionResponse> {

    private static final Logger LOG = Logger.getInstance(GetDefinitionTool.class);

    @Override
    public String getName() {
        return "get_definition";
    }

    @Override
    public String getDescription() {
        return "Get the definition location of a symbol at the specified file and offset position";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return JsonSchemaBuilder.object()
                .requiredString("filePath", "Absolute path to the file containing the symbol")
                .requiredInteger("offset", "Character offset position in the file where the symbol is located")
                .requiredString("projectPath", "Absolute path to the project root directory")
                .build();
    }

    @Override
    public Result<ErrorResponse, GetDefinitionResponse> execute(Map<String, Object> arguments) {
        return runReadActionWithResult(() -> {
            try {
                // Get arguments
                String filePath;
                int offset;
                String projectPath;
                try {
                    filePath = getRequiredStringArg(arguments, "filePath");
                    offset = getIntegerArg(arguments, "offset")
                            .orElseThrow(() -> new IllegalArgumentException("offset is required"));
                    projectPath = getRequiredStringArg(arguments, "projectPath");
                } catch (IllegalArgumentException e) {
                    return errorResult("Error: " + e.getMessage());
                }

                // Find project
                Optional<Project> projectOpt = findProjectByPath(projectPath);
                if (projectOpt.isEmpty()) {
                    return errorResult("Error: Project not found at path: " + projectPath);
                }
                Project project = projectOpt.get();

                // Find file
                VirtualFile virtualFile = VirtualFileManager.getInstance()
                        .findFileByNioPath(Paths.get(filePath));
                if (virtualFile == null) {
                    return errorResult("Error: File not found: " + filePath);
                }

                PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
                if (psiFile == null) {
                    return errorResult("Error: Could not parse file: " + filePath);
                }

                // Find reference at offset
                PsiReference reference = psiFile.findReferenceAt(offset);
                if (reference == null) {
                    // Try to find element at offset and check if it's already a declaration
                    PsiElement element = psiFile.findElementAt(offset);
                    if (element != null) {
                        PsiElement parent = element.getParent();
                        if (parent instanceof PsiNamedElement namedElement) {
                            DefinitionInfo info = createDefinitionInfo(namedElement);
                            if (info != null) {
                                return successResult(new GetDefinitionResponse(info));
                            }
                        }
                    }
                    return errorResult("Error: No symbol found at the specified position");
                }

                // Resolve reference to get definition
                PsiElement resolved = reference.resolve();
                if (resolved == null) {
                    return errorResult("Error: Could not resolve symbol definition");
                }

                DefinitionInfo info = createDefinitionInfo(resolved);
                if (info == null) {
                    return errorResult("Error: Could not get definition information");
                }

                return successResult(new GetDefinitionResponse(info));

            } catch (Exception e) {
                LOG.error("Error in get_definition tool", e);
                return errorResult("Error: " + e.getMessage());
            }
        });
    }

    private DefinitionInfo createDefinitionInfo(PsiElement element) {
        String name = null;
        String kind = "unknown";
        String containingClass = null;
        String filePath = null;
        LineRange lineRange = null;

        // Get name
        if (element instanceof PsiNamedElement namedElement) {
            name = namedElement.getName();
        }

        // Get kind and container
        if (element instanceof PsiClass psiClass) {
            kind = getClassKind(psiClass);
            PsiClass parent = psiClass.getContainingClass();
            if (parent != null) {
                containingClass = parent.getQualifiedName();
            }
        } else if (element instanceof PsiMethod method) {
            kind = method.isConstructor() ? "constructor" : "method";
            PsiClass parent = method.getContainingClass();
            if (parent != null) {
                containingClass = parent.getQualifiedName();
            }
        } else if (element instanceof PsiField field) {
            kind = "field";
            PsiClass parent = field.getContainingClass();
            if (parent != null) {
                containingClass = parent.getQualifiedName();
            }
        } else if (element instanceof PsiParameter) {
            kind = "parameter";
        } else if (element instanceof PsiLocalVariable) {
            kind = "local_variable";
        } else if (element instanceof PsiPackage) {
            kind = "package";
            name = ((PsiPackage) element).getQualifiedName();
        }

        // Get file path and line range
        PsiFile containingFile = element.getContainingFile();
        if (containingFile != null) {
            VirtualFile virtualFile = containingFile.getVirtualFile();
            if (virtualFile != null) {
                filePath = virtualFile.getPath();
            }

            var textRange = element.getTextRange();
            if (textRange != null) {
                com.intellij.openapi.editor.Document document =
                        PsiDocumentManager.getInstance(element.getProject()).getDocument(containingFile);
                if (document != null) {
                    int startLine = document.getLineNumber(textRange.getStartOffset()) + 1;
                    int endLine = document.getLineNumber(textRange.getEndOffset()) + 1;
                    lineRange = new LineRange(startLine, endLine);
                }
            }
        }

        if (name == null && filePath == null) {
            return null;
        }

        return new DefinitionInfo(name, kind, containingClass, filePath, lineRange);
    }

    private String getClassKind(PsiClass psiClass) {
        if (psiClass.isInterface()) {
            return "interface";
        } else if (psiClass.isEnum()) {
            return "enum";
        } else if (psiClass.isRecord()) {
            return "record";
        } else if (psiClass.isAnnotationType()) {
            return "annotation";
        } else {
            return "class";
        }
    }

    public record GetDefinitionResponse(DefinitionInfo definition) {}

    public record DefinitionInfo(
            String name,
            String kind,
            String containingClass,
            String filePath,
            LineRange lineRange
    ) {}
}
