package com.rex.worldMood.moods;

import com.rex.worldMood.WorldMood;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.SoundCategory;

import java.util.*;

public class BloodMoon extends Mood implements Listener {

    private double healthMultiplier;
    private double damageMultiplier;
    private double spawnRateMultiplier;
    private final NamespacedKey BLOODMOON_HEALTH_KEY;
    private final NamespacedKey BLOODMOON_DAMAGE_KEY;
    private final NamespacedKey BLOODMOON_BUFFED_KEY;

    private static class OriginalBorderSettings {
        final double size; final double centerX; final double centerZ;
        final double damageBuffer; final double damageAmount;
        final int warningDistance; final int warningTime;
        OriginalBorderSettings(WorldBorder border) {
            this.size = border.getSize(); this.centerX = border.getCenter().getX(); this.centerZ = border.getCenter().getZ();
            this.damageBuffer = border.getDamageBuffer(); this.damageAmount = border.getDamageAmount();
            this.warningDistance = border.getWarningDistance(); this.warningTime = border.getWarningTime();
        }
    }
    private final Map<UUID, OriginalBorderSettings> originalBorders = new HashMap<>();
    private boolean bmEventsEnabled;
    private int bmEventCheckIntervalTicks;
    private double bmOverallEventChance;

    private boolean bmFrenzyEnabled;
    private int bmFrenzyDurationTicks;
    private int bmFrenzySpeedAmplifier;

    private boolean bmLightningEnabled;
    private int bmLightningStrikesPerPlayer;

    private boolean bmHordeEnabled;
    private int bmHordeMobsToSpawn;
    private EntityType bmHordeSpawnType;

    private BukkitTask bloodMoonEventTask = null;
    private final Random random = new Random();

