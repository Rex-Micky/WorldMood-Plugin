package com.rex.worldMood;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Transient, per-player atmosphere helpers for the immersive mood effects.
 * <p>
 * Everything here is sent to a single player via {@code player.spawnParticle} / potion effects
 * that expire on their own. Nothing is written to the world, so none of it needs crash-safety —
 * the moment a mood ends (or the player logs off) the effects simply stop.
 * <p>
 * All methods are null-safe on the {@link Particle} / {@link PotionEffectType} argument, because
 * those come from {@link Compat} and can be null on older server versions.
 */
public final class Atmosphere {

    private Atmosphere() {
    }

    /**
     * Particles that turned out to require extra data (BlockData, Float, etc.) on this server
     * version. Different Minecraft versions attach different data requirements to the same
     * particle, so rather than hard-code which need what, we try once, and if the server rejects
     * the plain call we remember it and never spawn it again — a purely cosmetic haze must never
     * crash a tick.
     */
    private static final Set<Particle> NEEDS_DATA = ConcurrentHashMap.newKeySet();
    private static final Logger LOG = Logger.getLogger("WorldMood");

    /**
     * Scatters coloured dust in a box around the player to create a drifting haze.
     * Sent only to that player, so it fills their view without touching anyone else's.
     */
    public static void dustHaze(Player player, Color color, float size, int count, double radius) {
        if (player == null || Compat.DUST == null || count <= 0) return;
        Location centre = player.getEyeLocation();
        player.spawnParticle(Compat.DUST, centre, count, radius, radius * 0.7, radius, 0.0,
                new Particle.DustOptions(color, size));
    }

    /** Scatters a plain particle in a box around the player (client-side to them only). */
    public static void haze(Player player, Particle particle, int count, double radius, double extra) {
        if (player == null || particle == null || count <= 0 || NEEDS_DATA.contains(particle)) return;
        try {
            player.spawnParticle(particle, player.getEyeLocation(), count, radius, radius * 0.7, radius, extra);
        } catch (IllegalArgumentException e) {
            NEEDS_DATA.add(particle);
            LOG.warning("[Atmosphere] Particle " + particle + " needs extra data on this version; "
                    + "leaving it out of the ambient haze.");
        }
    }

    /**
     * A few particles drifting DOWN from above the player — reads as falling embers / ash / rain,
     * which sells "weather" far better than particles spawned at eye level.
     */
    public static void fallingMotes(Player player, Particle particle, int count, double radius) {
        if (player == null || particle == null || count <= 0) return;
        if (NEEDS_DATA.contains(particle)) return;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Location base = player.getLocation();
        try {
            for (int i = 0; i < count; i++) {
                Location at = base.clone().add(
                        (rnd.nextDouble() - 0.5) * 2 * radius,
                        4 + rnd.nextDouble() * 4,
                        (rnd.nextDouble() - 0.5) * 2 * radius);
                player.spawnParticle(particle, at, 0, 0, -0.35, 0, 0.12); // downward velocity
            }
        } catch (IllegalArgumentException e) {
            NEEDS_DATA.add(particle);
            LOG.warning("[Atmosphere] Particle " + particle + " needs extra data on this version; "
                    + "leaving it out of the falling motes.");
        }
    }

    /**
     * Applies a short potion "pulse" to a player, refreshed so it never fully drops between calls.
     * No-ops when the effect type is unavailable on this version (Compat returns null there).
     */
    public static void pulse(Player player, PotionEffectType type, int durationTicks, int amplifier) {
        if (player == null || type == null) return;
        player.addPotionEffect(new PotionEffect(type, durationTicks, amplifier, false, false, false), true);
    }

    /** Plays an ambient sound to a single player at their location. */
    public static void ambient(Player player, Sound sound, float volume, float pitch) {
        if (player == null || sound == null) return;
        player.playSound(player.getLocation(), sound, SoundCategory.AMBIENT, volume, pitch);
    }
}
