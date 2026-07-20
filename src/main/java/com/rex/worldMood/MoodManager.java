package com.rex.worldMood;

import com.rex.worldMood.moods.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class MoodManager implements Listener {

    private final WorldMood plugin;
    private final Map<String, Mood> availableMoods = new HashMap<>();
    private final List<Mood> weightedMoodList = new ArrayList<>();
    private Mood currentMood = null;
    private BukkitTask moodCycleTask = null;
    private BukkitTask moodDurationTask = null;
    private BukkitTask moodTickTask = null;

    private BossBar moodBossBar = null;
    private final NamespacedKey bossBarKey;

    private Scoreboard temporaryHudScoreboard = null;
    private BukkitTask hudHideTask = null;
    private final Map<UUID, Scoreboard> playerOriginalScoreboards = new ConcurrentHashMap<>();
    private static final String SCOREBOARD_OBJECTIVE_NAME = "wm_hud";
    /** ChatColor.values()[0..15] are the colour codes — one unique prefix per HUD line. */
    private static final int MAX_HUD_LINES = 15;
    private static final int MAX_ENTRY_LENGTH = 40;

    public MoodManager(WorldMood plugin) {
        this.plugin = plugin;
        this.bossBarKey = new NamespacedKey(plugin, "worldmood_bossbar");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void loadMoods() {
        availableMoods.clear();
        weightedMoodList.clear();
        FileConfiguration config = plugin.getConfig();

        registerMood(new CalmSkies(plugin));
        registerMood(new InfernalHeat(plugin));
        registerMood(new BloodMoon(plugin));
        registerMood(new ProsperousWinds(plugin));
        registerMood(new ShadowVeil(plugin));
        registerMood(new LuckyDay(plugin));
        registerMood(new VoidTension(plugin));

        availableMoods.values().stream()
                .filter(Mood::isEnabled)
                .forEach(mood -> {
                    for (int i = 0; i < mood.getWeight(); i++) {
                        weightedMoodList.add(mood);
                    }
                });

        plugin.getLogger().info("Loaded " + availableMoods.size() + " enabled moods.");

        BossBar existing = Bukkit.getBossBar(bossBarKey);
        if (existing != null) {
            existing.removeAll();
            Bukkit.removeBossBar(bossBarKey);
        }

        if (config.getBoolean("useBossBar", true)) {
            moodBossBar = Bukkit.createBossBar(bossBarKey, "No Active Mood", BarColor.BLUE, BarStyle.SOLID);
            moodBossBar.setVisible(false);
        } else {
            moodBossBar = null;
        }
    }

    private void registerMood(Mood mood) {
        if (mood.isEnabled()) {
            availableMoods.put(mood.getConfigKey().toLowerCase(), mood);
        }
    }

    public void startMoodCycle() {
        stopMoodCycle();

        if (!plugin.getConfig().getBoolean("randomizeMoods", true) || weightedMoodList.isEmpty()) {
            return;
        }

        long frequencyTicks = plugin.getConfig().getLong("moodFrequencyMinutes", 30) * 60 * 20;
        if (frequencyTicks <= 0) {
            plugin.getLogger().warning("Mood frequency is set to 0 or less, disabling random cycle.");
            return;
        }

        moodCycleTask = new BukkitRunnable() {
            @Override
            public void run() {
                startRandomMood();
            }
        }.runTaskTimer(plugin, 100L, frequencyTicks);

        plugin.getLogger().info("Started random mood cycle.");
    }

    public void stopMoodCycle() {
        if (moodCycleTask != null && !moodCycleTask.isCancelled()) {
            moodCycleTask.cancel();
        }
        moodCycleTask = null;
        stopCurrentMood();
    }

    public boolean startRandomMood() {
        if (currentMood != null) {
            plugin.getLogger().warning("Tried to start a random mood while one is already active: " + currentMood.getName());
            return false;
        }
        if (weightedMoodList.isEmpty()) {
            plugin.getLogger().warning("Cannot start random mood: No enabled moods found");
            return false;
        }

        // Filter to moods that can actually run right now BEFORE picking one. Picking first and
        // then rejecting on time-of-day meant a single unlucky draw wasted the entire cycle
        // interval (30 minutes by default) doing nothing at all, which reads as "plugin is dead".
        List<Mood> eligible = weightedMoodList.stream()
                .filter(this::isEligibleNow)
                .collect(Collectors.toList());

        if (eligible.isEmpty()) {
            plugin.getLogger().info("No moods are eligible at this time of day. Trying again next cycle.");
            return false;
        }

        // The list still holds one entry per weight point, so weighting is preserved.
        Mood selectedMood = eligible.get(ThreadLocalRandom.current().nextInt(eligible.size()));
        return startMood(selectedMood);
    }

    /** Whether a mood's time-of-day requirement is satisfied right now. */
    private boolean isEligibleNow(Mood mood) {
        if (!mood.requiresNight() && !mood.requiresDay()) return true;

        World primaryWorld = Bukkit.getWorlds().stream()
                .filter(w -> w.getEnvironment() == World.Environment.NORMAL)
                .findFirst().orElse(null);

        if (primaryWorld == null) {
            plugin.getLogger().warning("No Overworld found; cannot evaluate the time restriction on '"
                    + mood.getName() + "'.");
            return false;
        }

        long time = primaryWorld.getTime();
        boolean isNight = time >= 12500 && time < 24000;
        return mood.requiresNight() ? isNight : !isNight;
    }

    public boolean startSpecificMood(String moodKey) {
        if (currentMood != null) {
            stopCurrentMood(false);
        }
        Mood moodToStart = availableMoods.get(moodKey.toLowerCase());
        if (moodToStart == null) {
            plugin.getLogger().severe("Mood with key '" + moodKey + "' not found or not enabled.");
            return false;
        }
        if (!moodToStart.isEnabled()) {
            plugin.getLogger().warning("Mood '" + moodKey + "' is disabled in the config.");
            return false;
        }

        return startMood(moodToStart);
    }

    private boolean startMood(Mood mood) {
        if (currentMood != null) {
            plugin.getLogger().severe("INTERNAL ERROR: Attempted to start mood " + mood.getName() + " while " + currentMood.getName() + " is active.");
            return false;
        }

        currentMood = mood;
        plugin.getLogger().info("Starting mood: " + currentMood.getName());

        currentMood.resetActivationState();
        currentMood.apply();

        if (plugin.getConfig().getBoolean("broadcastMoodChanges", true)) {
            Bukkit.broadcastMessage(ChatColor.DARK_AQUA + "[WorldMood] " + ChatColor.AQUA + "The atmosphere shifts... " + ChatColor.BOLD + currentMood.getName() + ChatColor.RESET + ChatColor.AQUA + " has begun!");
            Bukkit.broadcastMessage(ChatColor.GRAY + " > " + ChatColor.ITALIC + currentMood.getDescription());
        }

        updateHUDStart();
        for (Player player : Bukkit.getOnlinePlayers()) {
            showHUD(player);
            currentMood.onPlayerJoin(player);
        }

        long durationTicks = calculateDuration(mood);
        final long finalDurationTicks = durationTicks;

        if (plugin.getConfig().getBoolean("useScoreboardHud", true)) {
            createAndShowScoreboardHUD();
            long hideDelayTicks = plugin.getConfig().getLong("hudDisplaySeconds", 15) * 20L;
            if (hideDelayTicks > 0) {
                if (hudHideTask != null) hudHideTask.cancel();
                hudHideTask = new BukkitRunnable() {
                    @Override
                    public void run() {
                        clearScoreboardHUD();
                        hudHideTask = null;
                    }
                }.runTaskLater(plugin, hideDelayTicks);
            } else {
                clearScoreboardHUD();
            }
        }

        moodDurationTask = new BukkitRunnable() {
            @Override
            public void run() {
                stopCurrentMood();
            }
        }.runTaskLater(plugin, finalDurationTicks);

        moodTickTask = new BukkitRunnable() {
            long ticksRemaining = finalDurationTicks;
            @Override
            public void run() {
                if (currentMood == null) {
                    this.cancel();
                    return;
                }
                ticksRemaining -= 20;
                if (ticksRemaining < 0) ticksRemaining = 0;

                currentMood.handleTick(ticksRemaining);
                updateHUDProgress(ticksRemaining, finalDurationTicks);

                if (ticksRemaining <= 0) {
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);

        return true;
    }

    public void stopCurrentMood() {
        stopCurrentMood(true);
    }

    public void stopCurrentMood(boolean broadcast) {
        if (currentMood == null) return;

        plugin.getLogger().info("Stopping mood: " + currentMood.getName());

        if (hudHideTask != null && !hudHideTask.isCancelled()) {
            hudHideTask.cancel();
            hudHideTask = null;
        }
        clearScoreboardHUD();

        if (moodDurationTask != null) moodDurationTask.cancel();
        moodDurationTask = null;

        if (moodTickTask != null) moodTickTask.cancel();
        moodTickTask = null;

        currentMood.remove();
        for (Player player : Bukkit.getOnlinePlayers()) {
            currentMood.onPlayerQuit(player);
        }

        if (broadcast && plugin.getConfig().getBoolean("broadcastMoodChanges", true)) {
            Bukkit.broadcastMessage(ChatColor.DARK_AQUA + "[WorldMood] " + ChatColor.GRAY + "The " + ChatColor.BOLD + currentMood.getName() + ChatColor.RESET + ChatColor.GRAY + " mood fades away.");
        }

        currentMood = null;
        clearHUD();
    }

    private long calculateDuration(Mood mood) {
        World primaryWorld = Bukkit.getWorlds().stream()
                .filter(w -> w.getEnvironment() == World.Environment.NORMAL)
                .findFirst().orElse(null);
        long currentTime = (primaryWorld != null) ? primaryWorld.getTime() : -1;
        long durationTicks;

        if (mood instanceof VoidTension) {
            durationTicks = 24000L;
        }
        else if ((mood instanceof CalmSkies || mood instanceof LuckyDay || mood instanceof ProsperousWinds) && primaryWorld != null) {
            durationTicks = (currentTime < 12500) ? (12500 - currentTime) : (24000 - currentTime);
            if (durationTicks < 20 * 10) durationTicks = 20 * 10;
        }
        else if (mood instanceof ShadowVeil && primaryWorld != null) {
            if (currentTime >= 12500 && currentTime < 24000) {
                durationTicks = (24000 - currentTime);
                if (durationTicks < 20 * 10) durationTicks = 20 * 10;
            } else {
                durationTicks = mood.getDuration() * 20L;
            }
        }
        else if (mood instanceof InfernalHeat && primaryWorld != null) {
            if (currentTime >= 0 && currentTime < 12500) {
                durationTicks = 12500 - currentTime;
                if (durationTicks < 20 * 10) durationTicks = 20 * 10;
            } else {
                durationTicks = mood.getDuration() * 20L;
            }
        }
        else {
            if (currentTime == -1 && (mood instanceof CalmSkies || mood instanceof LuckyDay || mood instanceof InfernalHeat || mood instanceof ProsperousWinds || mood instanceof ShadowVeil)) {
                plugin.getLogger().warning("Could not find Overworld for dynamic duration. Using configured duration.");
            }
            durationTicks = mood.getDuration() * 20L;
        }

        if (durationTicks <= 0) {
            plugin.getLogger().warning("Mood duration calculated to invalid value. Setting to default 300s.");
            return 300 * 20L;
        }
        return durationTicks;
    }

    private void updateHUDStart() {
        if (moodBossBar == null || currentMood == null) return;

        BarColor color = BarColor.BLUE;
        if (currentMood instanceof InfernalHeat || currentMood instanceof BloodMoon) color = BarColor.RED;
        else if (currentMood instanceof CalmSkies || currentMood instanceof ProsperousWinds) color = BarColor.GREEN;
        else if (currentMood instanceof LuckyDay) color = BarColor.YELLOW;
        else if (currentMood instanceof ShadowVeil || currentMood instanceof VoidTension) color = BarColor.PURPLE;

        moodBossBar.setColor(color);
        moodBossBar.setTitle(ChatColor.BOLD + currentMood.getName());
        moodBossBar.setProgress(1.0);
        moodBossBar.setVisible(true);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!moodBossBar.getPlayers().contains(player)) {
                moodBossBar.addPlayer(player);
            }
        }
    }

    private void updateHUDProgress(long ticksRemaining, long totalDurationTicks) {
        if (moodBossBar == null || currentMood == null || !moodBossBar.isVisible()) return;

        double progress = (totalDurationTicks <= 0) ? 0 : (double) ticksRemaining / totalDurationTicks;
        progress = Math.max(0, Math.min(1, progress));
        moodBossBar.setProgress(progress);

        long secondsRemaining = ticksRemaining / 20;
        moodBossBar.setTitle(ChatColor.BOLD + currentMood.getName() + ChatColor.RESET + " - " + formatTime(secondsRemaining));
    }

    private void clearHUD() {
        if (moodBossBar != null) {
            moodBossBar.setVisible(false);
            moodBossBar.setProgress(1.0);
            moodBossBar.setTitle("No Active Mood");
            moodBossBar.setColor(BarColor.BLUE);
        }
    }

    private void showHUD(Player player) {
        if (moodBossBar != null && currentMood != null && moodBossBar.isVisible()) {
            if (!moodBossBar.getPlayers().contains(player)) {
                moodBossBar.addPlayer(player);
            }
        }
    }

    private void createAndShowScoreboardHUD() {
        if (currentMood == null || !plugin.getConfig().getBoolean("useScoreboardHud", true)) return;

        clearScoreboardHUD();

        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            plugin.getLogger().severe("ScoreboardManager is null! Cannot create HUD.");
            return;
        }
        temporaryHudScoreboard = manager.getNewScoreboard();
        Objective objective = temporaryHudScoreboard.registerNewObjective(SCOREBOARD_OBJECTIVE_NAME, Criteria.DUMMY, ChatColor.AQUA + "" + ChatColor.BOLD + "World Mood");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.YELLOW + "" + ChatColor.BOLD + currentMood.getName());
        lines.add(" ");

        if (currentMood.getDescription() != null && !currentMood.getDescription().isEmpty()) {
            lines.add(ChatColor.GRAY + currentMood.getDescription());
            lines.add("  ");
        }

        List<String> effects = currentMood.getEffects();
        if (effects != null && !effects.isEmpty()) {
            lines.add(ChatColor.GOLD + "Effects:");
            effects.forEach(effect -> lines.add(ChatColor.WHITE + "- " + (effect.length() > 35 ? effect.substring(0, 32) + "..." : effect)));
        }

        // Each entry gets a unique colour-code prefix derived from its INDEX, not its hash.
        // Index-derived prefixes are unique by construction, so no retry loop is needed.
        // (The previous hash-based version could spin forever: prepending a 2-char code and then
        // trimming back to 40 chars removed exactly the characters it had just added.)
        int scoreValue = lines.size();
        for (int index = 0; index < lines.size(); index++) {
            if (index >= MAX_HUD_LINES) {
                plugin.getLogger().warning("Scoreboard HUD had more lines than can be displayed ("
                        + lines.size() + "); showing the first " + MAX_HUD_LINES + ".");
                break;
            }
            String prefix = ChatColor.values()[index].toString() + ChatColor.RESET;
            String entry = prefix + trimToLength(lines.get(index), MAX_ENTRY_LENGTH - prefix.length());

            objective.getScore(entry).setScore(scoreValue - index);
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!playerOriginalScoreboards.containsKey(player.getUniqueId())) {
                playerOriginalScoreboards.put(player.getUniqueId(), player.getScoreboard());
            }
            player.setScoreboard(temporaryHudScoreboard);
        }
    }

    private void clearScoreboardHUD() {
        Iterator<Map.Entry<UUID, Scoreboard>> iterator = playerOriginalScoreboards.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Scoreboard> entry = iterator.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                if (temporaryHudScoreboard == null || player.getScoreboard().equals(temporaryHudScoreboard)) {
                    player.setScoreboard(entry.getValue());
                }
            }
            iterator.remove();
        }

        if (temporaryHudScoreboard != null) {
            Objective objective = temporaryHudScoreboard.getObjective(SCOREBOARD_OBJECTIVE_NAME);
            if (objective != null) {
                objective.unregister();
            }
            temporaryHudScoreboard = null;
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (currentMood != null) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline()) {
                        if (plugin.getConfig().getBoolean("useBossBar", true)) showHUD(player);
                        currentMood.onPlayerJoin(player);
                        if (temporaryHudScoreboard != null && hudHideTask != null && !hudHideTask.isCancelled()) {
                            if (!playerOriginalScoreboards.containsKey(player.getUniqueId())) {
                                playerOriginalScoreboards.put(player.getUniqueId(), player.getScoreboard());
                            }
                            player.setScoreboard(temporaryHudScoreboard);
                        }
                    }
                }
            }.runTaskLater(plugin, 10L);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (currentMood != null) {
            currentMood.onPlayerQuit(player);
        }
        ScoreboardManager sm = Bukkit.getScoreboardManager();
        Scoreboard originalBoard = playerOriginalScoreboards.remove(player.getUniqueId());
        if (sm != null && player.getScoreboard().equals(temporaryHudScoreboard)) {
            player.setScoreboard(originalBoard != null ? originalBoard : sm.getMainScoreboard());
        }
    }

    public Mood getCurrentMood() {
        return currentMood;
    }

    public Map<String, Mood> getAvailableMoods() {
        return Collections.unmodifiableMap(availableMoods);
    }

    public Collection<Mood> getEnabledMoods() {
        return availableMoods.values().stream().filter(Mood::isEnabled).collect(Collectors.toList());
    }

    /** Trims to {@code max} chars without leaving a dangling section sign that would corrupt the line. */
    private String trimToLength(String text, int max) {
        if (text == null) return "";
        if (text.length() <= max) return text;
        String trimmed = text.substring(0, max);
        if (trimmed.endsWith("§")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        return trimmed;
    }

    private String formatTime(long totalSeconds) {
        if (totalSeconds < 0) totalSeconds = 0;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
}