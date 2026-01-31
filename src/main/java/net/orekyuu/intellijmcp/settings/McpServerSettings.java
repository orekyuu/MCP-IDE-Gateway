package net.orekyuu.intellijmcp.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Persistent settings for MCP Server.
 */
@State(
    name = "McpServerSettings",
    storages = @Storage("McpServerSettings.xml")
)
public final class McpServerSettings implements PersistentStateComponent<McpServerSettings.State> {

    public static final int DEFAULT_PORT = 3000;

    private State state = new State();

    public static McpServerSettings getInstance() {
        return ApplicationManager.getApplication().getService(McpServerSettings.class);
    }

    @Override
    public @Nullable State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    public int getPort() {
        return state.port;
    }

    public void setPort(int port) {
        state.port = port;
    }

    public static class State {
        public int port = DEFAULT_PORT;
    }
}
