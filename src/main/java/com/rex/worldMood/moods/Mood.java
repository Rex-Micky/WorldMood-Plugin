package com.rex.worldMood.moods;

import com.rex.worldMood.WorldMood;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Abstract base class for all World Moods.
 */
public abstract class Mood {

    protected final WorldMood plugin;
    protected final String configKey;
    protected boolean enabled;
    protected int weight;
    protected int duration;

    public Mood(WorldMood plugin, String configKey) {
        this.plugin = plugin;
        this.configKey = configKey;
    }
    protected void loadConfigValues() {
        FileConfiguration config = plugin.getConfig();
        String path = "moods." + this.configKey;

        this.enabled = config.getBoolean(path + ".enabled", false);
        this.weight = config.getInt(path + ".weight", 1);
        this.duration = config.getInt(path + ".duration", plugin.getConfig().getInt("defaultMoodDuration", 300));
    }
    public String getConfigKey() {
        return configKey;
    }
    public abstract String getName();

    public abstract String getDescription();

    public abstract List<String> getEffects();

    public abstract void apply();

    public abstract void remove();

    public int getWeight() {
        return Math.max(0, weight);
    }

    public int getDuration() {
        return Math.max(1, duration);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Counts invocations of {@link #tick(long)} for the CURRENT activation of this mood.
     * <p>
     * MoodManager calls {@code tick} once per second, so this is effectively "seconds elapsed".
     * Moods must throttle off this counter, never off a free-running server tick counter: because
     * {@code tick} is only invoked on a stride of 20 ticks, a global counter is always the same
     * value modulo any divisor of 20, so a gate like {@code globalTick % 10 == 0} is either always
     * true or always false for the whole lifetime of the server, decided by scheduling phase.
     * That bug silently disabled the BloodMoon and InfernalHeat effects.
     */
    protected long secondsElapsed = 0;

    /** Invoked by MoodManager once per second. Keeps the counter honest for every subclass. */
    public final void handleTick(long ticksRemaining) {
        secondsElapsed++;
        tick(ticksRemaining);
    }

    /** Clears per-activation state. MoodManager calls this immediately before {@link #apply()}. */
    public void resetActivationState() {
        secondsElapsed = 0;
    }

    public void tick(long ticksRemaining) {
    }

    public void onPlayerJoin(Player player) {
    }

    public void onPlayerQuit(Player player) {
    }

    protected ConfigurationSection getMoodConfigSection() {
        FileConfiguration config = plugin.getConfig();
        String path = "moods." + configKey;
        if (config.isConfigurationSection(path)) {
            return config.getConfigurationSection(path);
        }
        return null;
    }

    public boolean requiresNight() {
        return false;
    }

    public boolean requiresDay() {
        return false;
    }
}