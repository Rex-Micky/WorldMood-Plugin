package com.rex.worldMood.moods;

import com.rex.worldMood.WorldMood;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.ChatColor;
import org.bukkit.SoundCategory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CalmSkies extends Mood implements Listener {
    private final Map<UUID, Boolean> originalMobSpawningRules = new HashMap<>();
    private boolean configDisableMobSpawning;
    private int regenAmplifier;

    private static final int REGEN_DURATION_TICKS = 120 * 20;

    public CalmSkies(WorldMood plugin) {
        super(plugin, "calm_skies");
        loadConfigValues();
    }

    @Override
    protected void loadConfigValues() {
        super.loadConfigValues();

        ConfigurationSection moodConfig = getMoodConfigSection();
        if (moodConfig != null) {
            configDisableMobSpawning = moodConfig.getBoolean("disableMobSpawning", true);
            regenAmplifier = moodConfig.getInt("passiveRegenAmplifier", 0);
            if (regenAmplifier < 0) {
                regenAmplifier = 0;
                plugin.getLogger().warning("[CalmSkies] passiveRegenAmplifier was negative, set to 0.");
            }
        } else {
            configDisableMobSpawning = true;
            regenAmplifier = 0;
            plugin.getLogger().warning("[CalmSkies] Configuration section missing. Using default values.");
        }
    }

    @Override
    public String getName() {
        return "Calm Skies";
    }

    @Override
    public String getDescription() {
        return "A peaceful aura settles over the world. Hostile creatures hesitate to appear, and players feel invigorated.";
    }

    @Override
    public List<String> getEffects() {
        return Arrays.asList(
                "Temporary Regeneration (Level " + (regenAmplifier + 1) + ", 2 mins)",
                configDisableMobSpawning ? "Hostile Mob Spawning Disabled" : "Peaceful Environment",
                ChatColor.GRAY + "(Mood lasts until Night/Day)"
        );
    }

    @Override
    public void apply() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        originalMobSpawningRules.clear();

        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == World.Environment.NORMAL) {
                if (configDisableMobSpawning) {
                    // Store original value before changing, using world UUID as key
                    originalMobSpawningRules.put(world.getUID(), world.getGameRuleValue(GameRule.DO_MOB_SPAWNING));
                    world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
                }
                world.setStorm(false);
                world.setThundering(false);
            }
        }

        PotionEffect timedRegenEffect = new PotionEffect(
                PotionEffectType.REGENERATION,
                REGEN_DURATION_TICKS,
                regenAmplifier,
                true,
                false,
                true
        );

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.removePotionEffect(PotionEffectType.REGENERATION);
            player.addPotionEffect(timedRegenEffect);
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.6f, 1.5f);
            player.sendMessage(ChatColor.GREEN + "A calming, regenerative aura washes over you...");
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                if (plugin.getMoodManager().getCurrentMood() != CalmSkies.this) {
                    this.cancel();
                    return;
                }
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getWorld().getEnvironment() == World.Environment.NORMAL && p.getLocation().getBlock().getLightFromSky() > 10) {
                        p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getEyeLocation(), 1, 0.5, 0.5, 0.5, 0);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 40L);
    }

    @Override
    public void remove() {
        HandlerList.unregisterAll(this);

        originalMobSpawningRules.forEach((worldUID, originalValue) -> {
            World world = Bukkit.getWorld(worldUID);
            if (world != null && world.getEnvironment() == World.Environment.NORMAL) {
                if (configDisableMobSpawning && originalValue != null) {
                    world.setGameRule(GameRule.DO_MOB_SPAWNING, originalValue);
                }
            }
        });
        originalMobSpawningRules.clear();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPotionEffect(PotionEffectType.REGENERATION)) {
                PotionEffect currentRegen = player.getPotionEffect(PotionEffectType.REGENERATION);
                if (currentRegen != null && currentRegen.getAmplifier() == regenAmplifier) {
                    player.removePotionEffect(PotionEffectType.REGENERATION);
                }
            }
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 0.6f, 1.5f);
        }
    }

    @Override
    public void onPlayerJoin(Player player) {
        player.sendMessage(ChatColor.AQUA + "The air feels calm and peaceful.");
    }

    @Override
    public void onPlayerQuit(Player player) {
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!configDisableMobSpawning) return;

        if (event.getEntity() instanceof Monster &&
                event.getEntity().getWorld().getEnvironment() == World.Environment.NORMAL) {
            switch (event.getSpawnReason()) {
                case NATURAL:
                case REINFORCEMENTS:
                case PATROL:
                case RAID:
                case DROWNED:
                case JOCKEY:
                case MOUNT:
                    event.setCancelled(true);
                    // plugin.getLogger().fine("[CalmSkies] Blocked hostile spawn: " + event.getEntityType() + " Reason: " + event.getSpawnReason());
                    break;
                default:
                    break;
            }
        }
    }
}