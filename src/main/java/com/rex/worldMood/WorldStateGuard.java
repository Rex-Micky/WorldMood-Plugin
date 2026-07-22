package com.rex.worldMood;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Crash-safety net for <b>persistent</b> world state.
 * <p>
 * Some moods change settings that live in the world's own save data and therefore outlive the
 * plugin: {@code doMobSpawning} (CalmSkies), the world border (BloodMoon, VoidTension), and the
 * per-cell biomes used for coloured fog ({@link FogController}). Restoring those only in
 * {@code Mood.remove()} is not enough — if the server crashes, is killed, or throws partway through
 * removal, the change becomes permanent and invisible. A server could be left with mob spawning
 * switched off forever, or a permanently red-fogged world, long after WorldMood was uninstalled.
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

    // ------------------------------------------------------------------
    // Coloured-fog biome cells. There can be thousands, so they are stored
    // compactly: a small palette of original biome keys plus one Base64 blob
    // (x:int, z:int, y:short, paletteIndex:short per cell) rather than a
    // YAML node per cell. FogController calls this with the full current set
    // for a world whenever that set changes, keeping disk == memory.
    // ------------------------------------------------------------------
    private static final int BYTES_PER_CELL = 12;

    /** Records the complete set of tinted cells for a world. Call BEFORE swapping newly-added cells. */
    public void saveFog(World world, Collection<BiomeFog.Cell> cells) {
        if (cells == null || cells.isEmpty()) {
            clearFogWorld(world);
            return;
        }
        Map<String, Integer> paletteIndex = new LinkedHashMap<>();
        ByteBuffer buf = ByteBuffer.allocate(cells.size() * BYTES_PER_CELL);
        int written = 0;
        for (BiomeFog.Cell c : cells) {
            String key = BiomeFog.keyOf(c.original);
            if (key == null) continue; // unkeyable original — cannot be restored, so don't record it
            int idx = paletteIndex.computeIfAbsent(key, k -> paletteIndex.size());
            buf.putInt(c.x).putInt(c.z).putShort((short) c.y).putShort((short) idx);
            written++;
        }
        List<String> palette = new ArrayList<>(paletteIndex.keySet());
        String path = "fog." + world.getUID() + ".";
        data.set(path + "palette", palette);
        data.set(path + "data", Base64.getEncoder().encodeToString(
                java.util.Arrays.copyOf(buf.array(), written * BYTES_PER_CELL)));
        save();
    }

    /** Clears the fog record for one world once its cells have been restored normally. */
    public void clearFogWorld(World world) {
        data.set("fog." + world.getUID(), null);
        save();
    }

    /** Clears every fog record (used when a mood ends and all cells are restored). */
    public void clearFog() {
        if (data.getConfigurationSection("fog") != null) {
            data.set("fog", null);
            save();
        }
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

        restored += restoreFog();

        if (restored > 0) {
            data.set("gamerules", null);
            data.set("borders", null);
            data.set("fog", null);
            save();
        }
        return restored;
    }

    /**
     * Puts back every fog-tinted biome cell an unclean shutdown left behind. Originals are vanilla
     * biomes, always resolvable, so this works even if the custom fog datapack is gone. Returns the
     * number of cells restored (0 when nothing was pending).
     */
    private int restoreFog() {
        ConfigurationSection fog = data.getConfigurationSection("fog");
        if (fog == null) return 0;
        int restored = 0;
        for (String worldId : fog.getKeys(false)) {
            World world = worldFor(worldId);
            ConfigurationSection saved = fog.getConfigurationSection(worldId);
            if (world == null || saved == null) continue;

            List<String> palette = saved.getStringList("palette");
            String data64 = saved.getString("data");
            if (data64 == null || palette.isEmpty()) continue;

            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(data64);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Skipping a corrupt fog record for world '" + world.getName() + "'.");
                continue;
            }
            ByteBuffer buf = ByteBuffer.wrap(bytes);
            Set<Long> chunks = new HashSet<>();
            int cellsHere = 0;
            while (buf.remaining() >= BYTES_PER_CELL) {
                int x = buf.getInt();
                int z = buf.getInt();
                int y = buf.getShort();
                int idx = buf.getShort();
                if (idx < 0 || idx >= palette.size()) continue;
                Biome original = BiomeFog.biome(palette.get(idx));
                if (original == null) continue;
                world.setBiome(x, y, z, original);
                chunks.add((((long) (x >> 4)) << 32) | ((z >> 4) & 0xFFFFFFFFL));
                cellsHere++;
            }
            for (long ck : chunks) {
                BiomeFog.refreshChunk(world, (int) (ck >> 32), (int) ck);
            }
            if (cellsHere > 0) {
                plugin.getLogger().warning("Restored " + cellsHere + " fog biome cells in world '"
                        + world.getName() + "' after an unclean shutdown.");
                restored += cellsHere;
            }
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
