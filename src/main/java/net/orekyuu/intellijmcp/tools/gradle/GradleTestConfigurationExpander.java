package net.orekyuu.intellijmcp.tools.gradle;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import net.orekyuu.intellijmcp.tools.TestConfigurationExpander;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestRunConfigurationProducer;
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration;
import org.jetbrains.plugins.gradle.util.TasksToRun;

import java.util.ArrayList;
import java.util.List;

public class GradleTestConfigurationExpander implements TestConfigurationExpander {

    @Override
    public List<RunnerAndConfigurationSettings> expand(
            List<RunnerAndConfigurationSettings> configs,
            VirtualFile file,
            Project project) {

        List<TasksToRun> allTasks = GradleTestRunConfigurationProducer.findAllTestsTaskToRun(file, project);
        if (allTasks.size() <= 1) {
            return configs;
        }

        List<RunnerAndConfigurationSettings> result = new ArrayList<>();
        for (RunnerAndConfigurationSettings config : configs) {
            if (!(config.getConfiguration() instanceof GradleRunConfiguration gradleConfig)) {
                result.add(config);
                continue;
            }

            // Extract the test filter arguments (e.g. "--tests com.example.MyTest") from the
            // original taskNames. IntelliJ stores the filter inline with the task names, so
            // a plain setTaskNames() would strip it. We preserve arguments (tokens starting
            // with "--") and append them to the new task names.
            List<String> originalTaskNames = gradleConfig.getSettings().getTaskNames();
            int filterArgStart = originalTaskNames.size();
            for (int i = 0; i < originalTaskNames.size(); i++) {
                if (originalTaskNames.get(i).startsWith("--")) {
                    filterArgStart = i;
                    break;
                }
            }
            List<String> testFilterArgs = new ArrayList<>(originalTaskNames.subList(filterArgStart, originalTaskNames.size()));

            for (TasksToRun tasksToRun : allTasks) {
                ExternalSystemRunConfiguration cloned = gradleConfig.clone();
                List<String> newTaskNames = new ArrayList<>(tasksToRun.getTasks());
                newTaskNames.addAll(testFilterArgs);
                cloned.getSettings().setTaskNames(newTaskNames);
                String taskName = tasksToRun.getTestName();
                cloned.setName(config.getName() + " (" + taskName + ")");

                RunnerAndConfigurationSettings clonedSettings = RunManager.getInstance(project)
                        .createConfiguration(cloned, config.getFactory());
                result.add(clonedSettings);
            }
        }

        return result;
    }
}
