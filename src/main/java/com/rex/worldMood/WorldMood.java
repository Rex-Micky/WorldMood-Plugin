package com.rex.worldMood;

import com.rex.worldMood.commands.WorldMoodCommand;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class WorldMood extends JavaPlugin {

    private MoodManager moodManager;
    private int currentTick = 0;

    public void startTickCounter() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            currentTick++;
        }, 1L, 1L);
    }

    public int getCurrentTick() {
        return currentTick;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();

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
        startTickCounter();
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
}