package com.rex.worldMood.moods;

import com.rex.worldMood.WorldMood;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExpEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.SoundCategory;

import java.util.*;

public class ProsperousWinds extends Mood implements Listener {

    private double oreDoubleDropChance;
    private double mobExtraLootChance;
    private final Random random = new Random();

    private boolean configEnableWindEffects;
    private boolean configEnableOreDoubling;

    private static final double PARTIAL_LOOT_DUPE_CHANCE = 0.5;
    private static final double XP_MULTIPLIER = 1.5;
    private static final int WIND_EFFECT_INTERVAL = 25;
    private static final double WIND_EFFECT_CHANCE_PER_PLAYER = 0.15;

    private static final Set<Material> ORE_MATERIALS = Set.of(
            Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE,
            Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE,
            Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
            Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
            Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
            Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
            Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
            Material.NETHER_QUARTZ_ORE, Material.NETHER_GOLD_ORE,
            Material.ANCIENT_DEBRIS
    );

    private static final Sound ALT_WIND_SOUND = Sound.ITEM_ELYTRA_FLYING;

    public ProsperousWinds(WorldMood plugin) {
        super(plugin, "prosperous_winds");
        loadConfigValues();
    }

    @Override
    protected void loadConfigValues() {
        super.loadConfigValues();
        ConfigurationSection moodConfig = getMoodConfigSection();
        if (moodConfig != null) {
            oreDoubleDropChance = moodConfig.getDouble("oreDoubleDropChance", 0.15);
            mobExtraLootChance = moodConfig.getDouble("mobExtraLootChance", 0.10);
            configEnableWindEffects = moodConfig.getBoolean("enableWindEffects", true);
            configEnableOreDoubling = moodConfig.getBoolean("enableOreDoubling", true);
        } else {
            oreDoubleDropChance = 0.15;
            mobExtraLootChance = 0.10;
            configEnableWindEffects = true;
            configEnableOreDoubling = true;
            plugin.getLogger().warning("[ProsperousWinds] Configuration section missing. Using default values.");
        }
    }

    @Override
    public String getName() {
        return "Prosperous Winds";
    }

    @Override
    public String getDescription() {
        return "Good fortune blows through the land, revealing richer veins and greater spoils from fallen foes amidst swirling breezes.";
    }

    @Override
    public List<String> getEffects() {
        List<String> effects = new ArrayList<>();
        if (configEnableOreDoubling) {
            effects.add(String.format("+%.0f%% Ore Drop Chance", oreDoubleDropChance * 100));
        }
        effects.add(String.format("+%.0f%% Extra Mob Loot Chance", mobExtraLootChance * 100));
        effects.add(String.format("x%.1f XP Gain", XP_MULTIPLIER));
        if (configEnableWindEffects) {
            effects.add("Ambient Wind Effects");
        }
        effects.add(ChatColor.GRAY + "(Mood lasts until Night/Day)");
        return effects;
    }

