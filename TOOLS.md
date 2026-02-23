# intellij-mcp Tools Reference

This document describes all MCP tools provided by the intellij-mcp plugin.

## Common Types

### LineRange

Used across many response types to represent a range of lines.

| Field | Type | Description |
|-------|------|-------------|
| `startLine` | `integer` | Start line (1-based, inclusive) |
| `endLine` | `integer` | End line (1-based, inclusive) |
| `lineCount` | `integer` | Total lines (`endLine - startLine + 1`) |

---

## Project & File Navigation

### list_projects

Get list of open projects in IntelliJ IDEA.

**Parameters:** None

**Response:**

| Field | Type | Description |
|-------|------|-------------|
| `projects` | `ProjectInfo[]` | List of open projects |

`ProjectInfo`:

| Field | Type | Description |
|-------|------|-------------|
| `name` | `string` | Project name |
| `basePath` | `string` | Absolute path to project root |
| `gitRemote` | `string` | Git remote URL (if available) |
| `gitBranch` | `string` | Current git branch (if available) |

---

### find_file

Search for files by name in a project. Supports glob patterns (`*` and `?`).

**Parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `projectPath` | Yes | Absolute path to the project root directory |
| `fileName` | Yes | File name or pattern (supports `*` and `?` wildcards) |
| `includeLibraries` | No | Include library files (default: `false`) |
| `maxResults` | No | Maximum number of results (default: `100`) |

**Response:**

| Field | Type | Description |
|-------|------|-------------|
| `pattern` | `string` | Search pattern used |
| `includeLibraries` | `boolean` | Whether libraries were included |
| `totalResults` | `integer` | Number of results found |
| `truncated` | `boolean` | Whether results were truncated |
| `files` | `FileInfo[]` | List of found files |

`FileInfo`:

| Field | Type | Description |
|-------|------|-------------|
| `path` | `string` | Absolute path |
| `relativePath` | `string` | Path relative to project root |
| `name` | `string` | File name |
| `extension` | `string` | File extension |
| `size` | `integer` | File size in bytes |
| `fileType` | `string` | File type name |

---

### open_file

Open a file in the IntelliJ IDEA editor.

**Parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `filePath` | Yes | Absolute path to the file to open |
| `projectPath` | Yes | Absolute path to the project root directory |

**Response:**

| Field | Type | Description |
|-------|------|-------------|
| `message` | `string` | Success message |

---

### read_file

Read the content of a file by its absolute path. Supports optional line range.

**Parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `filePath` | Yes | Absolute path to the file to read |
| `startLine` | No | Start line number (1-based, inclusive) |
| `endLine` | No | End line number (1-based, inclusive) |

**Response:**

| Field | Type | Description |
|-------|------|-------------|
| `filePath` | `string` | Absolute path to file |
| `content` | `string` | File content (or requested line range) |
| `totalLines` | `integer` | Total line count in file |
| `startLine` | `integer` | Start line of returned content (1-based) |
| `endLine` | `integer` | End line of returned content (1-based) |

---

### get_project_modules

List modules in a project with their type and source/resource roots.

**Parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `projectPath` | Yes | Absolute path to the project root directory |

**Response:**

| Field | Type | Description |
|-------|------|-------------|
| `modules` | `ModuleInfo[]` | Module list |

`ModuleInfo`:

| Field | Type | Description |
|-------|------|-------------|
| `name` | `string` | Module name |
| `path` | `string` | Module directory (relative to project) |
| `type` | `string` | Module type ID |
| `sourceFolders` | `string[]` | Relative paths of source folders |
| `resourceFolders` | `string[]` | Relative paths of resource folders |
| `testSourceFolders` | `string[]` | Relative paths of test source folders |
| `testResourceFolders` | `string[]` | Relative paths of test resource folders |

---

### get_project_dependencies

List module and library dependencies for a project or a single module.

**Parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `projectPath` | Yes | Absolute path to the project root directory |
| `moduleName` | No | Module name to filter dependencies |

