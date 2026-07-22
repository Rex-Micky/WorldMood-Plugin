package com.rex.worldMood;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Owns the live "coloured fog" session for whichever mood is active.
 * <p>
 * The fog is produced by swapping the biome of 4×4×4 cells around each player to a custom datapack
 * biome ({@code worldmood:blood_moon} etc.), which recolours the client's fog. That change lives in
 * the world's save data, so it is <b>crash-persistent</b>: every swap is recorded to disk (via
 * {@link WorldStateGuard}) <i>before</i> it happens, and {@link WorldStateGuard#restorePending()}
 * puts everything back if the server dies mid-mood. Without that, a crash during a Blood Moon would
 * leave a permanently red-fogged world.
 * <p>
 * The tinted region is a moving bubble: a periodic sweep tints cells players walk into and restores
 * cells they leave behind, which keeps the effect following the player and bounds how much world
 * state (and how much of the crash-recovery record) is ever outstanding.
 * <p>
 * On legacy servers a custom biome cannot be resolved ({@link BiomeFog#biome(String)} is null), so
 * {@link #begin(String)} no-ops and the mood runs with its Tier-1 effects only.
 */
public final class FogController {

    // Tuned in FogController so all three moods share one honest, tested footprint.
    private static final int RADIUS_CHUNKS = 3;   // horizontal reach of the tint around a player
    private static final int Y_BAND = 12;         // ± blocks around the camera (fog is camera-height)
    private static final int PRUNE_RADIUS = RADIUS_CHUNKS + 2; // hysteresis: leave before restoring
    private static final long RETINT_PERIOD_TICKS = 40L;       // 2s — follow the player, catch joiners
    private static final int MAX_CELLS_PER_WORLD = 120_000;    // safety valve; the bubble stays well under

    /** The datapack files shipped in the modern jar, copied verbatim into the world on first enable. */
    private static final String[] DATAPACK_FILES = {
            "pack.mcmeta",
            "data/worldmood/worldgen/biome/blood_moon.json",
            "data/worldmood/worldgen/biome/void_tension.json",
            "data/worldmood/worldgen/biome/shadow_veil.json",
    };

    private final WorldMood plugin;
    private final WorldStateGuard guard;

    /** All biome keys this plugin ever swaps to — never recorded as a cell's "original". */
    private static final String[] OWN_BIOME_KEYS = {
            "worldmood:blood_moon", "worldmood:void_tension", "worldmood:shadow_veil",
    };

    private String activeKey;
    private Biome activeBiome;
    // world UID -> (packed cell key -> recorded cell). The authoritative in-memory mirror of disk.
    private final Map<UUID, Map<Long, BiomeFog.Cell>> tinted = new HashMap<>();
    // Our own fog biomes, compared by REFERENCE only: on 1.21.x Biome.equals/hashCode collapse all
    // biomes together, so a HashSet would match every vanilla biome. Registry singletons make == safe.
    private Biome[] ownBiomes;
    private BukkitTask sweepTask;
    private boolean warnedUnregistered;
    // True only where the bundled datapack's biome schema is known to load: the modern jar on MC 1.21+.
    private boolean datapackCapable;

    public FogController(WorldMood plugin, WorldStateGuard guard) {
        this.plugin = plugin;
        this.guard = guard;
    }

    /**
     * Starts recolouring the fog to the given datapack biome around every online player. No-ops (and
     * logs once) when the biome isn't registered — legacy servers, or a freshly-extracted datapack
     * that needs one restart. Safe to call even if a session is somehow already running.
     */
    public void begin(String biomeKey) {
        end(); // never stack sessions
        Biome biome = BiomeFog.biome(biomeKey);
        if (biome == null) {
            if (!warnedUnregistered) {
                if (datapackCapable) {
                    // Modern jar on 1.21+: the datapack ships but its biomes register only at world load.
                    plugin.getLogger().info("[Fog] '" + biomeKey + "' isn't registered yet — restart the "
                            + "server once so the bundled fog datapack loads. Moods run without coloured fog "
                            + "until then.");
                } else {
                    // Legacy jar, or a modern jar on 1.20.x where the datapack schema can't load.
                    plugin.getLogger().info("[Fog] Coloured fog isn't available on this server version "
                            + "(needs MC 1.21+); the mood runs with all its other effects.");
                }
                warnedUnregistered = true;
            }
            return;
        }
        this.activeKey = biomeKey;
        this.activeBiome = biome;
        sweep(Bukkit.getOnlinePlayers());
        int total = 0;
        for (Map<Long, BiomeFog.Cell> m : tinted.values()) total += m.size();
        plugin.getLogger().info("[Fog] " + biomeKey + " active — tinted " + total + " biome cells around "
                + Bukkit.getOnlinePlayers().size() + " player(s).");
        sweepTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (activeBiome == null) {
                    cancel();
                    return;
                }
                sweep(Bukkit.getOnlinePlayers());
            }
        }.runTaskTimer(plugin, RETINT_PERIOD_TICKS, RETINT_PERIOD_TICKS);
    }

    /** Stops the session and restores every tinted cell to its original biome, clearing the disk record. */
    public void end() {
        if (sweepTask != null) {
            sweepTask.cancel();
            sweepTask = null;
        }
        activeBiome = null;
        activeKey = null;
        if (tinted.isEmpty()) {
            guard.clearFog();
            return;
        }
        for (Map.Entry<UUID, Map<Long, BiomeFog.Cell>> e : tinted.entrySet()) {
            World world = Bukkit.getWorld(e.getKey());
            if (world == null) continue;
            Set<Long> chunks = new HashSet<>();
            for (BiomeFog.Cell c : e.getValue().values()) {
                world.setBiome(c.x, c.y, c.z, c.original);
                chunks.add(chunkKey(c.x >> 4, c.z >> 4));
            }
            refreshAll(world, chunks);
            plugin.getLogger().info("[Fog] Restored " + e.getValue().size() + " biome cells in world '"
                    + world.getName() + "'.");
        }
        tinted.clear();
        guard.clearFog();
    }

    /**
     * One bubble step: restore cells everyone has walked away from, tint cells they've walked into.
     * Ordering is dictated by crash-safety — see the numbered steps below.
     */
    private void sweep(Collection<? extends Player> players) {
        if (activeBiome == null) return;

        // Group online players by world so distance checks and edits are per-world.
        Map<UUID, List<int[]>> playerChunksByWorld = new HashMap<>();
        for (Player p : players) {
            World w = p.getWorld();
            if (w.getEnvironment() != World.Environment.NORMAL) continue;
            playerChunksByWorld.computeIfAbsent(w.getUID(), k -> new ArrayList<>())
                    .add(new int[]{p.getLocation().getBlockX() >> 4, p.getLocation().getBlockZ() >> 4,
                            p.getLocation().getBlockY()});
        }

        Set<UUID> worlds = new HashSet<>();
        worlds.addAll(tinted.keySet());
        worlds.addAll(playerChunksByWorld.keySet());

        for (UUID worldId : worlds) {
            World world = Bukkit.getWorld(worldId);
            if (world == null) continue;
            List<int[]> playerChunks = playerChunksByWorld.getOrDefault(worldId, java.util.Collections.emptyList());
            Map<Long, BiomeFog.Cell> cells = tinted.computeIfAbsent(worldId, k -> new LinkedHashMap<>());

            // Compute the two edit sets first, reading originals for new cells before any swap.
            List<BiomeFog.Cell> toPrune = collectPrunable(cells, playerChunks);
            List<BiomeFog.Cell> toAdd = collectNew(world, cells, playerChunks);
            if (toPrune.isEmpty() && toAdd.isEmpty()) continue;

            Set<Long> chunks = new HashSet<>();

            // 1. Restore pruned cells on disk BEFORE dropping them from the record (a crash here just
            //    re-restores them next startup — harmless — whereas dropping first could strand fog).
            for (BiomeFog.Cell c : toPrune) {
                world.setBiome(c.x, c.y, c.z, c.original);
                cells.remove(BiomeFog.cellKey(c.x, c.y, c.z));
                chunks.add(chunkKey(c.x >> 4, c.z >> 4));
            }
            // 2. Add new cells to the in-memory record (not yet swapped on disk).
            for (BiomeFog.Cell c : toAdd) {
                cells.put(BiomeFog.cellKey(c.x, c.y, c.z), c);
            }
            // 3. Persist the record NOW — new cells are on disk as "originally X" before we change them.
            if (cells.isEmpty()) {
                guard.clearFogWorld(world);
            } else {
                guard.saveFog(world, cells.values());
            }
            // 4. Only now swap the new cells to the fog biome.
            for (BiomeFog.Cell c : toAdd) {
                world.setBiome(c.x, c.y, c.z, activeBiome);
                chunks.add(chunkKey(c.x >> 4, c.z >> 4));
            }
            // 5. Resend every touched chunk so clients see the change without relogging.
            refreshAll(world, chunks);
        }
    }

    /** Tinted cells whose chunk is beyond PRUNE_RADIUS of every online player in this world. */
    private List<BiomeFog.Cell> collectPrunable(Map<Long, BiomeFog.Cell> cells, List<int[]> playerChunks) {
        List<BiomeFog.Cell> out = new ArrayList<>();
        if (cells.isEmpty()) return out;
        for (BiomeFog.Cell c : cells.values()) {
            int cx = c.x >> 4, cz = c.z >> 4;
            if (playerChunks.isEmpty() || chebyshevToNearest(cx, cz, playerChunks) > PRUNE_RADIUS) {
                out.add(c);
            }
        }
        return out;
    }

    /** Untinted cells within RADIUS_CHUNKS/Y_BAND of a player, originals read here before any swap. */
    private List<BiomeFog.Cell> collectNew(World world, Map<Long, BiomeFog.Cell> cells, List<int[]> playerChunks) {
        List<BiomeFog.Cell> out = new ArrayList<>();
        if (playerChunks.isEmpty() || cells.size() >= MAX_CELLS_PER_WORLD) return out;
        int minWorldY = world.getMinHeight();
        int maxWorldY = world.getMaxHeight() - 1;
        Set<Long> seen = new HashSet<>();
        for (int[] pc : playerChunks) {
            for (int dcx = -RADIUS_CHUNKS; dcx <= RADIUS_CHUNKS; dcx++) {
                for (int dcz = -RADIUS_CHUNKS; dcz <= RADIUS_CHUNKS; dcz++) {
                    int cx = pc[0] + dcx, cz = pc[1] + dcz;
                    int bx = cx << 4, bz = cz << 4;
                    // Camera height for THIS player; align the y grid to multiples of 4 for clean dedup.
                    int camY = pc[2];
                    int minY = alignDown(Math.max(minWorldY, camY - Y_BAND));
                    int maxY = Math.min(maxWorldY, camY + Y_BAND);
                    for (int x = bx; x < bx + 16; x += 4) {
                        for (int z = bz; z < bz + 16; z += 4) {
                            for (int y = minY; y <= maxY; y += 4) {
                                long key = BiomeFog.cellKey(x, y, z);
                                if (cells.containsKey(key) || !seen.add(key)) continue;
                                Biome cur = world.getBiome(x, y, z);
                                // Never record one of our own fog biomes as an "original": that would
                                // happen only if a lost record left stray fog behind, and persisting it
                                // as the original would make the tint permanent.
                                if (isOwnBiome(cur)) continue;
                                out.add(new BiomeFog.Cell(x, y, z, cur));
                            }
                        }
                    }
                }
            }
        }
        return out;
    }

    /** True if {@code b} is one of our fog biomes, by reference (see the {@link #ownBiomes} note). */
    private boolean isOwnBiome(Biome b) {
        if (ownBiomes == null) {
            List<Biome> resolved = new ArrayList<>();
            for (String key : OWN_BIOME_KEYS) {
                Biome own = BiomeFog.biome(key);
                if (own != null) resolved.add(own);
            }
            ownBiomes = resolved.toArray(new Biome[0]);
        }
        for (Biome own : ownBiomes) {
            if (own == b) return true;
        }
        return false;
    }

    private static int chebyshevToNearest(int cx, int cz, List<int[]> playerChunks) {
        int best = Integer.MAX_VALUE;
        for (int[] pc : playerChunks) {
            int d = Math.max(Math.abs(cx - pc[0]), Math.abs(cz - pc[1]));
            if (d < best) best = d;
        }
        return best;
    }

    private static int alignDown(int y) {
        return Math.floorDiv(y, 4) * 4;
    }

    private static long chunkKey(int cx, int cz) {
        return (((long) cx) << 32) | (cz & 0xFFFFFFFFL);
    }

    private static void refreshAll(World world, Set<Long> chunks) {
        for (long ck : chunks) {
            BiomeFog.refreshChunk(world, (int) (ck >> 32), (int) ck);
        }
    }

    // ------------------------------------------------------------------
    // Datapack shipping (modern jar only)
    // ------------------------------------------------------------------

    /**
     * Copies the bundled fog datapack into the primary world's {@code datapacks/} folder on first
     * enable, then reports whether the fog biomes are live. The legacy jar does not ship the datapack,
     * so {@code getResource} returns null there and this simply no-ops.
     * <p>
     * The datapack uses the 1.21+ biome schema (carvers as a JSON array). On MC 1.20.x that schema
     * fails to load and aborts world load — a hard crash — so we install it <b>only on MC 1.21+</b>.
     * Older modern servers (1.20.x) keep every other effect and simply run without coloured fog.
     * <p>
     * Custom biomes only register when the world loads, so a datapack written this session needs one
     * restart. We detect that (biome still unresolved) and tell the owner rather than silently doing
     * nothing.
     */
    public void installDatapack() {
        InputStream probe = plugin.getResource("fog-datapack/pack.mcmeta");
        if (probe == null) {
            closeQuietly(probe);
            return; // legacy jar — no datapack shipped, fog stays off by design
        }
        closeQuietly(probe);

        if (!serverSupportsFogSchema()) {
            plugin.getLogger().info("[Fog] Coloured fog needs MC 1.21+ (its datapack biome format does "
                    + "not load on older servers); skipping the datapack on " + Bukkit.getBukkitVersion()
                    + ". Every other mood effect still works.");
            return; // datapackCapable stays false — begin() will explain to players/admins
        }
        datapackCapable = true;

        World primary = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        if (primary == null) {
            plugin.getLogger().warning("[Fog] No world loaded; cannot install the fog datapack.");
            return;
        }
        File target = new File(primary.getWorldFolder(), "datapacks/worldmood_fog");
        boolean freshlyWritten = false;
        if (!target.exists()) {
            try {
                for (String rel : DATAPACK_FILES) {
                    File dest = new File(target, rel);
                    File parent = dest.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        throw new IOException("could not create " + parent);
                    }
                    try (InputStream in = plugin.getResource("fog-datapack/" + rel)) {
                        if (in == null) throw new IOException("missing bundled resource " + rel);
                        Files.copy(in, dest.toPath());
                    }
                }
                freshlyWritten = true;
                plugin.getLogger().info("[Fog] Installed the coloured-fog datapack to "
                        + target.getPath() + ".");
            } catch (IOException e) {
                plugin.getLogger().warning("[Fog] Could not install the fog datapack: " + e.getMessage()
                        + " — coloured fog will be unavailable, but every mood still works.");
                return;
            }
        }

        if (BiomeFog.biome("worldmood:blood_moon") == null) {
            plugin.getLogger().warning("[Fog] Coloured-fog datapack is "
                    + (freshlyWritten ? "installed" : "present") + " but its biomes are not registered yet."
                    + " Restart the server once to enable coloured fog. Until then moods run without it.");
        } else {
            plugin.getLogger().info("[Fog] Coloured-fog biomes are registered and ready.");
        }
    }

    /**
     * Whether this server's biome-datapack format matches the bundled datapack (MC 1.21+). Parsed
     * from {@link Bukkit#getBukkitVersion()} (e.g. {@code "1.21.11-R0.1-SNAPSHOT"}); an unrecognised
     * string is treated as unsupported so we never risk shipping an incompatible datapack.
     */
    private boolean serverSupportsFogSchema() {
        try {
            String[] parts = Bukkit.getBukkitVersion().split("-")[0].split("\\.");
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            return major > 1 || (major == 1 && minor >= 21);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static void closeQuietly(InputStream in) {
        if (in == null) return;
        try {
            in.close();
        } catch (IOException ignored) {
        }
    }
}
