# MCP IDE Gateway

MCP IDE Gateway is an MCP (Model Context Protocol) server plugin that enables AI assistants like Claude to interact with JetBrains IDEs such as IntelliJ IDEA. It allows AI to read and write code, search, refactor, and more.

## Key Features

- **Project & File Operations**: Get project information, search for files, read/write files, and expand files.
- **Code Search**: Search for classes, symbols, and text, and find usages.
- **Code Structure Analysis**: Get class structures, type hierarchies, call hierarchies, and locate definitions.
- **Code Diagnostics**: Get errors and warnings, and run code inspections.
- **Refactoring**: Rename symbols, extract methods, and optimize imports.
- **Editor Integration**: Add inline comments.

For more details on available tools, please refer to [TOOLS.md](TOOLS.md).

## Usage

### 1. Plugin Installation

#### Install from GitHub Releases (Recommended)

1. Download the latest ZIP file from [GitHub Releases](https://github.com/orekyuu/intellij-mcp/releases).
2. In IntelliJ IDEA, go to `Settings > Plugins > ⚙️ > Install Plugin from Disk...` and select the downloaded ZIP file.

#### Build and Install from Source

1. Clone this repository.
2. Run `./gradlew buildPlugin`.
3. Install the ZIP file generated in `build/distributions/` following the same steps as above.

### 2. Configuration

By default, the MCP server starts on port `3000`.
You can change the port number in `Settings > Tools > MCP Server`.

### 3. Verification

Once the plugin is enabled, an "MCP Server" tool window will appear at the bottom of the IDE.
This window displays server startup logs and information about connected clients.

## Setup for Claude CLI

To use this plugin with Claude CLI, run the following command to add the configuration:

```bash
claude mcp add intellij-mcp curl -- -Ns http://localhost:3000/mcp/sse
```

*Note: If you have changed the port number, please update the URL accordingly.*

