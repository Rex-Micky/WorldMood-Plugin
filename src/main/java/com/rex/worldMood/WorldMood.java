package com.rex.worldMood;

import com.rex.worldMood.commands.WorldMoodCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class WorldMood extends JavaPlugin {

    private MoodManager moodManager;
    private WorldStateGuard worldStateGuard;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Must run before any mood can start: puts back world settings that a crash left behind.
        worldStateGuard = new WorldStateGuard(this);
        worldStateGuard.restorePending();

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
}