package com.rex.worldMood;

import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * SPIKE: temporarily recolours the fog around players by swapping the local biome to a custom
 * datapack biome (red for Blood Moon, etc.), recording every cell it touches so it can put them
 * back.
 * <p>
 * This is the risky Phase-2 mechanism, deliberately minimal here — no disk-backed crash safety
 * yet — purely to prove the fog actually recolours on a live client before that machinery gets
 * built. Biomes are stored in 4x4x4 cells; we only touch a Y-band around the camera because the
 * client derives fog from the biome at the camera position.
 */
public final class BiomeFog {

    private static final Logger LOG = Logger.getLogger("WorldMood");

    private BiomeFog() {
    }

    /** One recorded cell: where it is and what biome it was before we changed it. */
    public static final class Cell {
        final int x, y, z;
        final Biome original;
        Cell(int x, int y, int z, Biome original) { this.x = x; this.y = y; this.z = z; this.original = original; }
    }

    /** Resolves a datapack biome by key, or null if it isn't registered (e.g. on legacy servers). */
    public static Biome biome(String key) {
        try {
            NamespacedKey nk = NamespacedKey.fromString(key);
            return nk == null ? null : Registry.BIOME.get(nk);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Swaps the biome to {@code fog} in a box of chunks around the player and resends them.
     * Returns the recorded originals, to be passed to {@link #restore}.
     */
    public static List<Cell> apply(Player player, Biome fog, int radiusChunks, int yBand) {
        List<Cell> touched = new ArrayList<>();
        if (fog == null) return touched;

        World world = player.getWorld();
        Chunk centre = player.getLocation().getChunk();
        int camY = player.getLocation().getBlockY();
        int minY = Math.max(world.getMinHeight(), camY - yBand);
        int maxY = Math.min(world.getMaxHeight() - 1, camY + yBand);

        for (int dcx = -radiusChunks; dcx <= radiusChunks; dcx++) {
            for (int dcz = -radiusChunks; dcz <= radiusChunks; dcz++) {
                int cx = centre.getX() + dcx, cz = centre.getZ() + dcz;
                int bx = cx << 4, bz = cz << 4;
                for (int x = bx; x < bx + 16; x += 4) {
                    for (int z = bz; z < bz + 16; z += 4) {
                        for (int y = minY; y <= maxY; y += 4) {
                            Biome cur = world.getBiome(x, y, z);
                            if (cur == fog) continue;
                            touched.add(new Cell(x, y, z, cur));
                            world.setBiome(x, y, z, fog);
                        }
                    }
                }
                resend(world, cx, cz);
            }
        }
        LOG.info("[BiomeFog] recoloured " + touched.size() + " biome cells around " + player.getName());
        return touched;
    }

    /** Restores every recorded cell to its original biome and resends the affected chunks. */
    public static void restore(World world, List<Cell> touched) {
        if (touched == null || touched.isEmpty()) return;
        Set<Long> chunks = new HashSet<>();
        for (Cell c : touched) {
            world.setBiome(c.x, c.y, c.z, c.original);
            chunks.add((((long) (c.x >> 4)) << 32) | ((c.z >> 4) & 0xFFFFFFFFL));
        }
        for (long ck : chunks) {
            resend(world, (int) (ck >> 32), (int) ck);
        }
        LOG.info("[BiomeFog] restored " + touched.size() + " biome cells");
    }

    /** refreshChunk pushes fresh chunk data (including biomes) to nearby players. */
    @SuppressWarnings("deprecation")
    private static void resend(World world, int cx, int cz) {
        try {
            world.refreshChunk(cx, cz);
        } catch (Throwable ignored) {
        }
    }
}
