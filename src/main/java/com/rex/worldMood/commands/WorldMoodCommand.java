package com.rex.worldMood.commands;

import com.rex.worldMood.WorldMood;
import com.rex.worldMood.moods.Mood;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class WorldMoodCommand implements CommandExecutor, TabCompleter {

    private final WorldMood plugin;
    private static final String NO_PERM = ChatColor.RED + "You do not have permission to use this command.";
    private static final String PREFIX = ChatColor.DARK_AQUA + "[WorldMood] " + ChatColor.AQUA;
    private static final List<String> SUBCOMMANDS = Arrays.asList("reload", "start", "skip", "list", "stop", "info");

    public WorldMoodCommand(WorldMood plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("worldmood.admin")) {
            sender.sendMessage(NO_PERM);
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload":
                if (args.length > 1) {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Usage: /" + label + " reload");
                    return true;
                }
                plugin.reloadPluginConfig();
                sender.sendMessage(PREFIX + "Configuration reloaded successfully!");
                break;

            case "start":
                if (args.length != 2) {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Usage: /" + label + " start <mood_key>");
                    sender.sendMessage(PREFIX + ChatColor.GRAY + "Example: /" + label + " start calm_skies");
                    List<String> enabledKeysForStart = plugin.getMoodManager().getEnabledMoods()
                            .stream().map(Mood::getConfigKey).sorted().collect(Collectors.toList());
                    if (enabledKeysForStart.isEmpty()) {
                        sender.sendMessage(PREFIX + ChatColor.YELLOW + "No moods are currently enabled to be started.");
                    } else {
                        sender.sendMessage(PREFIX + ChatColor.GRAY + "Enabled keys: " + String.join(", ", enabledKeysForStart));
                    }
                    return true;
                }
                String moodKeyToStart = args[1].toLowerCase();
                if (plugin.getMoodManager().startSpecificMood(moodKeyToStart)) {
                    sender.sendMessage(PREFIX + "Attempting to start mood: " + ChatColor.WHITE + moodKeyToStart + ChatColor.AQUA + "...");
                } else {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Failed to start mood '" + moodKeyToStart +
                            "'. It might be disabled, invalid, another mood is already active, or time requirements not met. Check console/config.");
                }
                break;

            case "skip":
            case "stop":
                if (args.length > 1) {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Usage: /" + label + " " + subCommand);
                    return true;
                }
                Mood currentMoodToStop = plugin.getMoodManager().getCurrentMood();
                if (currentMoodToStop != null) {
                    sender.sendMessage(PREFIX + "Stopping current mood: " + ChatColor.WHITE + currentMoodToStop.getName() + ChatColor.AQUA + "...");
                    plugin.getMoodManager().stopCurrentMood();
                    if (subCommand.equals("skip") && plugin.getConfig().getBoolean("randomizeMoods", true)) {
                        sender.sendMessage(PREFIX + "Attempting to trigger next random mood from cycle...");
                        plugin.getMoodManager().startRandomMood();
                    }
                } else {
                    sender.sendMessage(PREFIX + ChatColor.YELLOW + "No mood is currently active to " + subCommand + ".");
                }
                break;

            case "info":
            case "list":
                if (args.length > 1) {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Usage: /" + label + " " + subCommand);
                    return true;
                }
                sender.sendMessage(ChatColor.DARK_AQUA + "--- WorldMood Status & Moods ---");
                Mood activeMood = plugin.getMoodManager().getCurrentMood();
                if (activeMood != null) {
                    sender.sendMessage(ChatColor.GREEN + "Active Mood: " + ChatColor.WHITE + activeMood.getName() +
                            ChatColor.GRAY + " (" + activeMood.getConfigKey() + ")");
                    // + (timeRemaining.isEmpty() ? "" : ChatColor.GREEN + " - Time Left: " + timeRemaining));
                    sender.sendMessage(ChatColor.GRAY + "  Description: " + ChatColor.ITALIC + activeMood.getDescription());
                    List<String> effects = activeMood.getEffects();
                    if (!effects.isEmpty()) {
                        sender.sendMessage(ChatColor.GRAY + "  Effects:");
                        for (String effect : effects) {
                            sender.sendMessage(ChatColor.GRAY + "    - " + ChatColor.WHITE + effect);
                        }
                    }
                } else {
                    sender.sendMessage(ChatColor.YELLOW + "Active Mood: None");
                }

                sender.sendMessage(PREFIX + ChatColor.AQUA + "Available & Enabled Moods:");
                List<Mood> enabledMoods = plugin.getMoodManager().getEnabledMoods()
                        .stream()
                        .sorted(Comparator.comparing(Mood::getName)) // Sort by name
                        .collect(Collectors.toList());
                if (enabledMoods.isEmpty()) {
                    sender.sendMessage(ChatColor.GRAY + "  (No moods are enabled in the configuration)");
                } else {
                    for (Mood mood : enabledMoods) {
                        String rarityTag = plugin.getConfig().getString("moods." + mood.getConfigKey() + ".rarityTag", "");
                        rarityTag = rarityTag.isEmpty() ? "" : ChatColor.GOLD + " [" + rarityTag + "]";
                        sender.sendMessage(ChatColor.WHITE + "- " + mood.getName() +
                                ChatColor.GRAY + " (key: " + mood.getConfigKey() +
                                ", weight: " + mood.getWeight() +
                                ", base_duration: " + mood.getDuration() + "s)" + rarityTag);
                    }
                }

                sender.sendMessage(PREFIX + ChatColor.AQUA + "Configured but Disabled Moods:");
                ConfigurationSection moodsSection = plugin.getConfig().getConfigurationSection("moods");
                List<String> disabledMoodMessages = new ArrayList<>();
                if (moodsSection != null) {
                    List<String> enabledKeysList = enabledMoods.stream().map(Mood::getConfigKey).collect(Collectors.toList());
                    List<String> allConfiguredMoodKeys = new ArrayList<>(moodsSection.getKeys(false));
                    allConfiguredMoodKeys.sort(String.CASE_INSENSITIVE_ORDER);

                    for (String key : allConfiguredMoodKeys) {
                        if (!enabledKeysList.contains(key.toLowerCase())) {
                            String moodName = moodsSection.getString(key + ".displayName", key);
                            disabledMoodMessages.add(ChatColor.DARK_GRAY + "- " + moodName + " (" + key + ")");
                        }
                    }
                }
                if (disabledMoodMessages.isEmpty()) {
                    sender.sendMessage(ChatColor.GRAY + "  (All configured moods are enabled, or no other moods are configured as disabled)");
                } else {
                    disabledMoodMessages.forEach(sender::sendMessage);
                }
                break;

            default:
                sender.sendMessage(PREFIX + ChatColor.RED + "Unknown subcommand: " + args[0]);
                sendHelp(sender, label);
                break;
        }
        return true;
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.DARK_AQUA + "--- WorldMood Plugin Help ---");
        sender.sendMessage(ChatColor.AQUA + "/" + label + " reload" + ChatColor.GRAY + " - Reloads the plugin's configuration.");
        sender.sendMessage(ChatColor.AQUA + "/" + label + " start <mood_key>" + ChatColor.GRAY + " - Force starts a specific mood.");
        sender.sendMessage(ChatColor.AQUA + "/" + label + " skip" + ChatColor.GRAY + " - Stops the current mood & tries to trigger a new random one.");
        sender.sendMessage(ChatColor.AQUA + "/" + label + " stop" + ChatColor.GRAY + " - Stops the current mood entirely (no new mood triggered).");
        sender.sendMessage(ChatColor.AQUA + "/" + label + " list" + ChatColor.GRAY + " - Lists current status and all configured moods.");
        sender.sendMessage(ChatColor.AQUA + "/" + label + " info" + ChatColor.GRAY + " - Alias for 'list'.");
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("worldmood.admin")) {
            return Collections.emptyList();
        }

        final List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], SUBCOMMANDS, completions);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
            // For "start", suggest keys of moods that are currently enabled
            List<String> moodKeys = plugin.getMoodManager().getEnabledMoods()
                    .stream()
                    .map(Mood::getConfigKey)
                    .sorted()
                    .collect(Collectors.toList());
            StringUtil.copyPartialMatches(args[1], moodKeys, completions);
        }

        Collections.sort(completions);
        return completions;
    }
}