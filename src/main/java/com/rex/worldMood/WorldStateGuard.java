package com.rex.worldMood;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * Crash-safety net for <b>persistent</b> world state.
 * <p>
 * Some moods change settings that live in the world's own save data and therefore outlive the
 * plugin: {@code doMobSpawning} (CalmSkies) and the world border (BloodMoon, VoidTension).
 * Restoring those only in {@code Mood.remove()} is not enough — if the server crashes, is killed,
 * or throws partway through removal, the change becomes permanent and invisible. A server could be
 * left with mob spawning switched off forever, long after WorldMood was uninstalled.
 * <p>
 * So every such change is written to disk <i>before</i> it is applied, and cleared once it has been
 * undone. On startup {@link #restorePending()} puts back anything an unclean shutdown left behind.
 */
public class WorldStateGuard {

    private static final String FILE_NAME = "pending-world-state.yml";

    private final WorldMood plugin;
    private final File file;
    private final YamlConfiguration data;

    public WorldStateGuard(WorldMood plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), FILE_NAME);
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    /** Records a boolean game rule's original value. Call immediately BEFORE changing it. */
    public void recordGameRule(World world, GameRule<Boolean> rule, boolean originalValue) {
        data.set("gamerules." + world.getUID() + "." + rule.getName(), originalValue);
        save();
    }

    /** Clears the record once the rule has been restored normally. */
    public void clearGameRule(World world, GameRule<Boolean> rule) {
        data.set("gamerules." + world.getUID() + "." + rule.getName(), null);
        save();
    }

    /** Snapshots the world border as it is right now. Call immediately BEFORE changing it. */
    public void recordBorder(World world) {
        WorldBorder border = world.getWorldBorder();
        String path = "borders." + world.getUID() + ".";
        data.set(path + "centerX", border.getCenter().getX());
        data.set(path + "centerZ", border.getCenter().getZ());
        data.set(path + "size", border.getSize());
        data.set(path + "damageBuffer", border.getDamageBuffer());
        data.set(path + "damageAmount", border.getDamageAmount());
        data.set(path + "warningTime", border.getWarningTime());
        data.set(path + "warningDistance", border.getWarningDistance());
        save();
    }

    /** Clears the record once the border has been restored normally. */
    public void clearBorder(World world) {
        data.set("borders." + world.getUID(), null);
        save();
    }

    /**
     * Restores anything left behind by an unclean shutdown, then wipes the record.
     * Safe (and cheap) to call when nothing is pending.
     *
     * @return how many settings were put back
     */
    public int restorePending() {
        int restored = 0;

        ConfigurationSection rules = data.getConfigurationSection("gamerules");
        if (rules != null) {
            for (String worldId : rules.getKeys(false)) {
                World world = worldFor(worldId);
                ConfigurationSection perWorld = rules.getConfigurationSection(worldId);
                if (world == null || perWorld == null) continue;

                for (String ruleName : perWorld.getKeys(false)) {
                    GameRule<?> rule = GameRule.getByName(ruleName);
                    if (rule == null || rule.getType() != Boolean.class) continue;

                    @SuppressWarnings("unchecked")
                    GameRule<Boolean> booleanRule = (GameRule<Boolean>) rule;
                    world.setGameRule(booleanRule, perWorld.getBoolean(ruleName));
                    plugin.getLogger().warning("Restored game rule " + ruleName + " in world '"
                            + world.getName() + "' after an unclean shutdown.");
                    restored++;
                }
            }
        }

        ConfigurationSection borders = data.getConfigurationSection("borders");
        if (borders != null) {
            for (String worldId : borders.getKeys(false)) {
                World world = worldFor(worldId);
                ConfigurationSection saved = borders.getConfigurationSection(worldId);
                if (world == null || saved == null) continue;

                WorldBorder border = world.getWorldBorder();
                border.setCenter(saved.getDouble("centerX"), saved.getDouble("centerZ"));
                border.setSize(saved.getDouble("size"));
                border.setDamageBuffer(saved.getDouble("damageBuffer"));
                border.setDamageAmount(saved.getDouble("damageAmount"));
                border.setWarningTime(saved.getInt("warningTime"));
                border.setWarningDistance(saved.getInt("warningDistance"));
                plugin.getLogger().warning("Restored the world border in world '" + world.getName()
                        + "' after an unclean shutdown.");
                restored++;
            }
        }

        if (restored > 0) {
            data.set("gamerules", null);
            data.set("borders", null);
            save();
        }
        return restored;
    }

    private World worldFor(String uid) {
        try {
            return Bukkit.getWorld(UUID.fromString(uid));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Ignoring malformed world id in " + FILE_NAME + ": " + uid);
            return null;
        }
    }

    private void save() {
        File folder = plugin.getDataFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().severe("Could not create the plugin data folder — world settings are "
                    + "NOT protected against a crash this session.");
            return;
        }
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not write " + FILE_NAME + " — world settings are NOT "
                    + "protected against a crash this session: " + e.getMessage());
        }
    }
}
