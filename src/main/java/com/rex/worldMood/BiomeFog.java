package com.rex.worldMood;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Biome;

import java.util.Locale;

/**
 * Low-level, version-defensive helpers for the biome-fog mechanism. The stateful session
 * (which biome, which cells, crash-safe persistence, re-tinting) lives in {@link FogController};
 * this class only knows how to resolve a datapack biome, read a biome's key, pack a cell
 * coordinate, and resend a chunk.
 * <p>
 * Everything here is defensive: on servers whose {@code Biome} is still the old fixed enum
 * (the legacy 1.16.5–1.19 jar) a custom datapack biome cannot be represented, so
 * {@link #biome(String)} returns {@code null} and the whole fog feature simply no-ops.
 */
public final class BiomeFog {

    private BiomeFog() {
    }

    /** One recorded cell: where it is (block coords on the 4-grid) and its biome before we changed it. */
    public static final class Cell {
        final int x, y, z;
        final Biome original;

        Cell(int x, int y, int z, Biome original) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.original = original;
        }
    }

    /**
     * Resolves a biome by key via the runtime registry, or {@code null} if it isn't registered
     * (a custom datapack biome that hasn't loaded yet, or any custom biome on a legacy server).
     * Also used to resolve the vanilla originals during crash recovery.
     */
    public static Biome biome(String key) {
        try {
            NamespacedKey nk = NamespacedKey.fromString(key);
            return nk == null ? null : Registry.BIOME.get(nk);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * The namespaced key of a biome as a string (e.g. {@code "minecraft:forest"}), resolved
     * reflectively so the shared source compiles against both the enum-era and registry-era APIs.
     * Only ever called on servers where fog actually applied (modern), but must compile everywhere.
     */
    public static String keyOf(Biome biome) {
        if (biome == null) return null;
        try {
            Object key = biome.getClass().getMethod("getKey").invoke(biome);
            if (key != null) return key.toString();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Very old API without Keyed on Biome — fall through to the enum name.
        }
        try {
            return "minecraft:" + biome.toString().toLowerCase(Locale.ROOT);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Packs a cell's block coordinates into a single long for de-duplication, using the same
     * 26/12/26-bit layout as vanilla {@code BlockPos}. Cells sit on a 4-block grid but the full
     * coordinate is packed so no information is lost.
     */
    public static long cellKey(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFFL);
    }

    /** refreshChunk pushes fresh chunk data (including biomes) to nearby players; no-op with no viewers. */
    @SuppressWarnings("deprecation")
    public static void refreshChunk(World world, int cx, int cz) {
        try {
            world.refreshChunk(cx, cz);
        } catch (Throwable ignored) {
            // Some server flavours dislike refreshing an unloaded chunk — never let cosmetics throw.
        }
    }
}