**Response:**

| Field | Type | Description |
|-------|------|-------------|
| `dependencies` | `DependencyInfo[]` | Dependency entries |

`DependencyInfo`:

| Field | Type | Description |
|-------|------|-------------|
| `name` | `string` | Dependency name |

---

## Code Search

### find_class

Find classes by name in the project. Supports simple names or fully qualified names.

**Parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `className` | Yes | The class name to search for (simple or fully qualified) |
| `projectPath` | Yes | Absolute path to the project root directory |
| `includeLibraries` | No | Whether to include library classes (default: `false`) |

**Response:**

| Field | Type | Description |
|-------|------|-------------|
| `searchQuery` | `string` | The search term used |
| `classes` | `ClassInfo[]` | List of found classes |

`ClassInfo`:

| Field | Type | Description |
|-------|------|-------------|
| `name` | `string` | Simple class name |
| `qualifiedName` | `string` | Fully qualified name |
| `filePath` | `string` | Absolute path to file |
| `classType` | `string` | `"class"`, `"interface"`, `"enum"`, `"record"`, or `"annotation"` |
| `lineRange` | `LineRange` | Start and end line numbers |

---

### search_symbol

Search for symbols (methods, fields, classes) by name in the project. Returns up to 50 results with case-insensitive partial matching.

**Parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `query` | Yes | The symbol name to search for (supports partial matching) |
| `projectPath` | Yes | Absolute path to the project root directory |
| `symbolType` | No | Type of symbol: `all`, `class`, `method`, `field` (default: `all`) |

**Response:**

| Field | Type | Description |
|-------|------|-------------|
| `query` | `string` | Search query |
| `totalFound` | `integer` | Number of symbols found |
| `symbols` | `SymbolInfo[]` | List of found symbols |

`SymbolInfo`:

| Field | Type | Description |
|-------|------|-------------|
| `name` | `string` | Symbol name |
| `kind` | `string` | `"class"`, `"method"`, `"field"`, `"constructor"`, etc. |
| `containingClass` | `string` | Fully qualified class name (if applicable) |
| `signature` | `string` | Method/field signature (if applicable) |
| `filePath` | `string` | Absolute path to file |
| `lineRange` | `LineRange` | Definition line range |

---

### search_text

Search for text in project files. Supports regular expressions and case-sensitive matching.

**Parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `searchText` | Yes | The text or pattern to search for |
| `projectPath` | Yes | Absolute path to the project root directory |
| `useRegex` | No | Use regular expression matching (default: `false`) |
| `caseSensitive` | No | Case-sensitive matching (default: `false`) |
| `filePattern` | No | File name pattern to filter (e.g., `*.java`, `*.xml`) |
| `maxResults` | No | Maximum number of results (default: `100`) |

**Response:**

| Field | Type | Description |
|-------|------|-------------|
| `searchText` | `string` | Search query |
| `useRegex` | `boolean` | Whether regex was used |
| `caseSensitive` | `boolean` | Whether search was case-sensitive |
| `filePattern` | `string` | File pattern filter (if used) |
| `totalMatches` | `integer` | Number of matches found |
| `truncated` | `boolean` | Whether results were truncated |
| `matches` | `SearchMatch[]` | List of matches |

`SearchMatch`:

| Field | Type | Description |
|-------|------|-------------|
| `filePath` | `string` | Absolute path to file |
| `line` | `integer` | Line number (1-based) |
| `column` | `integer` | Column number (1-based) |
| `matchedText` | `string` | The matched text |
| `lineContent` | `string` | Full line content |

---

### find_usages

Find all usages of a symbol by class name and optional member name.

**Parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `className` | Yes | Fully qualified class name |
| `memberName` | No | Method, field, or inner class name |
| `projectPath` | Yes | Absolute path to the project root directory |

**Response:**

| Field | Type | Description |
|-------|------|-------------|
| `symbol` | `SymbolInfo` | Information about the searched symbol |
| `usages` | `UsageInfo[]` | List of usage locations |

