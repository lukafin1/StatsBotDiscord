package de.timmi6790.statsbotdiscord.modules;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ModuleManagerTest {
    private ModuleManager getModuleManager() {
        return new ModuleManager();
    }

    @Test
    void registerModule() {
        final ModuleManager commandManager = this.getModuleManager();
        final AbstractModule module = new ExampleModule();
        assertThat(commandManager.registerModule(module)).isTrue();
    }

    @Test
    void getModuleByClass() {
        final ModuleManager commandManager = this.getModuleManager();
        final AbstractModule module = new ExampleModule();
        commandManager.registerModule(module);

        final AbstractModule found = commandManager.getModule(module.getClass());
        assertThat(module).isEqualTo(found);
    }
}