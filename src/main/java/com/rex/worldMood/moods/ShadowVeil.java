package com.rex.worldMood.moods;

import com.rex.worldMood.Atmosphere;
import com.rex.worldMood.Compat;
import com.rex.worldMood.WorldMood;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.SoundCategory;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class ShadowVeil extends Mood {

    private double effectChance;
    private int effectIntervalSeconds;
    private int blindnessDurationTicks;
    private int invisibilityDurationTicks;
    private boolean ambientHazeEnabled;
    private boolean fogRecolorEnabled;
    private final Random random = new Random();

    private static final Color HAZE_SHADOW = Color.fromRGB(38, 18, 54);   // deep creeping violet
    private static final Color HAZE_VIOLET = Color.fromRGB(74, 30, 96);

    /** In seconds — tick() is invoked once per second. */
    private static final int SPOOKY_EFFECT_INTERVAL_SECONDS = 2;
    private static final double SPOOKY_SOUND_CHANCE = 0.15;
    private static final double CAVE_DARKNESS_CHANCE = 0.03;
    private static final int CAVE_DARKNESS_DURATION = 40;
    // Both resolved once by Compat. DARKNESS is null before 1.19, which is the signal to fall
    // back to short blindness; SCULK_SHRIEK degrades to an older sound rather than going silent.
    private static final PotionEffectType darknessPotionEffectType = Compat.DARKNESS;
    private static final Sound sculkShriekSound = Compat.SCULK_SHRIEK;

    public ShadowVeil(WorldMood plugin) {
        super(plugin, "shadow_veil");
        loadConfigValues();
    }

    @Override
    public boolean requiresNight() {
        return true;
    }

    @Override
    protected void loadConfigValues() {
        super.loadConfigValues();

        ConfigurationSection moodConfig = getMoodConfigSection();
        if (moodConfig != null) {
            effectChance = moodConfig.getDouble("effectChance", 0.35);
            effectIntervalSeconds = moodConfig.getInt("effectIntervalSeconds", 10);
            blindnessDurationTicks = moodConfig.getInt("blindnessDurationSeconds", 5) * 20;
            invisibilityDurationTicks = moodConfig.getInt("invisibilityDurationSeconds", 10) * 20;
            ambientHazeEnabled = moodConfig.getBoolean("shadowHaze", true);
            fogRecolorEnabled = moodConfig.getBoolean("fogRecolor", true);
        } else {
            effectChance = 0.35;
            effectIntervalSeconds = 10;
            blindnessDurationTicks = 5 * 20;
            invisibilityDurationTicks = 10 * 20;
            ambientHazeEnabled = true;
            fogRecolorEnabled = true;
            plugin.getLogger().warning("[ShadowVeil] Configuration section missing. Using default values.");
        }
    }

    @Override
    public String getName() {
        return "Shadow Veil";
    }

    @Override
    public String getDescription() {
        return "Reality flickers as shadows deepen and writhe, causing brief moments of blindness or invisibility amidst unsettling whispers.";
    }

    @Override
    public List<String> getEffects() {
        String caveDarknessEffect = "Enhanced Effects in Darkness";
        if (darknessPotionEffectType != null) {
            caveDarknessEffect += " (Chance of True Darkness)";
        }
        return Arrays.asList(
                "Intermittent Blindness/Invisibility",
                "Spooky Ambient Sounds & Particles",
                caveDarknessEffect,
                ChatColor.GRAY + "(Mood lasts until Day)"
        );
    }

    @Override
    public void apply() {
        for(Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().getEnvironment() == World.Environment.NORMAL || p.getWorld().getEnvironment() == World.Environment.NETHER) {
                p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_STARE, SoundCategory.AMBIENT, 0.6f, 0.7f);
                p.playSound(p.getLocation(), Sound.AMBIENT_CAVE, SoundCategory.AMBIENT, 0.4f, 0.5f);
            }
        }
        // Recolour the fog a dark violet around each player (crash-safe; no-ops on legacy).
        if (fogRecolorEnabled) {
            plugin.getFogController().begin("worldmood:shadow_veil");
        }
    }

    @Override
    public void remove() {
        // Restore the recoloured fog biomes (safe to call even if fog was never applied).
        plugin.getFogController().end();
        for (Player player : Bukkit.getOnlinePlayers()) {
            PotionEffect blindness = player.getPotionEffect(Compat.BLINDNESS);
            if (blindness != null && blindness.getDuration() <= blindnessDurationTicks + 20 && blindness.getAmplifier() == 0) {
                player.removePotionEffect(Compat.BLINDNESS);
            }
            PotionEffect invis = player.getPotionEffect(Compat.INVISIBILITY);
            if (invis != null && invis.getDuration() <= invisibilityDurationTicks + 20 && invis.getAmplifier() == 0) {
                player.removePotionEffect(Compat.INVISIBILITY);
            }
            if (darknessPotionEffectType != null && player.hasPotionEffect(darknessPotionEffectType)) {
                PotionEffect dark = player.getPotionEffect(darknessPotionEffectType);
                if (dark != null && dark.getDuration() <= CAVE_DARKNESS_DURATION + 10 && dark.getAmplifier() == 0) {
                    player.removePotionEffect(darknessPotionEffectType);
                }
            }
        }
    }

    @Override
    public void tick(long ticksRemaining) {
        // tick() runs once per second, so secondsElapsed is directly comparable to the config value.

        // creeping violet haze + drifting ash + periodic true-darkness pulses (client-side, transient)
        if (ambientHazeEnabled) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getGameMode() == GameMode.SPECTATOR) continue;
                World.Environment env = player.getWorld().getEnvironment();
                if (env != World.Environment.NORMAL && env != World.Environment.NETHER) continue;
                Atmosphere.dustHaze(player, HAZE_SHADOW, 2.0f, 26, 7.0);
                Atmosphere.dustHaze(player, HAZE_VIOLET, 1.5f, 10, 4.5);
                Atmosphere.fallingMotes(player, Compat.ASH, 5, 6.0);
                // a slow pulse of oppressive dark every ~8s (real Darkness on 1.19+, else short blindness)
                if (secondsElapsed % 8 == 0) {
                    Atmosphere.pulse(player, darknessPotionEffectType != null ? darknessPotionEffectType : Compat.BLINDNESS,
                            darknessPotionEffectType != null ? 90 : 30, 0);
                }
            }
        }

        if (effectIntervalSeconds > 0 && secondsElapsed % effectIntervalSeconds == 0) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.isDead() || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) continue;
                if (player.getWorld().getEnvironment() != World.Environment.NORMAL && player.getWorld().getEnvironment() != World.Environment.NETHER) continue;

                if (random.nextDouble() < effectChance) {
                    boolean applyBlindness = random.nextBoolean();
                    if (applyBlindness) {
                        PotionEffect blindEffect = new PotionEffect(Compat.BLINDNESS, blindnessDurationTicks, 0, true, false, true);
                        player.addPotionEffect(blindEffect, true);
                        player.getWorld().spawnParticle(Compat.SQUID_INK, player.getEyeLocation(), 20, 0.3, 0.3, 0.3, 0.03);
                        player.playSound(player.getLocation(), Sound.ENTITY_GHAST_SCREAM, SoundCategory.PLAYERS, 0.25f, 1.9f);
                    } else {
                        PotionEffect invisEffect = new PotionEffect(Compat.INVISIBILITY, invisibilityDurationTicks, 0, true, true, true);
                        player.addPotionEffect(invisEffect, true);
                        player.getWorld().spawnParticle(Compat.SMOKE, player.getLocation().add(0, 1, 0), 15, 0.4, 0.7, 0.4, 0.02);
                        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.4f, 1.6f);
                    }
                }
            }
        }

        if (secondsElapsed % SPOOKY_EFFECT_INTERVAL_SECONDS == 0) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR) continue;
                if (player.getWorld().getEnvironment() != World.Environment.NORMAL && player.getWorld().getEnvironment() != World.Environment.NETHER) continue;

                World world = player.getWorld();
                Location loc = player.getLocation();

                if (random.nextDouble() < SPOOKY_SOUND_CHANCE) {
                    Sound soundToPlay;
                    float pitch = 0.5f + random.nextFloat() * 0.3f;
                    float volume = 0.25f + random.nextFloat() * 0.25f;
                    int soundChoice = random.nextInt(5);

                    switch(soundChoice) {
                        case 0: soundToPlay = Sound.AMBIENT_CAVE; break;
                        case 1: soundToPlay = Sound.ENTITY_ENDERMAN_AMBIENT; volume *= 0.7f; break;
                        case 2:
                            if (darknessPotionEffectType != null && Compat.WARDEN_AMBIENT != null) {
                                soundToPlay = Compat.WARDEN_AMBIENT; volume *= 0.15f; pitch *= 0.7f;
                            } else {
                                soundToPlay = Sound.AMBIENT_BASALT_DELTAS_MOOD;
                            }
                            break;
                        case 3: soundToPlay = Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE; volume *= 0.5f; pitch = 0.5f + random.nextFloat() * 0.2f; break;
                        case 4: default: soundToPlay = Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD; volume *= 0.4f; break;
                    }
                    world.playSound(loc, soundToPlay, SoundCategory.AMBIENT, volume, pitch);
                }

                Block blockAtPlayer = loc.getBlock();
                if (blockAtPlayer.getLightLevel() < 6) {
                    int particleCount = 1 + random.nextInt(2);
                    double spread = 0.7;
                    // Particle.DustTransition is 1.17+, so it is built reflectively and comes back
                    // null on older servers; those simply always take the ASH branch below.
                    Object dustTransition = Compat.dustTransition(
                            Color.fromRGB(25, 25, 35), Color.fromRGB(5, 5, 10), 0.8f + random.nextFloat() * 0.4f);
                    if (random.nextBoolean() && dustTransition != null && Compat.DUST_COLOR_TRANSITION != null) {
                        Compat.spawn(world, Compat.DUST_COLOR_TRANSITION, loc.add(random.nextGaussian()*0.5, 1 + random.nextDouble()*0.5, random.nextGaussian()*0.5), particleCount, spread, spread, spread, 0, dustTransition);
                    } else {
                        Compat.spawn(world, Compat.ASH, loc.add(random.nextGaussian()*0.5, 0.8 + random.nextDouble()*0.5, random.nextGaussian()*0.5), particleCount, spread*0.8, spread*0.5, spread*0.8, 0.01);
                    }

                    if (darknessPotionEffectType != null && random.nextDouble() < CAVE_DARKNESS_CHANCE) {
                        PotionEffect darknessEffect = new PotionEffect(darknessPotionEffectType, CAVE_DARKNESS_DURATION, 0, true, false, false);
                        player.addPotionEffect(darknessEffect, true);
                        world.playSound(loc, sculkShriekSound, SoundCategory.AMBIENT, 0.6f, 0.7f + random.nextFloat() * 0.2f);
                    } else if (darknessPotionEffectType == null && random.nextDouble() < CAVE_DARKNESS_CHANCE / 2) {
                        PotionEffect shortBlind = new PotionEffect(Compat.BLINDNESS, CAVE_DARKNESS_DURATION / 2, 0, true, false, false);
                        player.addPotionEffect(shortBlind, true);
                        world.playSound(loc, Sound.BLOCK_STONE_BUTTON_CLICK_ON, SoundCategory.AMBIENT, 0.5f, 0.6f);
                    }
                }
            }
        }
    }

    @Override
    public void onPlayerJoin(Player player) {
        if (player.getWorld().getEnvironment() == World.Environment.NORMAL || player.getWorld().getEnvironment() == World.Environment.NETHER) {
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_STARE, SoundCategory.AMBIENT, 0.5f, 0.8f);
        }
    }

    @Override
    public void onPlayerQuit(Player player) {
    }
}