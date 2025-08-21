package com.rex.worldMood.moods;

import com.rex.worldMood.WorldMood;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
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
import org.bukkit.util.Vector;
import org.bukkit.SoundCategory;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class VoidTension extends Mood implements Listener {

    private boolean configEnableAnomalies;
    private double configAnomalyChancePerTickPerPlayer;
    private double configStrongMobSpawnChance;
    private final Random random = new Random();
    private boolean anomalyAntiGravityPulseEnabled; private int anomalyAntiGravityPulseDurationTicks;
    private boolean anomalyVoidGraspEnabled; private int anomalyVoidGraspDurationTicks;
    private boolean anomalyRealityTearEnabled; private int anomalyRealityTearDurationTicks;
    private boolean anomalySpatialWarpEnabled; private int anomalySpatialWarpDurationTicks;
    private boolean anomalyVoidShriekEnabled; private int anomalyVoidShriekDurationTicks;
    private boolean anomalyUnstableEnergyEnabled; private int anomalyUnstableEnergyDurationTicks;
    private boolean anomalyChronoStutterEnabled; private int anomalyChronoStutterDurationTicks;
    private boolean anomalyWhisperingMadnessEnabled; private int anomalyWhisperingMadnessDurationTicks;
    private boolean anomalyUnstableGroundEnabled; private int anomalyUnstableGroundDurationTicks;
    private boolean anomalyVoidLeechEnabled; private int anomalyVoidLeechWitherDurationTicks;

    private static final double UNIVERSAL_SPEED_MULTIPLIER = 1.15;
    private static final int MOB_TELEPORT_INTERVAL = 40;
    private static final double MOB_TELEPORT_CHANCE_PER_MOB = 0.40;
    private static final double MOB_TELEPORT_MAX_DISTANCE = 10.0;

    private final NamespacedKey VOID_HEALTH_KEY, VOID_DAMAGE_KEY, VOID_BUFFED_KEY, VOID_NAMED_KEY, VOID_GENERIC_SPEED_KEY;

    private static PotionEffectType PURE_DARKNESS_EFFECT = null;
    private static Particle SONIC_BOOM_PARTICLE = null;
    private static Material SCULK_MATERIAL = null;
    private static boolean versionSpecificsInitialized = false;

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
    private final List<Supplier<Boolean>> anomalyExecutors = new ArrayList<>();
    private static final double DEFAULT_ANOMALY_CHANCE_PER_TICK_PER_PLAYER = 0.0002;
    private static final int DEFAULT_SHORT_DURATION_SECONDS = 6;
    private static final int DEFAULT_MEDIUM_DURATION_SECONDS = 10;
    private static final int DEFAULT_LONG_DURATION_SECONDS = 15;
    private static final int DEFAULT_UNSTABLE_GROUND_REVERT_SECONDS = 7;
    private static final int DEFAULT_VOID_LEECH_WITHER_SECONDS = 6;

    public VoidTension(WorldMood plugin) {
        super(plugin, "void_tension");

        this.VOID_HEALTH_KEY = new NamespacedKey(plugin, "wm_vt_health_orig");
        this.VOID_DAMAGE_KEY = new NamespacedKey(plugin, "wm_vt_damage_orig");
        this.VOID_BUFFED_KEY = new NamespacedKey(plugin, "wm_vt_is_buffed");
        this.VOID_NAMED_KEY = new NamespacedKey(plugin, "wm_vt_is_named");
        this.VOID_GENERIC_SPEED_KEY = new NamespacedKey(plugin, "wm_vt_speed_orig");

        if (!versionSpecificsInitialized) {
            try { PURE_DARKNESS_EFFECT = PotionEffectType.DARKNESS; }
            catch (NoSuchFieldError e) { this.plugin.getLogger().info("[VoidTension] PotionEffectType.DARKNESS not found (server pre-1.19). Void Grasp anomaly will use Blindness."); }
            try { SONIC_BOOM_PARTICLE = Particle.SONIC_BOOM; }
            catch (NoSuchFieldError e) { this.plugin.getLogger().info("[VoidTension] Particle.SONIC_BOOM not found (server pre-1.19). Void Shriek anomaly will use EXPLOSION_HUGE."); }
            try { SCULK_MATERIAL = Material.SCULK; }
            catch (NoSuchFieldError e) { this.plugin.getLogger().info("[VoidTension] Material.SCULK not found (server pre-1.19). Unstable Ground anomaly will have fewer block types.");}
            versionSpecificsInitialized = true;
        }

        loadConfigValues();
    }

    @Override
    protected void loadConfigValues() {
        super.loadConfigValues();

        ConfigurationSection moodConfig = getMoodConfigSection();
        if (moodConfig != null) {
            configEnableAnomalies = moodConfig.getBoolean("enableAnomalies", true);
            configAnomalyChancePerTickPerPlayer = moodConfig.getDouble("anomalyChancePerTickPerPlayer", DEFAULT_ANOMALY_CHANCE_PER_TICK_PER_PLAYER);
            configStrongMobSpawnChance = moodConfig.getDouble("strongMobSpawnChance", 0.12);

            ConfigurationSection anomaliesConfig = moodConfig.getConfigurationSection("individualAnomalies");
            if (anomaliesConfig != null && configEnableAnomalies) {
                anomalyAntiGravityPulseEnabled = anomaliesConfig.getBoolean("antiGravityPulse.enabled", true);
                anomalyAntiGravityPulseDurationTicks = anomaliesConfig.getInt("antiGravityPulse.durationSeconds", DEFAULT_SHORT_DURATION_SECONDS) * 20;

                anomalyVoidGraspEnabled = anomaliesConfig.getBoolean("voidGrasp.enabled", true);
                anomalyVoidGraspDurationTicks = anomaliesConfig.getInt("voidGrasp.durationSeconds", DEFAULT_MEDIUM_DURATION_SECONDS) * 20;

                anomalyRealityTearEnabled = anomaliesConfig.getBoolean("realityTear.enabled", true);
                anomalyRealityTearDurationTicks = anomaliesConfig.getInt("realityTear.durationSeconds", DEFAULT_SHORT_DURATION_SECONDS) * 20;

                anomalySpatialWarpEnabled = anomaliesConfig.getBoolean("spatialWarp.enabled", true);
                anomalySpatialWarpDurationTicks = anomaliesConfig.getInt("spatialWarp.durationSeconds", DEFAULT_MEDIUM_DURATION_SECONDS) * 20;

                anomalyVoidShriekEnabled = anomaliesConfig.getBoolean("voidShriek.enabled", true);
                anomalyVoidShriekDurationTicks = anomaliesConfig.getInt("voidShriek.durationSeconds", DEFAULT_MEDIUM_DURATION_SECONDS + 2) * 20;

                anomalyUnstableEnergyEnabled = anomaliesConfig.getBoolean("unstableEnergy.enabled", true);
                anomalyUnstableEnergyDurationTicks = anomaliesConfig.getInt("unstableEnergy.durationSeconds", DEFAULT_LONG_DURATION_SECONDS) * 20;

                anomalyChronoStutterEnabled = anomaliesConfig.getBoolean("chronoStutter.enabled", true);
                anomalyChronoStutterDurationTicks = anomaliesConfig.getInt("chronoStutter.durationSeconds", DEFAULT_SHORT_DURATION_SECONDS) * 20;

                anomalyWhisperingMadnessEnabled = anomaliesConfig.getBoolean("whisperingMadness.enabled", true);
                anomalyWhisperingMadnessDurationTicks = anomaliesConfig.getInt("whisperingMadness.durationSeconds", DEFAULT_LONG_DURATION_SECONDS) * 20;

                anomalyUnstableGroundEnabled = anomaliesConfig.getBoolean("unstableGround.enabled", true);
                anomalyUnstableGroundDurationTicks = anomaliesConfig.getInt("unstableGround.durationSeconds", DEFAULT_UNSTABLE_GROUND_REVERT_SECONDS) * 20;

                anomalyVoidLeechEnabled = anomaliesConfig.getBoolean("voidLeech.enabled", true);
                anomalyVoidLeechWitherDurationTicks = anomaliesConfig.getInt("voidLeech.durationSeconds", DEFAULT_VOID_LEECH_WITHER_SECONDS) * 20;

                plugin.getLogger().info("[VoidTension] Individual anomaly toggles and durations loaded.");

            } else if (configEnableAnomalies) {
                plugin.getLogger().info("[VoidTension] 'individualAnomalies' section missing. All anomalies enabled by default with default durations if master 'enableAnomalies' is true.");
                anomalyAntiGravityPulseEnabled = true; anomalyAntiGravityPulseDurationTicks = DEFAULT_SHORT_DURATION_SECONDS * 20;
                anomalyVoidGraspEnabled = true; anomalyVoidGraspDurationTicks = DEFAULT_MEDIUM_DURATION_SECONDS * 20;
                anomalyRealityTearEnabled = true; anomalyRealityTearDurationTicks = DEFAULT_SHORT_DURATION_SECONDS * 20;
                anomalySpatialWarpEnabled = true; anomalySpatialWarpDurationTicks = DEFAULT_MEDIUM_DURATION_SECONDS * 20;
                anomalyVoidShriekEnabled = true; anomalyVoidShriekDurationTicks = (DEFAULT_MEDIUM_DURATION_SECONDS + 2) * 20;
                anomalyUnstableEnergyEnabled = true; anomalyUnstableEnergyDurationTicks = DEFAULT_LONG_DURATION_SECONDS * 20;
                anomalyChronoStutterEnabled = true; anomalyChronoStutterDurationTicks = DEFAULT_SHORT_DURATION_SECONDS * 20;
                anomalyWhisperingMadnessEnabled = true; anomalyWhisperingMadnessDurationTicks = DEFAULT_LONG_DURATION_SECONDS * 20;
                anomalyUnstableGroundEnabled = true; anomalyUnstableGroundDurationTicks = DEFAULT_UNSTABLE_GROUND_REVERT_SECONDS * 20;
                anomalyVoidLeechEnabled = true; anomalyVoidLeechWitherDurationTicks = DEFAULT_VOID_LEECH_WITHER_SECONDS * 20;
            } else {
                plugin.getLogger().info("[VoidTension] Master 'enableAnomalies' is false. All individual anomalies disabled.");
                anomalyAntiGravityPulseEnabled = anomalyVoidGraspEnabled = anomalyRealityTearEnabled =
                        anomalySpatialWarpEnabled = anomalyVoidShriekEnabled = anomalyUnstableEnergyEnabled =
                                anomalyChronoStutterEnabled = anomalyWhisperingMadnessEnabled = anomalyUnstableGroundEnabled =
                                        anomalyVoidLeechEnabled = false;
            }
        } else {
            configEnableAnomalies = true;
            configAnomalyChancePerTickPerPlayer = DEFAULT_ANOMALY_CHANCE_PER_TICK_PER_PLAYER;
            configStrongMobSpawnChance = 0.12;
            anomalyAntiGravityPulseEnabled = true; anomalyAntiGravityPulseDurationTicks = DEFAULT_SHORT_DURATION_SECONDS * 20;
            anomalyVoidGraspEnabled = true; anomalyVoidGraspDurationTicks = DEFAULT_MEDIUM_DURATION_SECONDS * 20;
            anomalyRealityTearEnabled = true; anomalyRealityTearDurationTicks = DEFAULT_SHORT_DURATION_SECONDS * 20;
            anomalySpatialWarpEnabled = true; anomalySpatialWarpDurationTicks = DEFAULT_MEDIUM_DURATION_SECONDS * 20;
            anomalyVoidShriekEnabled = true; anomalyVoidShriekDurationTicks = (DEFAULT_MEDIUM_DURATION_SECONDS + 2) * 20;
            anomalyUnstableEnergyEnabled = true; anomalyUnstableEnergyDurationTicks = DEFAULT_LONG_DURATION_SECONDS * 20;
            anomalyChronoStutterEnabled = true; anomalyChronoStutterDurationTicks = DEFAULT_SHORT_DURATION_SECONDS * 20;
            anomalyWhisperingMadnessEnabled = true; anomalyWhisperingMadnessDurationTicks = DEFAULT_LONG_DURATION_SECONDS * 20;
            anomalyUnstableGroundEnabled = true; anomalyUnstableGroundDurationTicks = DEFAULT_UNSTABLE_GROUND_REVERT_SECONDS * 20;
            anomalyVoidLeechEnabled = true; anomalyVoidLeechWitherDurationTicks = DEFAULT_VOID_LEECH_WITHER_SECONDS * 20;
            plugin.getLogger().warning("[VoidTension] Main configuration section missing. Using default values for all settings.");
        }

        plugin.getLogger().info("[VoidTension] Anomaly chance per player/tick set to: " + String.format("%.5f", configAnomalyChancePerTickPerPlayer) +
                (configEnableAnomalies ? "" : " (Master Anomaly Switch is DISABLED)"));
        if (!configEnableAnomalies) {
            plugin.getLogger().warning("[VoidTension] Master 'enableAnomalies' is false in the config. No anomalies will occur.");
        }
        populateAnomalyExecutors();
    }

    private void populateAnomalyExecutors() {
        this.anomalyExecutors.clear();

        if (anomalyAntiGravityPulseEnabled) anomalyExecutors.add(this::executeAntiGravityPulse);
        if (anomalyVoidGraspEnabled) anomalyExecutors.add(this::executeVoidGrasp);
        if (anomalyRealityTearEnabled) anomalyExecutors.add(this::executeRealityTear);
        if (anomalySpatialWarpEnabled) anomalyExecutors.add(this::executeSpatialWarp);
        if (anomalyVoidShriekEnabled) anomalyExecutors.add(this::executeVoidShriek);
        if (anomalyUnstableEnergyEnabled) anomalyExecutors.add(this::executeUnstableEnergy);
        if (anomalyChronoStutterEnabled) anomalyExecutors.add(this::executeChronoStutter);
        if (anomalyWhisperingMadnessEnabled) anomalyExecutors.add(this::executeWhisperingMadness);
        if (anomalyUnstableGroundEnabled) anomalyExecutors.add(this::executeUnstableGround);
        if (anomalyVoidLeechEnabled) anomalyExecutors.add(this::executeVoidLeech);
    }

    @Override
    public String getName() { return "Void Tension"; }

    @Override
    public String getDescription() {
        return "The fabric of reality strains. Anomalies abound, creatures adopt a void-touched visage, move with unnatural speed, and flicker through space.";
    }

    @Override
    public List<String> getEffects() {
        List<String> effects = new ArrayList<>();
        effects.add(configEnableAnomalies && !anomalyExecutors.isEmpty() ? "Frequent Configurable Anomalies" : (configEnableAnomalies ? "Anomalies Enabled (None Active/Configured)" : "Anomalies Disabled"));
        effects.add(String.format("Hostile Mobs: +%.0f%% Spawn Chance (Stronger)", configStrongMobSpawnChance * 100));
        effects.add("All Creatures: 'Void Touched' & Faster");
        effects.add("Hostile Mobs: Random Teleportation");
        effects.add("Warped Sky Visuals (Overworld/End)");
        effects.add(ChatColor.GRAY + "(Lasts 1 Full Day/Night Cycle)");
        return effects;
    }

    @Override
    public void apply() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        originalBorders.clear();

        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == World.Environment.NORMAL || world.getEnvironment() == World.Environment.THE_END) {
                WorldBorder border = world.getWorldBorder();
                originalBorders.put(world.getUID(), new OriginalBorderSettings(border));
                border.setCenter(border.getCenter()); border.setSize(60000000); border.setDamageBuffer(0);
                border.setDamageAmount(0); border.setWarningTime(0); border.setWarningDistance(59999900);
            }
        }
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == World.Environment.NORMAL || world.getEnvironment() == World.Environment.NETHER || world.getEnvironment() == World.Environment.THE_END) {
                for (LivingEntity entity : world.getLivingEntities()) {
                    if (entity instanceof Player || entity.isDead()) continue;
                    PersistentDataContainer data = entity.getPersistentDataContainer();
                    if (!data.has(VOID_NAMED_KEY, PersistentDataType.BYTE)) {
                        applyVoidName(entity); data.set(VOID_NAMED_KEY, PersistentDataType.BYTE, (byte)1);
                    }
                    if (entity instanceof Monster monster) {
                        if (!data.has(VOID_GENERIC_SPEED_KEY, PersistentDataType.DOUBLE)) { applySpeedBuff(monster); }
                    }
                }
            }
        }
        for(Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.BLOCK_PORTAL_AMBIENT, SoundCategory.AMBIENT, 0.8f, 0.4f);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_SCREAM, SoundCategory.HOSTILE, 0.35f, 0.6f);
            p.sendTitle(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Void Tension", ChatColor.LIGHT_PURPLE + "Reality feels thin and warped...", 10, 80, 20);
        }
    }

    @Override
    public void remove() {
        HandlerList.unregisterAll(this);
        originalBorders.forEach((worldUID, settings) -> {
            World world = Bukkit.getWorld(worldUID);
            if (world != null && (world.getEnvironment() == World.Environment.NORMAL || world.getEnvironment() == World.Environment.THE_END)) {
                WorldBorder border = world.getWorldBorder();
                try {
                    border.setCenter(settings.centerX, settings.centerZ); border.setSize(settings.size);
                    border.setDamageBuffer(settings.damageBuffer); border.setDamageAmount(settings.damageAmount);
                    border.setWarningTime(settings.warningTime); border.setWarningDistance(settings.warningDistance);
                } catch (Exception e) { plugin.getLogger().warning("Failed to restore border for " + world.getName() + ": " + e.getMessage());}
            }
        });
        originalBorders.clear();
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == World.Environment.NORMAL || world.getEnvironment() == World.Environment.NETHER || world.getEnvironment() == World.Environment.THE_END) {
                for (LivingEntity entity : world.getLivingEntities()) {
                    if (entity instanceof Player || entity.isDead()) continue;
                    PersistentDataContainer data = entity.getPersistentDataContainer();
                    if (data.has(VOID_BUFFED_KEY, PersistentDataType.BYTE) && entity instanceof Monster) { removeVoidBuffs((Monster) entity); }
                    if (data.has(VOID_GENERIC_SPEED_KEY, PersistentDataType.DOUBLE) && entity instanceof Monster) { removeSpeedBuff((Monster) entity); }
                    if (data.has(VOID_NAMED_KEY, PersistentDataType.BYTE)) { removeVoidName(entity); }
                }
            }
        }
        for(Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.AMBIENT, 0.7f, 1.1f);
            p.sendTitle(ChatColor.AQUA+"Reality Stabilizes", ChatColor.GRAY+"The void tension dissipates...", 10, 60, 20);
        }
    }

    @Override
    public void tick(long ticksRemaining) {
        long currentTick = plugin.getCurrentTick();
        if (configEnableAnomalies && !anomalyExecutors.isEmpty()) {
            int playerCount = Bukkit.getOnlinePlayers().size();
            if (playerCount > 0) {
                double effectiveChance = 1.0 - Math.pow(1.0 - configAnomalyChancePerTickPerPlayer, playerCount);
                if (random.nextDouble() < effectiveChance) {
                    triggerRandomAnomaly();
                }
            }
        }
        if (currentTick % MOB_TELEPORT_INTERVAL == 0) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                World world = player.getWorld();
                if (world.getEnvironment() != World.Environment.NORMAL && world.getEnvironment() != World.Environment.NETHER && world.getEnvironment() != World.Environment.THE_END) continue;
                for (Entity entity : player.getNearbyEntities(32, 16, 32)) {
                    if (entity instanceof Monster monster && !monster.isDead()) {
                        if (monster.getPersistentDataContainer().has(VOID_NAMED_KEY, PersistentDataType.BYTE)) {
                            if (random.nextDouble() < MOB_TELEPORT_CHANCE_PER_MOB) {
                                Location currentLocation = monster.getLocation();
                                Location targetLocation = findSafeTeleportLocation(currentLocation, MOB_TELEPORT_MAX_DISTANCE);
                                if (targetLocation != null && targetLocation.distanceSquared(currentLocation) > 4) {
                                    world.playSound(currentLocation, Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 0.7f, 0.8f);
                                    world.spawnParticle(Particle.PORTAL, currentLocation.add(0, monster.getHeight() / 2.0, 0), 30, 0.3, 0.5, 0.3, 0.15);
                                    monster.teleport(targetLocation);
                                    world.playSound(targetLocation, Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 0.9f, 1.1f);
                                    world.spawnParticle(Particle.PORTAL, targetLocation.add(0, monster.getHeight() / 2.0, 0), 35, 0.3, 0.5, 0.3, 0.15);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void triggerRandomAnomaly() {
        if (anomalyExecutors.isEmpty()) {
            return;
        }
        anomalyExecutors.get(random.nextInt(anomalyExecutors.size())).get();
    }

    private Player getEligibleRandomPlayer() {
        List<Player> eligiblePlayers = Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.isDead() && (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE))
                .collect(Collectors.toList());
        if (eligiblePlayers.isEmpty()) return null;
        return eligiblePlayers.get(random.nextInt(eligiblePlayers.size()));
    }

    private boolean isValidAnomalyWorld(World world) {
        return world.getEnvironment() == World.Environment.NORMAL ||
                world.getEnvironment() == World.Environment.THE_END ||
                world.getEnvironment() == World.Environment.NETHER;
    }

    private boolean executeAntiGravityPulse() {
        Player targetPlayer = getEligibleRandomPlayer(); if (targetPlayer == null) return false;
        Location center = targetPlayer.getLocation(); World world = center.getWorld();
        if (world == null || !isValidAnomalyWorld(world)) return false;

        plugin.getLogger().finer("[VoidTension Anomaly] Executing Anti-Gravity Pulse near " + targetPlayer.getName());
        world.playSound(center, Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.5f, 0.4f);
        world.spawnParticle(Particle.PORTAL, center, 180, 4.5, 1.8, 4.5, 0.25);
        world.spawnParticle(Particle.REVERSE_PORTAL, center, 120, 4.5, 1.8, 4.5, 0.15);
        for (Player p : getNearbyPlayers(center, 12)) {
            if (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, anomalyAntiGravityPulseDurationTicks, 1), true);
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, anomalyAntiGravityPulseDurationTicks * 2 + 60, 0), true);
                p.sendMessage(ChatColor.LIGHT_PURPLE + "The world lurches, pulling you upwards!");
            }
        }
        return true;
    }

    private boolean executeVoidGrasp() {
        Player targetPlayer = getEligibleRandomPlayer(); if (targetPlayer == null) return false;
        Location center = targetPlayer.getLocation(); World world = center.getWorld();
        if (world == null || !isValidAnomalyWorld(world)) return false;

        plugin.getLogger().finer("[VoidTension Anomaly] Executing Void Grasp near " + targetPlayer.getName());
        world.playSound(center, Sound.ENTITY_EVOKER_PREPARE_WOLOLO, SoundCategory.PLAYERS, 0.9f, 0.6f);
        world.playSound(center, Sound.BLOCK_CONDUIT_DEACTIVATE, SoundCategory.PLAYERS, 1.1f, 0.4f);
        world.spawnParticle(Particle.SQUID_INK, center, 250, 6.5, 2.5, 6.5, 0);
        world.spawnParticle(Particle.CRIT, center, 150, 6.5, 2.5, 6.5, 0.1);
        for (Player p : getNearbyPlayers(center, 15)) {
            if (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, anomalyVoidGraspDurationTicks, 2), true);
                p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, anomalyVoidGraspDurationTicks, 1), true);
                if (PURE_DARKNESS_EFFECT != null) {
                    p.addPotionEffect(new PotionEffect(PURE_DARKNESS_EFFECT, anomalyVoidGraspDurationTicks / 2, 0), true);
                } else {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, (anomalyVoidGraspDurationTicks / 4), 0), true); // Shorter fallback
                }
                p.sendMessage(ChatColor.DARK_PURPLE + "An unseen force grasps at you...");
            }
        }
        return true;
    }

    private boolean executeRealityTear() {
        Player targetPlayer = getEligibleRandomPlayer(); if (targetPlayer == null) return false;
        Location center = targetPlayer.getLocation(); World world = center.getWorld();
        if (world == null || !isValidAnomalyWorld(world)) return false;
        plugin.getLogger().finer("[VoidTension Anomaly] Executing Reality Tear near " + targetPlayer.getName());
        Location soundLoc = center.clone().add(random.nextGaussian() * 7, random.nextGaussian() * 3.5, random.nextGaussian() * 7);
        world.playSound(soundLoc, Sound.ENTITY_ENDERMAN_STARE, SoundCategory.AMBIENT, 1.3f, random.nextFloat() * 0.3f + 0.5f);
        world.playSound(soundLoc, Sound.BLOCK_GLASS_BREAK, SoundCategory.BLOCKS, 0.6f, 0.7f);
        world.spawnParticle(Particle.SMOKE, soundLoc, 20, 0.3, 0.3, 0.3, 0.03);
        world.spawnParticle(Particle.CRIT, soundLoc, 25, 0.6, 0.6, 0.6, 0.12);
        for (Player p : getNearbyPlayers(center, 10)) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, anomalyRealityTearDurationTicks, 0), true);
        }
        targetPlayer.sendMessage(ChatColor.GRAY + "You hear something... unsettling nearby.");
        return true;
    }

    private boolean executeSpatialWarp() {
        Player targetPlayer = getEligibleRandomPlayer(); if (targetPlayer == null) return false;
        World world = targetPlayer.getWorld();
        if (world == null || !isValidAnomalyWorld(world)) return false;
        plugin.getLogger().finer("[VoidTension Anomaly] Executing Spatial Warp for " + targetPlayer.getName());
        Location playerOriginalLoc = targetPlayer.getLocation();
        Location targetTeleportLoc = findSafeTeleportLocation(playerOriginalLoc, 8.0);
        if (targetTeleportLoc != null && targetTeleportLoc.distanceSquared(playerOriginalLoc) > 9) {
            targetPlayer.teleport(targetTeleportLoc);
            world.playSound(targetTeleportLoc, Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 0.7f);
            world.spawnParticle(Particle.PORTAL, targetTeleportLoc, 40, 0.5, 0.8, 0.5, 0.1);
            targetPlayer.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, anomalySpatialWarpDurationTicks, 0), true);
            targetPlayer.sendMessage(ChatColor.LIGHT_PURPLE + "You flicker through space!");
        } else {
            targetPlayer.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, anomalySpatialWarpDurationTicks, 0), true);
            targetPlayer.playSound(playerOriginalLoc, Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.5f, 0.5f);
            targetPlayer.sendMessage(ChatColor.GRAY + "Space warps around you momentarily.");
        }
        return true;
    }

    private boolean executeVoidShriek() {
        Player targetPlayer = getEligibleRandomPlayer(); if (targetPlayer == null) return false;
        Location center = targetPlayer.getLocation(); World world = center.getWorld();
        if (world == null || !isValidAnomalyWorld(world)) return false;
        plugin.getLogger().finer("[VoidTension Anomaly] Executing Void Shriek near " + targetPlayer.getName());
        world.playSound(center, Sound.ENTITY_GHAST_SCREAM, SoundCategory.AMBIENT, 1.2f, 0.5f);
        world.playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.AMBIENT, 0.6f, 0.6f);
        if (SONIC_BOOM_PARTICLE != null) world.spawnParticle(SONIC_BOOM_PARTICLE, center, 1, 0,0,0,0);
        else world.spawnParticle(Particle.EXPLOSION, center, 1,0,0,0,0);
        for (Player p : getNearbyPlayers(center, 20)) {
            if (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, anomalyVoidShriekDurationTicks, 1), true);
                p.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, anomalyVoidShriekDurationTicks, 1), true);
                p.sendMessage(ChatColor.DARK_RED + "A piercing shriek echoes from the void!");
            }
        }
        return true;
    }

    private boolean executeUnstableEnergy() {
        Player targetPlayer = getEligibleRandomPlayer(); if (targetPlayer == null) return false;
        Location center = targetPlayer.getLocation(); World world = center.getWorld();
        if (world == null || !isValidAnomalyWorld(world)) return false;
        plugin.getLogger().finer("[VoidTension Anomaly] Executing Unstable Energy on " + targetPlayer.getName());
        world.playSound(center, Sound.BLOCK_BEACON_AMBIENT, SoundCategory.PLAYERS, 1.0f, 1.8f);
        world.spawnParticle(Particle.ENCHANTED_HIT, center, 120, 3.5, 1.5, 3.5, 0.15);
        PotionEffectType randomEffectType; int amplifier = 0;
        switch(random.nextInt(6)) {
            case 0: randomEffectType = PotionEffectType.SPEED; amplifier = 1; break;
            case 1: randomEffectType = PotionEffectType.SLOWNESS; break;
            case 2: randomEffectType = PotionEffectType.HASTE; amplifier = 0; break;
            case 3: randomEffectType = PotionEffectType.MINING_FATIGUE; break;
            case 4: randomEffectType = PotionEffectType.JUMP_BOOST; amplifier = 1; break;
            default: randomEffectType = PotionEffectType.WEAKNESS; break;
        }
        PotionEffect randomGeneratedEffect = new PotionEffect(randomEffectType, anomalyUnstableEnergyDurationTicks, amplifier, true, true, true);
        targetPlayer.addPotionEffect(randomGeneratedEffect, true);
        targetPlayer.sendMessage(ChatColor.LIGHT_PURPLE + "Unstable energy washes over you ("+randomEffectType.getName().toLowerCase().replace("_"," ")+")...");
        return true;
    }

    private boolean executeChronoStutter() {
        Player targetPlayer = getEligibleRandomPlayer(); if (targetPlayer == null) return false;
        Location center = targetPlayer.getLocation(); World world = center.getWorld();
        if (world == null || !isValidAnomalyWorld(world)) return false;
        plugin.getLogger().finer("[VoidTension Anomaly] Executing Chrono Stutter near " + targetPlayer.getName());
        world.playSound(center, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, SoundCategory.PLAYERS, 1.0f, 0.4f);
        world.playSound(center, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, SoundCategory.PLAYERS, 1.0f, 1.6f);
        world.spawnParticle(Particle.REVERSE_PORTAL, center, 90, 3.5, 1.2, 3.5, 0.08);
        for (Player p : getNearbyPlayers(center, 10)) {
            if (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, anomalyChronoStutterDurationTicks, 3), true);
                p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, anomalyChronoStutterDurationTicks, 2, false, true, true), true);
                p.sendMessage(ChatColor.AQUA + "Time stutters around you!");
            }
        }
        return true;
    }

    private boolean executeWhisperingMadness() {
        Player targetPlayer = getEligibleRandomPlayer(); if (targetPlayer == null) return false;
        World world = targetPlayer.getWorld();
        if (world == null || !isValidAnomalyWorld(world)) return false;
        plugin.getLogger().finer("[VoidTension Anomaly] Executing Whispering Madness on " + targetPlayer.getName());
        world.playSound(targetPlayer.getLocation(), Sound.ENTITY_ENDERMAN_AMBIENT, SoundCategory.PLAYERS, 0.9f, 0.5f);
        world.playSound(targetPlayer.getLocation(), Sound.ENTITY_GHAST_WARN, SoundCategory.PLAYERS, 0.6f, 1.9f);
        for (int i = 0; i < 18; i++) {
            double angle = Math.toRadians(i * 20); double radius = 0.7;
            Location particleLoc = targetPlayer.getEyeLocation().add(radius * Math.cos(angle), random.nextDouble() * 0.4 - 0.2, radius * Math.sin(angle));
            world.spawnParticle(Particle.SMOKE, particleLoc, 1, 0, 0, 0, 0);
        }
        targetPlayer.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, anomalyWhisperingMadnessDurationTicks, 0), true);
        targetPlayer.sendMessage(ChatColor.DARK_GRAY + "Whispers echo directly in your mind...");
        return true;
    }

    private boolean executeUnstableGround() {
        Player targetPlayer = getEligibleRandomPlayer(); if (targetPlayer == null) return false;
        Location center = targetPlayer.getLocation(); World world = center.getWorld();
        if (world == null || !isValidAnomalyWorld(world)) return false;
        plugin.getLogger().finer("[VoidTension Anomaly] Executing Unstable Ground near " + targetPlayer.getName());

        world.playSound(center, Sound.BLOCK_GRINDSTONE_USE, SoundCategory.BLOCKS, 1.1f, 0.4f);
        world.playSound(center, Sound.BLOCK_DEEPSLATE_BREAK, SoundCategory.BLOCKS, 0.9f, 0.6f);

        List<Material> unstableTypesList = new ArrayList<>(Arrays.asList(Material.CRYING_OBSIDIAN, Material.MAGMA_BLOCK));
        if (Material.getMaterial("AMETHYST_BLOCK") != null) unstableTypesList.add(Material.AMETHYST_BLOCK);
        if (SCULK_MATERIAL != null) unstableTypesList.add(SCULK_MATERIAL);
        if (unstableTypesList.isEmpty()) unstableTypesList.add(Material.NETHERRACK);

        Material blockDataParticleMat = unstableTypesList.get(random.nextInt(unstableTypesList.size()));
        world.spawnParticle(Particle.BLOCK_CRUMBLE, center.clone().add(0, 0.2, 0), 60, 3, 0.2, 3, 0, blockDataParticleMat.createBlockData());

        List<Block> changedBlocksList = new ArrayList<>(); List<BlockData> originalDataList = new ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (random.nextDouble() < 0.45) {
                    Block currentBlock = center.clone().add(dx, -1, dz).getBlock();
                    if (currentBlock.getType().isSolid() && !currentBlock.isLiquid() &&
                            currentBlock.getType().getHardness() < 50 && !(currentBlock.getState() instanceof Container) &&
                            currentBlock.getType() != Material.BEDROCK && currentBlock.getType() != Material.BARRIER ) {
                        changedBlocksList.add(currentBlock); originalDataList.add(currentBlock.getBlockData());
                        currentBlock.setType(unstableTypesList.get(random.nextInt(unstableTypesList.size())), false);
                    }
                }
            }
        }
        if (!changedBlocksList.isEmpty()) {
            long revertDelayTicks = anomalyUnstableGroundDurationTicks + random.nextInt(4 * 20);
            if (revertDelayTicks < 20L) revertDelayTicks = 20L;

            new BukkitRunnable() {
                @Override
                public void run() {
                    for (int i = 0; i < changedBlocksList.size(); i++) {
                        Block blockToRevert = changedBlocksList.get(i);
                        if (unstableTypesList.contains(blockToRevert.getType())) {
                            blockToRevert.setBlockData(originalDataList.get(i), true);
                        }
                    }
                    world.playSound(center, Sound.BLOCK_DEEPSLATE_PLACE, SoundCategory.BLOCKS, 0.8f, 0.9f);
                }
            }.runTaskLater(plugin, revertDelayTicks);

            for (Player p : getNearbyPlayers(center, 8)) {
                p.sendMessage(ChatColor.DARK_PURPLE + "The ground beneath feels unstable!");
            }
        }
        return true;
    }

    private boolean executeVoidLeech() {
        Player targetPlayer = getEligibleRandomPlayer(); if (targetPlayer == null) return false;
        Location center = targetPlayer.getLocation(); World world = center.getWorld();
        if (world == null || !isValidAnomalyWorld(world)) return false;
        plugin.getLogger().finer("[VoidTension Anomaly] Executing Void Leech near " + targetPlayer.getName());

        world.playSound(center, Sound.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH, SoundCategory.PLAYERS, 1.0f, 0.7f);
        world.playSound(center, Sound.PARTICLE_SOUL_ESCAPE, SoundCategory.PLAYERS, 0.9f, 1.1f);
        Particle.DustOptions purpleDustOptions = new Particle.DustOptions(Color.fromRGB(100, 0, 120), 1.3f);
        for (Player p : getNearbyPlayers(center, 12)) {
            if (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) {
                int weaknessDuration = anomalyVoidLeechWitherDurationTicks + (DEFAULT_MEDIUM_DURATION_SECONDS - DEFAULT_SHORT_DURATION_SECONDS) * 20; // e.g., Wither 6s (120t), Weakness 10s (200t)
                p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, weaknessDuration, 0), true);
                p.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, anomalyVoidLeechWitherDurationTicks, 0), true);
                p.sendMessage(ChatColor.DARK_PURPLE + "A draining energy touches you...");
                Location pLoc = p.getEyeLocation(); Vector direction = center.toVector().subtract(pLoc.toVector()).normalize().multiply(0.6);
                for(int i = 0; i<6; i++) {
                    world.spawnParticle(Particle.DUST, pLoc.clone().add(random.nextGaussian()*0.4, random.nextGaussian()*0.4, random.nextGaussian()*0.4) , 0, direction.getX(), direction.getY(), direction.getZ(), 0.6, purpleDustOptions);
                }
            }
        }
        return true;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player || entity.isDead()) return;
        PersistentDataContainer data = entity.getPersistentDataContainer();

        if (!data.has(VOID_NAMED_KEY, PersistentDataType.BYTE)) {
            applyVoidName(entity); data.set(VOID_NAMED_KEY, PersistentDataType.BYTE, (byte)1);
        }

        if (entity instanceof Monster monster) {
            if (!data.has(VOID_GENERIC_SPEED_KEY, PersistentDataType.DOUBLE)) {
                applySpeedBuff(monster);
            }
            CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
            boolean shouldConsiderBuff = switch (reason) {
                case NATURAL, SPAWNER, REINFORCEMENTS, PATROL, RAID, DROWNED, JOCKEY, MOUNT, SLIME_SPLIT, SILVERFISH_BLOCK -> true;
                default -> false;
            };
            if (shouldConsiderBuff && random.nextDouble() < configStrongMobSpawnChance) {
                makeMobStrong(monster);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        PersistentDataContainer data = event.getEntity().getPersistentDataContainer();
        data.remove(VOID_BUFFED_KEY);
        data.remove(VOID_NAMED_KEY);
        data.remove(VOID_GENERIC_SPEED_KEY);
        data.remove(VOID_HEALTH_KEY);
        data.remove(VOID_DAMAGE_KEY);
    }

    private void applyVoidName(LivingEntity entity) {
        String typeName = entity.getType().name().replace("_", " ");
        String[] words = typeName.toLowerCase().split(" ");
        for (int i = 0; i < words.length; i++) { if (!words[i].isEmpty()) { words[i] = Character.toUpperCase(words[i].charAt(0)) + words[i].substring(1); } }
        String formattedTypeName = String.join(" ", words);
        entity.setCustomName(ChatColor.DARK_PURPLE + "Void Touched " + ChatColor.LIGHT_PURPLE + formattedTypeName);
        entity.setCustomNameVisible(false);
    }

    private void removeVoidName(LivingEntity entity) {
        String currentName = entity.getCustomName();
        if (currentName != null && currentName.startsWith(ChatColor.DARK_PURPLE + "Void Touched ")) {
            entity.setCustomName(null);
        }
        entity.getPersistentDataContainer().remove(VOID_NAMED_KEY);
    }

    private boolean applySpeedBuff(Monster monster) {
        PersistentDataContainer data = monster.getPersistentDataContainer();
        AttributeInstance speedAttr = monster.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttr != null && !data.has(VOID_GENERIC_SPEED_KEY, PersistentDataType.DOUBLE)) {
            try {
                double original = speedAttr.getBaseValue();
                double newSpeed = original * UNIVERSAL_SPEED_MULTIPLIER;
                if (newSpeed <= 0.001 && original > 0.001) newSpeed = original * 0.1;
                else if (newSpeed <= 0.001 && original <= 0.001) newSpeed = 0.01;
                speedAttr.setBaseValue(newSpeed);
                data.set(VOID_GENERIC_SPEED_KEY, PersistentDataType.DOUBLE, original);
                return true;
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to apply speed buff to " + monster.getType() + ": " + e.getMessage());
            }
        }
        return false;
    }

    private boolean removeSpeedBuff(Monster monster) {
        PersistentDataContainer data = monster.getPersistentDataContainer();
        AttributeInstance speedAttr = monster.getAttribute(Attribute.MOVEMENT_SPEED);
        if (data.has(VOID_GENERIC_SPEED_KEY, PersistentDataType.DOUBLE) && speedAttr != null) {
            try {
                double originalSpeed = data.get(VOID_GENERIC_SPEED_KEY, PersistentDataType.DOUBLE);
                speedAttr.setBaseValue(originalSpeed);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to remove speed buff from " + monster.getType() + ": " + e.getMessage());
            } finally {
                data.remove(VOID_GENERIC_SPEED_KEY);
            }
            return true;
        }
        return false;
    }

    private void makeMobStrong(Monster monster) {
        PersistentDataContainer data = monster.getPersistentDataContainer();
        if (data.has(VOID_BUFFED_KEY, PersistentDataType.BYTE)) return;
        boolean appliedAnyBuff = false;

        AttributeInstance maxHealthAttr = monster.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null && !data.has(VOID_HEALTH_KEY, PersistentDataType.DOUBLE)) {
            try {
                double original = maxHealthAttr.getBaseValue();
                double newHealth = Math.max(1.0, original * 1.5);
                maxHealthAttr.setBaseValue(newHealth);
                data.set(VOID_HEALTH_KEY, PersistentDataType.DOUBLE, original);
                if (!monster.isDead()) monster.setHealth(newHealth);
                appliedAnyBuff = true;
            } catch (Exception e) {plugin.getLogger().warning("Failed to buff health for " + monster.getType() + ": " + e.getMessage());}
        }

        AttributeInstance attackDamageAttr = monster.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attackDamageAttr != null && !data.has(VOID_DAMAGE_KEY, PersistentDataType.DOUBLE)) {
            try {
                double original = attackDamageAttr.getBaseValue();
                double newDamage = original * 1.3;
                if (newDamage <= original && original > 0) newDamage = original + 1.0;
                else if (newDamage <= 0 && original <= 0) newDamage = 1.0;
                attackDamageAttr.setBaseValue(newDamage);
                data.set(VOID_DAMAGE_KEY, PersistentDataType.DOUBLE, original);
                appliedAnyBuff = true;
            } catch (Exception e) {plugin.getLogger().warning("Failed to buff damage for " + monster.getType() + ": " + e.getMessage());}
        }
        if (appliedAnyBuff) data.set(VOID_BUFFED_KEY, PersistentDataType.BYTE, (byte)1);
    }

    private void removeVoidBuffs(Monster monster) {
        PersistentDataContainer data = monster.getPersistentDataContainer();
        data.remove(VOID_BUFFED_KEY);

        AttributeInstance maxHealthAttr = monster.getAttribute(Attribute.MAX_HEALTH);
        if (data.has(VOID_HEALTH_KEY, PersistentDataType.DOUBLE) && maxHealthAttr != null) {
            try {
                double originalHealth = data.get(VOID_HEALTH_KEY, PersistentDataType.DOUBLE);
                maxHealthAttr.setBaseValue(originalHealth);
                if (monster.getHealth() > originalHealth && !monster.isDead()) monster.setHealth(originalHealth);
            } catch (Exception e) { plugin.getLogger().warning("Failed to restore health for " + monster.getType() + ": " + e.getMessage());}
            finally { data.remove(VOID_HEALTH_KEY); }
        }
        AttributeInstance attackDamageAttr = monster.getAttribute(Attribute.ATTACK_DAMAGE);
        if (data.has(VOID_DAMAGE_KEY, PersistentDataType.DOUBLE) && attackDamageAttr != null) {
            try {
                double originalDamage = data.get(VOID_DAMAGE_KEY, PersistentDataType.DOUBLE);
                attackDamageAttr.setBaseValue(originalDamage);
            } catch (Exception e) { plugin.getLogger().warning("Failed to restore damage for " + monster.getType() + ": " + e.getMessage());}
            finally { data.remove(VOID_DAMAGE_KEY); }
        }
    }

    private List<Player> getNearbyPlayers(Location location, double radius) {
        List<Player> nearby = new ArrayList<>(); double radiusSq = radius * radius;
        World world = location.getWorld();
        if (world == null) return nearby;
        for (Player p : world.getPlayers()) {
            if (!p.isDead() && p.getLocation().distanceSquared(location) <= radiusSq) {
                nearby.add(p);
            }
        } return nearby;
    }

    private Location findSafeTeleportLocation(Location origin, double maxRadius) {
        World world = origin.getWorld(); if (world == null) return null;
        for (int i = 0; i < 20; i++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double radius = (random.nextDouble() * 0.6 + 0.4) * maxRadius;
            double x = origin.getX() + radius * Math.cos(angle);
            double z = origin.getZ() + radius * Math.sin(angle);
            int originY = origin.getBlockY();
            int Y_SEARCH_RANGE = 8;
            for (int dy = 0; dy <= Y_SEARCH_RANGE * 2; dy++) {
                int y = originY - Y_SEARCH_RANGE + dy;
                if (y < world.getMinHeight() || y > world.getMaxHeight() -2 ) continue;

                Location potentialLoc = new Location(world, Math.floor(x) + 0.5, y, Math.floor(z) + 0.5);
                if (!world.isChunkLoaded(potentialLoc.getBlockX() >> 4, potentialLoc.getBlockZ() >> 4)) continue;

                Block blockBelow = potentialLoc.clone().subtract(0,1,0).getBlock();
                Block blockAt = potentialLoc.getBlock();
                Block blockAbove = potentialLoc.clone().add(0,1,0).getBlock();

                if (blockBelow.getType().isSolid() && !blockBelow.isLiquid() &&
                        blockAt.isPassable() && !blockAt.isLiquid() &&
                        blockAbove.isPassable() && !blockAbove.isLiquid()) {
                    return potentialLoc;
                }
            }
        } return null;
    }

    @Override public void onPlayerJoin(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_AMBIENT, SoundCategory.AMBIENT, 0.8f, 0.4f);
        player.sendTitle(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Void Tension", ChatColor.LIGHT_PURPLE + "Reality feels thin and warped...", 10, 80, 20);
    }
    @Override public void onPlayerQuit(Player player) {}

}