`SymbolInfo`:

| Field | Type | Description |
|-------|------|-------------|
| `name` | `string` | Symbol name |
| `kind` | `string` | `"class"`, `"method"`, `"field"`, `"constructor"`, etc. |
| `containingClass` | `string` | Fully qualified class name (if applicable) |

`UsageInfo`:

| Field | Type | Description |
|-------|------|-------------|
| `filePath` | `string` | Absolute path to file |
| `lineRange` | `LineRange` | Start and end line numbers |
| `context` | `string` | Line content showing the usage |
| `usageType` | `string` | `"reference"`, `"call"`, etc. |
| `containingClass` | `string` | Class containing the usage |
| `containingMethod` | `string` | Method containing the usage (if applicable) |

---

## Code Structure & Navigation

### get_class_structure

Get the structure of a class including fields, methods, constructors, and inner classes.

**Parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `className` | Yes | The class name (simple or fully qualified) |
| `projectPath` | Yes | Absolute path to the project root directory |
| `includeInherited` | No | Whether to include inherited members (default: `false`) |

**Response:**

| Field | Type | Description |
|-------|------|-------------|
| `structure` | `ClassStructure` | Complete class structure |

`ClassStructure`:

| Field | Type | Description |
|-------|------|-------------|
| `name` | `string` | Simple class name |
| `qualifiedName` | `string` | Fully qualified name |
| `classType` | `string` | `"class"`, `"interface"`, `"enum"`, etc. |
| `filePath` | `string` | Absolute path to file |
| `lineRange` | `LineRange` | Class definition line range |
| `modifiers` | `string[]` | `"public"`, `"abstract"`, `"final"`, etc. |
| `superClass` | `string` | Superclass qualified name |
| `interfaces` | `string[]` | Implemented interface names |
| `fields` | `FieldInfo[]` | All fields |
| `constructors` | `MethodInfo[]` | All constructors |
| `methods` | `MethodInfo[]` | All methods |
| `innerClasses` | `InnerClassInfo[]` | All inner classes |

`FieldInfo`:

| Field | Type | Description |
|-------|------|-------------|
| `name` | `string` | Field name |
| `type` | `string` | Field type |
| `modifiers` | `string[]` | `"public"`, `"static"`, `"final"`, etc. |
| `inherited` | `boolean` | Whether inherited from superclass |
| `lineRange` | `LineRange` | Definition line range |

`MethodInfo`:

| Field | Type | Description |
|-------|------|-------------|
| `name` | `string` | Method name |
| `returnType` | `string` | Return type (`null` for constructors) |
| `parameters` | `ParameterInfo[]` | Method parameters |
| `modifiers` | `string[]` | `"public"`, `"static"`, `"abstract"`, etc. |
| `inherited` | `boolean` | Whether inherited from superclass |
| `lineRange` | `LineRange` | Definition line range |

`ParameterInfo`:

| Field | Type | Description |
|-------|------|-------------|
| `name` | `string` | Parameter name |
| `type` | `string` | Parameter type |

`InnerClassInfo`:

| Field | Type | Description |
|-------|------|-------------|
| `name` | `string` | Inner class name |
| `classType` | `string` | `"class"`, `"interface"`, `"enum"`, etc. |
| `modifiers` | `string[]` | `"public"`, `"static"`, etc. |
| `lineRange` | `LineRange` | Definition line range |

---

### get_definition

Get the definition location of a symbol by class name and optional member name.

**Parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `className` | Yes | Fully qualified class name |
| `memberName` | No | Method, field, or inner class name |
| `projectPath` | Yes | Absolute path to the project root directory |

**Response:**

| Field | Type | Description |
|-------|------|-------------|
| `definition` | `DefinitionInfo` | Symbol definition information |

`DefinitionInfo`:

