package com.rex.worldMood.moods;

import com.rex.worldMood.WorldMood;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.GameMode;
import org.bukkit.ChatColor;
import org.bukkit.SoundCategory;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class InfernalHeat extends Mood implements Listener {

    private boolean configBurnInSunlight;
    private String configRequiredProtection;
    private final Random random = new Random();

    public InfernalHeat(WorldMood plugin) {
        super(plugin, "infernal_heat");
        loadConfigValues();
    }

    @Override
    public boolean requiresDay() {
        return true;
    }

    @Override
    protected void loadConfigValues() {
        super.loadConfigValues();
        ConfigurationSection moodConfig = getMoodConfigSection();
        if (moodConfig != null) {
            configBurnInSunlight = moodConfig.getBoolean("burnInSunlight", true);
            configRequiredProtection = moodConfig.getString("requiredProtection", "HELMET").toUpperCase();
        } else {
            configBurnInSunlight = true;
            configRequiredProtection = "HELMET";
            plugin.getLogger().warning("[InfernalHeat] Configuration section missing. Using default values.");
        }
    }

    @Override
    public String getName() {
        return "Infernal Heat";
    }

    @Override
    public String getDescription() {
        return "The sun burns with unnatural intensity. Seek shade, water, or protection!";
    }

    @Override
    public List<String> getEffects() {
        String protectionInfo;
        switch (configRequiredProtection) {
            case "HELMET":
                protectionInfo = "Protection: Helmet";
                break;
            case "FULL_ARMOR":
                protectionInfo = "Protection: Full Armor";
                break;
            case "POTION":
                protectionInfo = "Protection: Fire Resistance Potion";
                break;
            case "NONE":
                protectionInfo = "No specific gear protects!";
                break;
            default:
                protectionInfo = "Protection: " + configRequiredProtection.substring(0,1).toUpperCase() + configRequiredProtection.substring(1).toLowerCase();
                break;
        }
        return Arrays.asList(
                configBurnInSunlight ? "Burning Sunlight (" + protectionInfo + ")" : "Intense Ambient Heat",
                "Fire Visuals & Sounds",
                ChatColor.GRAY + "(Mood lasts until Night)"
        );
    }

    @Override
    public void apply() {
        for(Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().getEnvironment() == World.Environment.NORMAL) {
                p.playSound(p.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, SoundCategory.AMBIENT, 0.7f, 1.2f);
            }
        }
    }

    @Override
    public void remove() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().getEnvironment() == World.Environment.NORMAL && player.getFireTicks() > 0) {
                player.setFireTicks(0);
            }
        }
    }

    @Override
    public void tick(long ticksRemaining) {
        if (!configBurnInSunlight) return;

        if (plugin.getCurrentTick() % 10 != 0) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().getEnvironment() != World.Environment.NORMAL) {
                continue;
            }

            if (isPlayerExposedToSun(player) && !isPlayerProtected(player)) {
                if (player.getFireTicks() < 20) {
                    player.setFireTicks(40);
                }
                if (random.nextDouble() < 0.15) {
                    player.playSound(player.getLocation(), Sound.BLOCK_FIRE_AMBIENT, SoundCategory.PLAYERS, 0.4f, 1.5f);
                }
                player.getWorld().spawnParticle(Particle.FLAME, player.getEyeLocation().subtract(0, 0.2, 0), 2, 0.1, 0.2, 0.1, 0.01);
            }
        }
    }

    private boolean isPlayerExposedToSun(Player player) {
        if (player.isDead() || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return false;

        World world = player.getWorld();
        long time = world.getTime();
        boolean isHighSun = time >= 500 && time < 12500;

        if (!isHighSun || world.hasStorm() || world.isThundering()) return false;

        Block blockAtHead = player.getEyeLocation().getBlock();

        return blockAtHead.getLightFromSky() >= 15 &&
                !player.isInWater() &&
                !player.isInsideVehicle() &&
                !isInLava(player);
    }

    private boolean isPlayerProtected(Player player) {
        if (player.hasPotionEffect(PotionEffectType.FIRE_RESISTANCE)) {
            return true;
        }

        PlayerInventory inv = player.getInventory();
        ItemStack helmet = inv.getHelmet();
        ItemStack chestplate = inv.getChestplate();
        ItemStack leggings = inv.getLeggings();
        ItemStack boots = inv.getBoots();

        int fireProtLevel = 0;
        if (helmet != null) fireProtLevel += helmet.getEnchantmentLevel(Enchantment.FIRE_PROTECTION);
        if (chestplate != null) fireProtLevel += chestplate.getEnchantmentLevel(Enchantment.FIRE_PROTECTION);
        if (leggings != null) fireProtLevel += leggings.getEnchantmentLevel(Enchantment.FIRE_PROTECTION);
        if (boots != null) fireProtLevel += boots.getEnchantmentLevel(Enchantment.FIRE_PROTECTION);

        if (fireProtLevel > 0) {
            return true;
        }

        switch (configRequiredProtection) {
            case "HELMET":
                return helmet != null && helmet.getType() != Material.AIR;
            case "FULL_ARMOR":
                return (helmet != null && helmet.getType() != Material.AIR) &&
                        (chestplate != null && chestplate.getType() != Material.AIR) &&
                        (leggings != null && leggings.getType() != Material.AIR) &&
                        (boots != null && boots.getType() != Material.AIR);
            case "POTION":
                return false;
            case "NONE":
            default:
                return true;
        }
    }

    private boolean isInLava(Player player) {
        Material blockPlayerIsIn = player.getLocation().getBlock().getType();
        return blockPlayerIsIn == Material.LAVA;
    }

    @Override
    public void onPlayerJoin(Player player) {
        if (player.getWorld().getEnvironment() == World.Environment.NORMAL &&
                configBurnInSunlight &&
                isPlayerExposedToSun(player) &&
                !isPlayerProtected(player)) {
            player.playSound(player.getLocation(), Sound.BLOCK_FIRE_AMBIENT, SoundCategory.PLAYERS, 0.5f, 1.3f);
        }
    }

    @Override
    public void onPlayerQuit(Player player) {
    }
}