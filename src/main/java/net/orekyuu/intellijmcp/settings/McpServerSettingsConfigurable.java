package net.orekyuu.intellijmcp.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Settings configurable for MCP Server.
 */
public class McpServerSettingsConfigurable implements Configurable {

    private JBTextField portField;

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "MCP Server";
    }

    @Override
    public @Nullable JComponent createComponent() {
        portField = new JBTextField();
        portField.setText(String.valueOf(McpServerSettings.getInstance().getPort()));

        return FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("Port:"), portField, 1, false)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
    }

    @Override
    public boolean isModified() {
        McpServerSettings settings = McpServerSettings.getInstance();
        try {
            int port = Integer.parseInt(portField.getText().trim());
            return port != settings.getPort();
        } catch (NumberFormatException e) {
            return true;
        }
    }

    @Override
    public void apply() throws ConfigurationException {
        try {
            int port = Integer.parseInt(portField.getText().trim());
            if (port < 1 || port > 65535) {
                throw new ConfigurationException("Port must be between 1 and 65535");
            }
            McpServerSettings.getInstance().setPort(port);
        } catch (NumberFormatException e) {
            throw new ConfigurationException("Invalid port number");
        }
    }

    @Override
    public void reset() {
        portField.setText(String.valueOf(McpServerSettings.getInstance().getPort()));
    }

    @Override
    public void disposeUIResources() {
        portField = null;
    }
}