| Field | Type | Description |
|-------|------|-------------|
| `name` | `string` | Symbol name |
| `kind` | `string` | `"class"`, `"method"`, `"field"`, `"constructor"`, etc. |
| `containingClass` | `string` | Fully qualified class name (if applicable) |
| `filePath` | `string` | Absolute path to file |
| `lineRange` | `LineRange` | Definition line range |

---

### get_source_code

Get the source code of a class or a specific member (method/field) by class name.

**Parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `projectPath` | Yes | Absolute path to the project root directory |
| `className` | Yes | Fully qualified class name |
| `memberName` | No | Method or field name |

**Response:**

| Field | Type | Description |
|-------|------|-------------|
| `name` | `string` | Symbol name |
| `kind` | `string` | `"class"`, `"method"`, `"field"`, etc. |
| `className` | `string` | Fully qualified class name |
| `sourceCode` | `string` | Actual source code text |
| `filePath` | `string` | Absolute path to file |
| `lineRange` | `LineRange` | Source code line range |

---

### get_call_hierarchy

Get call hierarchy (callers) for a method by class name and method name.

**Parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `className` | Yes | Fully qualified class name |
| `memberName` | Yes | Method name to get call hierarchy for |
| `projectPath` | Yes | Absolute path to the project root directory |
| `depth` | No | Maximum depth of the hierarchy (default: `3`, max: `10`) |

**Response:**

| Field | Type | Description |
|-------|------|-------------|
| `method` | `MethodInfo` | Root method with caller hierarchy |

`MethodInfo`:

| Field | Type | Description |
|-------|------|-------------|
| `name` | `string` | Method name |
| `className` | `string` | Fully qualified class name |
| `filePath` | `string` | Absolute path to file |
| `signature` | `string` | Method signature with parameter types |
| `lineRange` | `LineRange` | Start and end line numbers |
| `callers` | `MethodInfo[]` | Recursive list of caller methods |

---

### get_type_hierarchy

Get the type hierarchy of a class including superclasses, interfaces, and subclasses/implementors. Returns up to 50 subclasses.

**Parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `className` | Yes | The class name (simple or fully qualified) |
| `projectPath` | Yes | Absolute path to the project root directory |
| `includeSubclasses` | No | Whether to include subclasses/implementors (default: `true`) |

**Response:**

| Field | Type | Description |
|-------|------|-------------|
| `hierarchy` | `TypeHierarchy` | Complete type hierarchy |

`TypeHierarchy`:

| Field | Type | Description |
|-------|------|-------------|
| `name` | `string` | Class name |
| `qualifiedName` | `string` | Fully qualified name |
| `classType` | `string` | `"class"`, `"interface"`, `"enum"`, etc. |
| `filePath` | `string` | Absolute path to file |
| `lineRange` | `LineRange` | Class definition line range |
| `superclasses` | `TypeInfo[]` | Superclass chain |
| `interfaces` | `TypeInfo[]` | All implemented interfaces |
| `subclasses` | `TypeInfo[]` | Direct subclasses/implementations |

`TypeInfo`:

| Field | Type | Description |
|-------|------|-------------|
| `name` | `string` | Type name |
| `qualifiedName` | `string` | Fully qualified name |
| `classType` | `string` | `"class"`, `"interface"`, `"enum"`, etc. |
| `filePath` | `string` | Absolute path to file |
| `lineRange` | `LineRange` | Definition line range |

---

### get_implementations

Get all implementations of an interface or subclasses of a class.

**Parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `className` | Yes | The class or interface name (simple or fully qualified) |
| `projectPath` | Yes | Absolute path to the project root directory |
| `includeAbstract` | No | Whether to include abstract classes (default: `true`) |

**Response:**

| Field | Type | Description |
|-------|------|-------------|
| `target` | `ClassInfo` | Target class/interface |
| `implementations` | `ClassInfo[]` | List of implementations/subclasses |

`ClassInfo`:

