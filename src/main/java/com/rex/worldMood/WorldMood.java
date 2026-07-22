package com.rex.worldMood;

import com.rex.worldMood.commands.WorldMoodCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class WorldMood extends JavaPlugin {

    private MoodManager moodManager;
    private WorldStateGuard worldStateGuard;
    private FogController fogController;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        Compat.logSupportSummary();

        // Must run before any mood can start: puts back world settings (game rules, borders, and
        // fog biome cells) that a crash left behind.
        worldStateGuard = new WorldStateGuard(this);
        worldStateGuard.restorePending();

        // Ships/extracts the coloured-fog datapack (modern jar only) and reports if a restart is
        // needed for its biomes to register.
        fogController = new FogController(this, worldStateGuard);
        fogController.installDatapack();

        moodManager = new MoodManager(this);

        moodManager.loadMoods();

        WorldMoodCommand commandExecutor = new WorldMoodCommand(this);
        getCommand("worldmood").setExecutor(commandExecutor);
        getCommand("worldmood").setTabCompleter(commandExecutor);

        if (getConfig().getBoolean("pluginEnabled", true)) {
            moodManager.startMoodCycle();
            getLogger().info("WorldMood enabled successfully!");
        } else {
            getLogger().warning("WorldMood is disabled in the config. Not starting mood cycle.");
        }
    }

    @Override
    public void onDisable() {
        if (moodManager != null) {
            moodManager.stopMoodCycle();
        }
        // Safety net: stopMoodCycle already ends the active mood (which restores its fog), but if any
        // tint is somehow still outstanding, put it back and clear the crash record on a clean stop.
        if (fogController != null) {
            fogController.end();
        }

        getLogger().info("WorldMood disabled.");
    }

    public void reloadPluginConfig() {
        reloadConfig();

        moodManager.stopMoodCycle();

        moodManager.loadMoods();

        if (getConfig().getBoolean("pluginEnabled", true)) {
            moodManager.startMoodCycle();
            getLogger().info("WorldMood configuration reloaded.");
        } else {
            getLogger().warning("WorldMood is disabled in the reloaded config. Mood cycle not started.");
        }
    }

    public MoodManager getMoodManager() {
        return moodManager;
    }

    public WorldStateGuard getWorldStateGuard() {
        return worldStateGuard;
    }

    public FogController getFogController() {
        return fogController;
    }
}