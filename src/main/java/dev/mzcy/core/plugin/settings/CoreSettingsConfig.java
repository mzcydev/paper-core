package dev.mzcy.core.plugin.settings;

import dev.mzcy.core.annotation.Config;
import dev.mzcy.core.config.AbstractConfig;

@Config("core-settings")
public class CoreSettingsConfig extends AbstractConfig {

    public UpdaterSection updater = new UpdaterSection();

    public static class UpdaterSection implements java.io.Serializable {
        public boolean enabled = true;
        public String branch = "main"; // "main" oder "dev"
        public boolean notifyOps = true;
    }
}