| Field | Type | Description |
|-------|------|-------------|
| `name` | `string` | Simple class name |
| `qualifiedName` | `string` | Fully qualified name |
| `classType` | `string` | `"class"`, `"interface"`, `"enum"`, etc. |
| `filePath` | `string` | Absolute path to file |
| `lineRange` | `LineRange` | Class definition line range |
| `modifiers` | `string[]` | `"public"`, `"abstract"`, `"final"`, etc. |

---

### get_documentation

Get the documentation of a class, method, or field.

**Parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `symbolName` | Yes | The symbol name (class name, or `class.method`/`class.field`) |
| `projectPath` | Yes | Absolute path to the project root directory |

**Response:**

| Field | Type | Description |
|-------|------|-------------|
| `documentation` | `Documentation` | Symbol documentation |

`Documentation`:

| Field | Type | Description |
|-------|------|-------------|
| `name` | `string` | Symbol name |
| `qualifiedName` | `string` | Fully qualified name |
| `symbolType` | `string` | `"class"`, `"method"`, `"field"`, etc. |
| `documentation` | `string` | Documentation text (HTML stripped to plain text) |
| `filePath` | `string` | Absolute path to file |
| `lineRange` | `LineRange` | Definition line range |

---

## Diagnostics & Inspections

### get_diagnostics

Get all diagnostics (errors and warnings) in the project.

**Parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `projectPath` | Yes | Absolute path to the project root directory |
| `errorsOnly` | No | Whether to return only errors (default: `false`) |

**Response:**

| Field | Type | Description |
|-------|------|-------------|
| `totalErrors` | `integer` | Total error count |
| `totalWarnings` | `integer` | Total warning count |
| `files` | `FileDiagnostics[]` | Diagnostics grouped by file |

`FileDiagnostics`:

| Field | Type | Description |
|-------|------|-------------|
| `filePath` | `string` | Absolute path to file |
| `diagnostics` | `DiagnosticInfo[]` | List of issues in the file |

`DiagnosticInfo`:

| Field | Type | Description |
|-------|------|-------------|
| `severity` | `string` | `"error"` or `"warning"` |
| `message` | `string` | Diagnostic message |
| `lineRange` | `LineRange` | Location of the issue |
| `context` | `string` | Line content showing the issue |

---

### run_inspection

Run IntelliJ code inspections on a file or the entire project to find potential issues, code smells, and improvements.

**Parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `projectPath` | Yes | Absolute path to the project root directory |
| `filePath` | No | Absolute path to a specific file to inspect |
| `inspectionNames` | No | Names of specific inspections to run |
| `minSeverity` | No | Minimum severity level: `ERROR`, `WARNING`, `WEAK_WARNING`, or `INFO` (default: `INFO`) |
| `maxProblems` | No | Maximum number of problems to report (default: `100`) |
| `timeout` | No | Timeout in seconds (default: `60`) |

**Response:**

| Field | Type | Description |
|-------|------|-------------|
| `totalProblems` | `integer` | Total number of problems found |
| `errors` | `integer` | Error count |
| `warnings` | `integer` | Warning count |
| `weakWarnings` | `integer` | Weak warning count |
| `infos` | `integer` | Info count |
| `timedOut` | `boolean` | Whether inspection timed out |
| `truncated` | `boolean` | Whether results were truncated due to `maxProblems` limit |
| `files` | `FileProblems[]` | Problems grouped by file |

`FileProblems`:

| Field | Type | Description |
|-------|------|-------------|
| `filePath` | `string` | Relative path from project root |
| `problems` | `Problem[]` | List of problems in the file |

`Problem`:

| Field | Type | Description |
|-------|------|-------------|
| `message` | `string` | Problem description |
| `severity` | `string` | `"ERROR"`, `"WARNING"`, `"WEAK_WARNING"`, or `"INFO"` |
| `inspectionId` | `string` | Inspection identifier |
| `line` | `integer` | Line number (1-based) |

---

## Refactoring

### rename_symbol

Rename a symbol (class, method, field) and update all references.

**Parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `className` | Yes | Fully qualified class name |
| `memberName` | No | Method or field name |
| `newName` | Yes | The new name for the symbol |
| `projectPath` | Yes | Absolute path to the project root directory |

**Response:**

| Field | Type | Description |
|-------|------|-------------|
| `className` | `string` | Fully qualified class name |
| `oldName` | `string` | Previous symbol name |
| `newName` | `string` | New symbol name |
| `success` | `boolean` | Whether rename succeeded |
| `message` | `string` | Success message |

---

### extract_method

Extract a range of code into a new method. Java files only.

**Parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `filePath` | Yes | Absolute path to the file |
| `startLine` | Yes | Start line of code to extract (1-based) |
| `endLine` | Yes | End line of code to extract (1-based) |
| `methodName` | Yes | Name for the new method |
| `projectPath` | Yes | Absolute path to the project root directory |

**Response:**

| Field | Type | Description |
|-------|------|-------------|
| `filePath` | `string` | Absolute path to the file |
| `methodName` | `string` | Name of the extracted method |
| `startLine` | `integer` | Start line of extracted code (1-based) |
| `endLine` | `integer` | End line of extracted code (1-based) |
| `success` | `boolean` | Whether extraction succeeded |
| `message` | `string` | Success/error message |

---

### optimize_imports

Optimize imports in a file (remove unused imports and organize).

**Parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `filePath` | Yes | Absolute path to the file to optimize |
| `projectPath` | Yes | Absolute path to the project root directory |

**Response:**

| Field | Type | Description |
|-------|------|-------------|
| `filePath` | `string` | Absolute path to file |
| `success` | `boolean` | Whether optimization succeeded |
| `message` | `string` | Success message |

---

## Editor

### add_inline_comment

Add an inline comment to a file in the IDE editor. Supports Markdown formatting. The comment is displayed as a block inlay above the specified line.

**Parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `filePath` | Yes | Absolute path to the file |
| `line` | Yes | Line number to add the comment at (1-based) |
| `comment` | Yes | The comment text to display (supports Markdown) |
| `projectPath` | Yes | Absolute path to the project root directory |

**Response:**

| Field | Type | Description |
|-------|------|-------------|
| `commentId` | `string` | Unique identifier for the comment |
| `filePath` | `string` | Absolute path to the file |
| `line` | `integer` | Line number where comment was added (1-based) |
| `comment` | `string` | The comment text |
| `message` | `string` | Success message |

---

## File System

### create_file_or_directory

Create a file or directory in the project. Paths are relative to the project root to prevent access outside the project.

**Parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `projectPath` | Yes | Absolute path to the project root directory |
| `path` | Yes | Relative path from the project root (e.g., `src/main/java/Foo.java`) |
| `isDirectory` | Yes | `true` to create a directory, `false` to create a file |
| `content` | No | Initial content for the file (ignored for directories) |
| `createParents` | No | Whether to create parent directories if they don't exist (default: `true`) |
| `overwrite` | No | Whether to overwrite the file if it already exists (default: `false`, ignored for directories) |

**Response:**

| Field | Type | Description |
|-------|------|-------------|
| `path` | `string` | Relative path of the created file/directory |
| `isDirectory` | `boolean` | Whether a directory was created |
| `success` | `boolean` | Whether creation succeeded |
| `message` | `string` | Success message |

---

### delete_file_or_directory

Delete a file or directory in the project. Paths are relative to the project root to prevent access outside the project.

**Parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `projectPath` | Yes | Absolute path to the project root directory |
| `path` | Yes | Relative path from the project root |
| `recursive` | No | Whether to recursively delete directory contents (default: `false`) |

**Response:**

| Field | Type | Description |
|-------|------|-------------|
| `path` | `string` | Relative path of the deleted file/directory |
| `isDirectory` | `boolean` | Whether a directory was deleted |
| `success` | `boolean` | Whether deletion succeeded |
| `message` | `string` | Success message |