    @Override
    public void apply() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        if (configEnableWindEffects) {
            for(Player p : plugin.getServer().getOnlinePlayers()){
                p.playSound(p.getLocation(), ALT_WIND_SOUND, SoundCategory.AMBIENT, 0.2f, 0.5f + random.nextFloat() * 0.3f);
                p.playSound(p.getLocation(), Sound.WEATHER_RAIN, SoundCategory.AMBIENT, 0.05f, 1.8f + random.nextFloat() * 0.2f);
            }
        }
    }

    @Override
    public void remove() {
        HandlerList.unregisterAll(this);
    }

    @Override
    public void tick(long ticksRemaining) {
        if (!configEnableWindEffects || plugin.getCurrentTick() % WIND_EFFECT_INTERVAL != 0) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().getEnvironment() != World.Environment.NORMAL) continue;

            if (random.nextDouble() < WIND_EFFECT_CHANCE_PER_PLAYER) {
                Location loc = player.getLocation();
                World world = player.getWorld();

                float pitch = 0.5f + random.nextFloat() * 0.4f;
                float volume = 0.1f + random.nextFloat() * 0.2f;
                world.playSound(loc, ALT_WIND_SOUND, SoundCategory.AMBIENT, volume, pitch);
                if (random.nextBoolean()) {
                    world.playSound(loc, Sound.WEATHER_RAIN, SoundCategory.AMBIENT, volume * 0.1f, pitch + 0.8f);
                }

                int particleCount = 2 + random.nextInt(4);
                double offsetX = 2 + random.nextDouble() * 3;
                double offsetY = 0.3 + random.nextDouble() * 1.0;
                double offsetZ = 2 + random.nextDouble() * 3;
                Location particleLoc = player.getEyeLocation().add(random.nextGaussian() * 3, random.nextDouble() * 2, random.nextGaussian() * 3);
                Particle particleType = random.nextBoolean() ? Particle.CLOUD : Particle.SPORE_BLOSSOM_AIR;
                world.spawnParticle(particleType, particleLoc, particleCount, offsetX, offsetY, offsetZ, 0.005);
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        ItemStack tool = player.getInventory().getItemInMainHand();

        if (!configEnableOreDoubling || !ORE_MATERIALS.contains(block.getType()) || random.nextDouble() >= oreDoubleDropChance) {
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        if (tool != null && tool.containsEnchantment(Enchantment.SILK_TOUCH)) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), new ItemStack(block.getType(), 1));
        } else {
            Collection<ItemStack> defaultDrops = block.getDrops(tool);
            if (!defaultDrops.isEmpty()) {
                for (ItemStack drop : defaultDrops) {
                    if (drop.getType() != Material.AIR) {
                        block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), drop.clone());
                    }
                }
            }
        }
        block.getWorld().spawnParticle(Particle.COMPOSTER, block.getLocation().add(0.5, 0.7, 0.5), 10, 0.3, 0.3, 0.3, 0.05);
        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 0.9f, 1.6f + random.nextFloat() * 0.2f);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();

        if (killer != null && random.nextDouble() < mobExtraLootChance) {
            List<ItemStack> originalDrops = event.getDrops();
            List<ItemStack> extraDrops = new ArrayList<>();

            if (originalDrops.isEmpty()) return;

            for (ItemStack originalDrop : originalDrops) {
                if (random.nextDouble() < PARTIAL_LOOT_DUPE_CHANCE) {
                    extraDrops.add(originalDrop.clone());
                }
            }

            if (!extraDrops.isEmpty()) {
                for(ItemStack extraDrop : extraDrops) {
                    entity.getWorld().dropItemNaturally(entity.getLocation(), extraDrop);
                }
                entity.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, entity.getEyeLocation(), 10, 0.4, 0.4, 0.4, 0.05);
                entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_CHICKEN_EGG, SoundCategory.NEUTRAL, 1.0f, 1.0f + random.nextFloat() * 0.3f);
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onExpChange(PlayerExpChangeEvent event) {
        int originalAmount = event.getAmount();
        if (originalAmount <= 0) return;
        int newAmount = (int) Math.max(1, Math.round(originalAmount * XP_MULTIPLIER));
        event.setAmount(newAmount);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockExp(BlockExpEvent event) {
        int originalAmount = event.getExpToDrop();
        if (originalAmount <= 0) return;
        int newAmount = (int) Math.max(1, Math.round(originalAmount * XP_MULTIPLIER));
        event.setExpToDrop(newAmount);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        int originalAmount = event.getExpToDrop();
        if (originalAmount <= 0) return;
        int newAmount = (int) Math.max(1, Math.round(originalAmount * XP_MULTIPLIER));
        event.setExpToDrop(newAmount);
    }

    @Override
    public void onPlayerJoin(Player player) {
        if (configEnableWindEffects && player.getWorld().getEnvironment() == World.Environment.NORMAL) {
            player.playSound(player.getLocation(), ALT_WIND_SOUND, SoundCategory.AMBIENT, 0.15f, 0.6f + random.nextFloat() * 0.2f);
        }
    }
    @Override
    public void onPlayerQuit(Player player) {}
}