    public BloodMoon(WorldMood plugin) {
        super(plugin, "blood_moon");
        this.BLOODMOON_HEALTH_KEY = new NamespacedKey(plugin, "bloodmoon_orig_health");
        this.BLOODMOON_DAMAGE_KEY = new NamespacedKey(plugin, "bloodmoon_orig_damage");
        this.BLOODMOON_BUFFED_KEY = new NamespacedKey(plugin, "bloodmoon_is_buffed");
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
            healthMultiplier = moodConfig.getDouble("mobHealthMultiplier", 1.5);
            damageMultiplier = moodConfig.getDouble("mobDamageMultiplier", 1.2);
            spawnRateMultiplier = moodConfig.getDouble("spawnRateMultiplier", 2.0);

            ConfigurationSection eventsConfig = moodConfig.getConfigurationSection("bloodMoonEvents");
            if (eventsConfig != null) {
                bmEventsEnabled = eventsConfig.getBoolean("enabled", true);
                bmEventCheckIntervalTicks = eventsConfig.getInt("checkIntervalSeconds", 45) * 20;
                bmOverallEventChance = eventsConfig.getDouble("overallEventChance", 0.30);

                ConfigurationSection frenzyConfig = eventsConfig.getConfigurationSection("bloodFrenzy");
                if (frenzyConfig != null) {
                    bmFrenzyEnabled = frenzyConfig.getBoolean("enabled", true);
                    bmFrenzyDurationTicks = frenzyConfig.getInt("durationSeconds", 20) * 20;
                    bmFrenzySpeedAmplifier = frenzyConfig.getInt("speedAmplifier", 1);
                } else { bmFrenzyEnabled = false; /* plugin.getLogger().warning("[BloodMoon] 'bloodFrenzy' config section missing, disabling this event."); */ }

                ConfigurationSection lightningConfig = eventsConfig.getConfigurationSection("crimsonLightning");
                if (lightningConfig != null) {
                    bmLightningEnabled = lightningConfig.getBoolean("enabled", true);
                    bmLightningStrikesPerPlayer = lightningConfig.getInt("strikesPerPlayer", 1);
                } else { bmLightningEnabled = false; /* plugin.getLogger().warning("[BloodMoon] 'crimsonLightning' config section missing, disabling this event."); */ }

                ConfigurationSection hordeConfig = eventsConfig.getConfigurationSection("hordeSurge");
                if (hordeConfig != null) {
                    bmHordeEnabled = hordeConfig.getBoolean("enabled", true);
                    bmHordeMobsToSpawn = hordeConfig.getInt("mobsToSpawn", 3);
                    try {
                        bmHordeSpawnType = EntityType.valueOf(hordeConfig.getString("spawnType", "ZOMBIE").toUpperCase());
                        if (!Monster.class.isAssignableFrom(bmHordeSpawnType.getEntityClass()) && !Slime.class.isAssignableFrom(bmHordeSpawnType.getEntityClass()) && bmHordeSpawnType != EntityType.PHANTOM) {
                            plugin.getLogger().warning("[BloodMoon] Configured spawnType for Horde Surge '" + bmHordeSpawnType + "' is not a typical hostile mob. Defaulting to ZOMBIE.");
                            bmHordeSpawnType = EntityType.ZOMBIE;
                        }
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("[BloodMoon] Invalid spawnType for Horde Surge in config. Defaulting to ZOMBIE.");
                        bmHordeSpawnType = EntityType.ZOMBIE;
                    }
                } else { bmHordeEnabled = false; /* plugin.getLogger().warning("[BloodMoon] 'hordeSurge' config section missing, disabling this event."); */ }

            } else {
                bmEventsEnabled = false;
            }
        } else {
            healthMultiplier = 1.5; damageMultiplier = 1.2; spawnRateMultiplier = 2.0;
            bmEventsEnabled = false;
            plugin.getLogger().warning("[BloodMoon] Main configuration section missing. Using default values and disabling Blood Moon events.");
        }
    }

    @Override
    public String getName() { return "Blood Moon"; }

    @Override
    public String getDescription() {
        return "The moon hangs low and red. Creatures of the night grow stronger and more numerous under a blood-red sky.";
    }


    @Override
    public List<String> getEffects() {
        List<String> effects = new ArrayList<>(Arrays.asList(
                "Blood Red Sky (Visual Effect)",
                String.format("Hostile Mobs: +%.0f%% Health", (healthMultiplier - 1.0) * 100),
                String.format("Hostile Mobs: +%.0f%% Damage", (damageMultiplier - 1.0) * 100),
                String.format("Hostile Mob Spawn Rate: x%.1f", spawnRateMultiplier),
                "Buffed Mobs emit red particles"
        ));
        if (bmEventsEnabled) {
            effects.add(ChatColor.DARK_RED + "Special Events Active!");
            if (bmFrenzyEnabled) effects.add(ChatColor.RED + "  - Chance of Blood Frenzy");
            if (bmLightningEnabled) effects.add(ChatColor.RED + "  - Chance of Crimson Lightning");
            if (bmHordeEnabled) effects.add(ChatColor.RED + "  - Chance of Horde Surges");
        }
        return effects;
    }

    @Override
    public void apply() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("Blood Moon Active: Hostile mobs will be enhanced, and the sky will turn red.");
        originalBorders.clear();

        for (World world : Bukkit.getWorlds()) { /* ... sky tint logic same as before, make sure warningTime is 0 ... */
            if (world.getEnvironment() == World.Environment.NORMAL) {
                WorldBorder border = world.getWorldBorder();
                originalBorders.put(world.getUID(), new OriginalBorderSettings(border));
                border.setCenter(border.getCenter()); border.setSize(60000000);
                border.setDamageBuffer(0); border.setDamageAmount(0);
                border.setWarningTime(0); // Instant red tint
                border.setWarningDistance(59999900);
            }
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.AMBIENT, 0.7f, 0.6f);
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 0.5f, 0.7f);
            p.sendTitle(ChatColor.DARK_RED + "" + ChatColor.BOLD + "Blood Moon", ChatColor.RED + "The night itself bleeds...", 10, 70, 20);
        }

        // Start the Blood Moon Event scheduler
        if (bmEventsEnabled && bmEventCheckIntervalTicks > 0) {
            if (bloodMoonEventTask != null && !bloodMoonEventTask.isCancelled()) {
                bloodMoonEventTask.cancel();
            }
            bloodMoonEventTask = new BukkitRunnable() {
                @Override
                public void run() {
                    if (plugin.getMoodManager().getCurrentMood() != BloodMoon.this) {
                        this.cancel();
                        return;
                    }
                    if (random.nextDouble() < bmOverallEventChance) {
                        triggerRandomBloodMoonEvent();
                    }
                }
            }.runTaskTimer(plugin, bmEventCheckIntervalTicks, bmEventCheckIntervalTicks);
            plugin.getLogger().info("[BloodMoon] Event checker started (Chance: " + (bmOverallEventChance*100) + "% every " + (bmEventCheckIntervalTicks/20.0) + "s).");
        }
    }

    @Override
    public void remove() {
        HandlerList.unregisterAll(this);
        if (bloodMoonEventTask != null && !bloodMoonEventTask.isCancelled()) {
            bloodMoonEventTask.cancel();
            bloodMoonEventTask = null;
        }
        plugin.getLogger().info("Blood Moon Ended: Removing mob enhancements and restoring sky...");
        originalBorders.forEach((worldUID, settings) -> {
            World world = Bukkit.getWorld(worldUID);
            if (world != null && world.getEnvironment() == World.Environment.NORMAL) {
                WorldBorder border = world.getWorldBorder();
                try {
                    border.setCenter(settings.centerX, settings.centerZ); border.setSize(settings.size);
                    border.setDamageBuffer(settings.damageBuffer); border.setDamageAmount(settings.damageAmount);
                    border.setWarningTime(settings.warningTime); border.setWarningDistance(settings.warningDistance);
                } catch (Exception e) { plugin.getLogger().severe("Failed to restore world border for " + world.getName() + ": " + e.getMessage()); }
            }
        });
        originalBorders.clear();
        int removedCount = 0;
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == World.Environment.NORMAL || world.getEnvironment() == World.Environment.NETHER) {
                for (LivingEntity entity : world.getLivingEntities()) {
                    if (entity instanceof Monster monster && !monster.isDead()) {
                        if (monster.getPersistentDataContainer().has(BLOODMOON_BUFFED_KEY, PersistentDataType.BYTE)) {
                            removeMobBuffs(monster); removedCount++;
                        }
                    }
                }
            }
        }
        if (removedCount > 0) plugin.getLogger().info("Removed Blood Moon buffs from " + removedCount + " entities.");
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.AMBIENT, 0.8f, 1.2f);
            p.sendTitle(ChatColor.AQUA + "The Air Clears", ChatColor.GRAY + "The blood moon fades...", 10, 60, 20);
        }
    }

    @Override
    public void tick(long ticksRemaining) {
        if (plugin.getCurrentTick() % 10 != 0) return;
        Particle.DustOptions dustOptions = new Particle.DustOptions(Color.fromRGB(150, 0, 0), 1.2f);
        for (Player player : Bukkit.getOnlinePlayers()) {
            World world = player.getWorld();
            if (world.getEnvironment() != World.Environment.NORMAL && world.getEnvironment() != World.Environment.NETHER) continue;
            for (Entity entity : player.getNearbyEntities(30, 15, 30)) {
                if (entity instanceof Monster monster && !monster.isDead()) {
                    if (monster.getPersistentDataContainer().has(BLOODMOON_BUFFED_KEY, PersistentDataType.BYTE)) {
                        Location particleLoc = monster.getEyeLocation().subtract(0, 0.2, 0);
                        world.spawnParticle(Particle.DUST, particleLoc, 3, 0.4, 0.4, 0.4, 0, dustOptions);
                        if (Math.random() < 0.05) {
                            world.spawnParticle(Particle.SMOKE, monster.getLocation().add(0, 0.5, 0), 1, 0.2, 0.2, 0.2, 0.01);
                        }
                    }
                }
            }
        }
    }

    private void triggerRandomBloodMoonEvent() {
        List<Runnable> possibleEvents = new ArrayList<>();
        if (bmFrenzyEnabled) possibleEvents.add(this::executeBloodFrenzy);
        if (bmLightningEnabled) possibleEvents.add(this::executeCrimsonLightning);
        if (bmHordeEnabled) possibleEvents.add(this::executeHordeSurge);

        if (possibleEvents.isEmpty()) {
            // plugin.getLogger().info("[BloodMoon] No blood moon events are enabled to trigger.");
            return;
        }
        possibleEvents.get(random.nextInt(possibleEvents.size())).run();
    }

    private void executeBloodFrenzy() {
        plugin.getLogger().info("[BloodMoon Event] Blood Frenzy triggered!");
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 0.8f, 0.5f);
            player.playSound(player.getLocation(), Sound.ENTITY_WOLF_HOWL, SoundCategory.HOSTILE, 1.0f, 0.7f);
        }
        Bukkit.broadcastMessage(ChatColor.DARK_RED + "[BloodMoon] " + ChatColor.RED + "A wave of bloodlust empowers the beasts of the night!");

        PotionEffect speedFrenzy = new PotionEffect(PotionEffectType.SPEED, bmFrenzyDurationTicks, bmFrenzySpeedAmplifier, false, true, true);

        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == World.Environment.NORMAL || world.getEnvironment() == World.Environment.NETHER) {
                for (LivingEntity entity : world.getLivingEntities()) {
                    if (entity instanceof Monster monster && !monster.isDead() && monster.getPersistentDataContainer().has(BLOODMOON_BUFFED_KEY, PersistentDataType.BYTE)) {
                        boolean nearPlayer = false;
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            if (player.getWorld().equals(monster.getWorld()) && player.getLocation().distanceSquared(monster.getLocation()) < 64 * 64) {
                                nearPlayer = true;
                                break;
                            }
                        }
                        if (nearPlayer) {
                            monster.addPotionEffect(speedFrenzy, true);
                            monster.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, monster.getEyeLocation(), 5, 0.3, 0.3, 0.3, 0.1);
                        }
                    }
                }
            }
        }
    }

    private void executeCrimsonLightning() {
        plugin.getLogger().info("[BloodMoon Event] Crimson Lightning crackles!");
        Bukkit.broadcastMessage(ChatColor.DARK_RED + "[BloodMoon] " + ChatColor.RED + "The sky bleeds crimson lightning!");

        Particle.DustOptions redDust = new Particle.DustOptions(Color.RED, 1.5f);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR) continue;
            World world = player.getWorld();
            if (world.getEnvironment() != World.Environment.NORMAL && world.getEnvironment() != World.Environment.THE_END) continue; // Overworld & End

            for (int i = 0; i < bmLightningStrikesPerPlayer; i++) {
                Location playerLoc = player.getLocation();
                double offsetX = (random.nextDouble() - 0.5) * 30;
                double offsetZ = (random.nextDouble() - 0.5) * 30;
                Location strikeLoc = playerLoc.clone().add(offsetX, 0, offsetZ);
                strikeLoc.setY(world.getHighestBlockYAt(strikeLoc) + 1);

                if (strikeLoc.getBlock().isPassable() && strikeLoc.clone().add(0,1,0).getBlock().isPassable()) {
                    world.strikeLightningEffect(strikeLoc); // Visual only lightning
                    world.spawnParticle(Particle.DUST, strikeLoc, 50, 0.5, 0.5, 0.5, 0, redDust);
                    world.spawnParticle(Particle.LAVA, strikeLoc, 10, 0.3, 0.3, 0.3, 0);
                    if (player.getLocation().distanceSquared(strikeLoc) < 5*5 && random.nextDouble() < 0.3) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 0), true);
                    }
                }
            }
        }
    }

    private void executeHordeSurge() {
        plugin.getLogger().info("[BloodMoon Event] A Horde Surge begins!");
        Bukkit.broadcastMessage(ChatColor.DARK_RED + "[BloodMoon] " + ChatColor.RED + "More horrors crawl from the shadows!");

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR || player.getGameMode() == GameMode.CREATIVE) continue;
            World world = player.getWorld();
            if (world.getEnvironment() != World.Environment.NORMAL && world.getEnvironment() != World.Environment.NETHER) continue;

            for (int i = 0; i < bmHordeMobsToSpawn; i++) {
                Location playerLoc = player.getLocation();
                double offsetX = (random.nextDouble() - 0.5) * 20;
                double offsetZ = (random.nextDouble() - 0.5) * 20;
                Location spawnBase = playerLoc.clone().add(offsetX, 0, offsetZ);

                Location spawnLoc = findSafeSpawnLocation(spawnBase, 5);

                if (spawnLoc != null) {
                    Entity spawned = world.spawnEntity(spawnLoc, bmHordeSpawnType);
                    if (spawned instanceof Monster) {
                        buffMob((Monster) spawned);
                    }
                }
            }
        }
    }

    private Location findSafeSpawnLocation(Location center, int radius) {
        World world = center.getWorld();
        if (world == null) return null;
        for (int i = 0; i < 10; i++) {
            double x = center.getX() + random.nextInt(radius * 2 + 1) - radius;
            double z = center.getZ() + random.nextInt(radius * 2 + 1) - radius;
            int y = world.getHighestBlockYAt((int)x, (int)z);
            Location potentialLoc = new Location(world, x, y + 1, z);

            Block blockBelow = potentialLoc.clone().subtract(0,1,0).getBlock();
            Block blockAt = potentialLoc.getBlock();
            Block blockAbove = potentialLoc.clone().add(0,1,0).getBlock();

            if (blockBelow.getType().isSolid() && !blockBelow.isLiquid() &&
                    blockAt.isPassable() && !blockAt.isLiquid() &&
                    blockAbove.isPassable() && !blockAbove.isLiquid()) {
                return potentialLoc;
            }
        }
        return null;
    }
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Monster monster)) return;
        World.Environment env = monster.getWorld().getEnvironment();
        if (env != World.Environment.NORMAL && env != World.Environment.NETHER) return;

        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        boolean shouldBuff = switch (reason) {
            case NATURAL, REINFORCEMENTS, PATROL, RAID, DROWNED, SPAWNER, SLIME_SPLIT, SILVERFISH_BLOCK -> true;
            default -> false;
        };
        if (shouldBuff) {
            buffMob(monster);

            if (reason == CreatureSpawnEvent.SpawnReason.NATURAL && spawnRateMultiplier > 1.0 && Math.random() < (spawnRateMultiplier - 1.0)) {
                Location loc = monster.getLocation();
                for (int i = 0; i < 3; i++) {
                    Location potentialLoc = loc.clone().add(Math.random() * 6 - 3, 0, Math.random() * 6 - 3);
                    if (potentialLoc.getBlock().isPassable() && potentialLoc.clone().add(0,1,0).getBlock().isPassable() && potentialLoc.clone().add(0,-1,0).getBlock().getType().isSolid()) {
                        Monster extra = (Monster) monster.getWorld().spawnEntity(potentialLoc, monster.getType());
                        buffMob(extra);
                        break;
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Monster monster) {
            PersistentDataContainer data = monster.getPersistentDataContainer();
            if (data.has(BLOODMOON_BUFFED_KEY, PersistentDataType.BYTE)) {
                data.remove(BLOODMOON_HEALTH_KEY);
                data.remove(BLOODMOON_DAMAGE_KEY);
                data.remove(BLOODMOON_BUFFED_KEY);
            }
        }
    }

    private void buffMob(Monster monster) {
        PersistentDataContainer data = monster.getPersistentDataContainer();
        if (data.has(BLOODMOON_BUFFED_KEY, PersistentDataType.BYTE)) return;
        boolean appliedBuff = false;
        AttributeInstance maxHealth = monster.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null && !data.has(BLOODMOON_HEALTH_KEY, PersistentDataType.DOUBLE)) {
            double original = maxHealth.getBaseValue();
            double newHealth = Math.max(1.0, original * healthMultiplier);
            maxHealth.setBaseValue(newHealth);
            monster.setHealth(newHealth);
            data.set(BLOODMOON_HEALTH_KEY, PersistentDataType.DOUBLE, original);
            appliedBuff = true;
        }
        AttributeInstance attackDamage = monster.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attackDamage != null && !data.has(BLOODMOON_DAMAGE_KEY, PersistentDataType.DOUBLE)) {
            double original = attackDamage.getBaseValue();
            double newDamage = original * damageMultiplier;
            if (original > 0 && newDamage <= original) newDamage = original + 0.5;
            else if (original == 0 && newDamage == 0) newDamage = 0.5;
            attackDamage.setBaseValue(Math.max(0, newDamage));
            data.set(BLOODMOON_DAMAGE_KEY, PersistentDataType.DOUBLE, original);
            appliedBuff = true;
        }
        if (appliedBuff) {
            data.set(BLOODMOON_BUFFED_KEY, PersistentDataType.BYTE, (byte) 1);
        }
    }

    private void removeMobBuffs(Monster monster) {
        PersistentDataContainer data = monster.getPersistentDataContainer();
        if (!data.has(BLOODMOON_BUFFED_KEY, PersistentDataType.BYTE)) return;
        AttributeInstance maxHealth = monster.getAttribute(Attribute.MAX_HEALTH);
        if (data.has(BLOODMOON_HEALTH_KEY, PersistentDataType.DOUBLE) && maxHealth != null) {
            double original = data.get(BLOODMOON_HEALTH_KEY, PersistentDataType.DOUBLE);
            maxHealth.setBaseValue(original);
            if (!monster.isDead() && monster.getHealth() > original) {
                monster.setHealth(original);
            }
        }
        AttributeInstance attackDamage = monster.getAttribute(Attribute.ATTACK_DAMAGE);
        if (data.has(BLOODMOON_DAMAGE_KEY, PersistentDataType.DOUBLE) && attackDamage != null) {
            double original = data.get(BLOODMOON_DAMAGE_KEY, PersistentDataType.DOUBLE);
            attackDamage.setBaseValue(original);
        }
        data.remove(BLOODMOON_HEALTH_KEY);
        data.remove(BLOODMOON_DAMAGE_KEY);
        data.remove(BLOODMOON_BUFFED_KEY);
    }

    @Override
    public void onPlayerJoin(Player player) {
        World world = player.getWorld();
        if (world.getEnvironment() == World.Environment.NORMAL) {
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.AMBIENT, 0.7f, 0.6f);
            player.sendTitle(ChatColor.DARK_RED + "" + ChatColor.BOLD + "Blood Moon", ChatColor.RED + "The night itself bleeds...", 10, 70, 20);
        }
    }
    @Override
    public void onPlayerQuit(Player player) {}

}