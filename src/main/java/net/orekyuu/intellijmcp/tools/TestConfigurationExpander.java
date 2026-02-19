package net.orekyuu.intellijmcp.tools;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.List;

public interface TestConfigurationExpander {
    ExtensionPointName<TestConfigurationExpander> EP_NAME =
            ExtensionPointName.create("net.orekyuu.mcp-ide-gateway.testConfigurationExpander");

    List<RunnerAndConfigurationSettings> expand(
            List<RunnerAndConfigurationSettings> configs,
            VirtualFile file,
            Project project
    );
}
