package com.rex.worldMood.moods;

import com.rex.worldMood.WorldMood;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExpEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.SoundCategory;

import java.util.*;
import java.util.stream.Collectors;

public class LuckyDay extends Mood implements Listener {

    private double miningTreasureChance;
    private double mobKillTreasureChance;
    private List<Material> validTreasureItems = new ArrayList<>();
    private final Random random = new Random();

    private static final int LUCK_DURATION_TICKS = 5 * 60 * 20;
    private static final int LUCK_AMPLIFIER = 0;
    private static final double TRADE_DISCOUNT_MULTIPLIER = 0.5;
    private static final double XP_MULTIPLIER = 2.0;

    public LuckyDay(WorldMood plugin) {
        super(plugin, "lucky_day");
        loadConfigValues();
    }

    @Override
    protected void loadConfigValues() {
        super.loadConfigValues();
        ConfigurationSection moodConfig = getMoodConfigSection();
        validTreasureItems.clear();

        if (moodConfig != null) {
            miningTreasureChance = moodConfig.getDouble("miningTreasureChance", 0.01);
            mobKillTreasureChance = moodConfig.getDouble("mobKillTreasureChance", 0.02);

            List<String> itemNames = moodConfig.getStringList("treasureItems");
            if (itemNames != null) {
                validTreasureItems = itemNames.stream()
                        .map(name -> {
                            if (name == null || name.trim().isEmpty()) return null;
                            try {
                                Material mat = Material.matchMaterial(name.trim().toUpperCase());
                                if (mat == null || !mat.isItem()) {
                                    plugin.getLogger().warning("[LuckyDay] Invalid or non-item material in config: " + name);
                                    return null;
                                }
                                return mat;
                            } catch (IllegalArgumentException e) {
                                plugin.getLogger().warning("[LuckyDay] Error parsing material in config: " + name);
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

                if (validTreasureItems.isEmpty() && !itemNames.isEmpty()) {
                    plugin.getLogger().warning("[LuckyDay] All configured treasure items were invalid or list was empty.");
                }
            }
        } else {
            miningTreasureChance = 0.01;
            mobKillTreasureChance = 0.02;
            plugin.getLogger().warning("[LuckyDay] Configuration section missing. Using default chances.");
        }

        if (validTreasureItems.isEmpty()) {
            plugin.getLogger().info("[LuckyDay] No valid treasure items configured or loaded. Using defaults (Diamond, Emerald, Gold Ingot).");
            validTreasureItems.addAll(List.of(Material.DIAMOND, Material.EMERALD, Material.GOLD_INGOT));
        } else {
            plugin.getLogger().info("[LuckyDay] Loaded " + validTreasureItems.size() + " treasure items: " +
                    validTreasureItems.stream().map(Enum::name).collect(Collectors.joining(", ")));
        }
    }

    @Override
    public String getName() {
        return "Lucky Day";
    }

    @Override
    public String getDescription() {
        return "A wave of extraordinary fortune washes over the land! Keep an eye out for unexpected treasures, trade wisely, and feel invigorated.";
    }

    @Override
    public List<String> getEffects() {
        return Arrays.asList(
                "Temporary Luck Potion (" + (LUCK_DURATION_TICKS / 20 / 60) + " mins)",
                String.format("%.0f%% Cheaper Villager Trades", (1.0 - TRADE_DISCOUNT_MULTIPLIER) * 100),
                String.format("x%.1f XP Gain", XP_MULTIPLIER),
                String.format("%.2f%% Treasure Chance (Mining)", miningTreasureChance * 100),
                String.format("%.2f%% Treasure Chance (Mob Kills)", mobKillTreasureChance * 100),
                ChatColor.GRAY + "(Mood lasts until Night/Day)"
        );
    }

    @Override
    public void apply() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        PotionEffect luckEffect = new PotionEffect(
                PotionEffectType.LUCK,
                LUCK_DURATION_TICKS,
                LUCK_AMPLIFIER,
                true,
                false,
                true
        );

        for(Player p : plugin.getServer().getOnlinePlayers()){
            p.removePotionEffect(PotionEffectType.LUCK);
            p.addPotionEffect(luckEffect);

            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0f, 1.5f);
            p.sendTitle(ChatColor.GOLD + "" + ChatColor.BOLD + "Lucky Day!", ChatColor.YELLOW + "You feel extraordinarily fortunate...", 10, 70, 20);
            p.sendMessage(ChatColor.GOLD + "A wave of good fortune washes over you for the next 5 minutes!");
        }
    }

    @Override
    public void remove() {
        HandlerList.unregisterAll(this);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPotionEffect(PotionEffectType.LUCK)) {
                PotionEffect currentLuck = player.getPotionEffect(PotionEffectType.LUCK);
                if (currentLuck != null && currentLuck.getAmplifier() == LUCK_AMPLIFIER && currentLuck.getDuration() <= LUCK_DURATION_TICKS + 20) {
                    player.removePotionEffect(PotionEffectType.LUCK);
                }
            }
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory() != null &&
                    player.getOpenInventory().getTopInventory().getHolder() instanceof Merchant) {
                Merchant merchant = (Merchant) player.getOpenInventory().getTopInventory().getHolder();
                resetTrades(merchant, player.getOpenInventory().getTitle());
            }
        }
        plugin.getLogger().info("[LuckyDay] Lucky Day ended. Effects (Luck, Trade Discounts) removed or reverted.");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;

        Block block = event.getBlock();
        if (event.getExpToDrop() > 0 || !block.getDrops(player.getInventory().getItemInMainHand()).isEmpty()) {
            if (!validTreasureItems.isEmpty() && random.nextDouble() < miningTreasureChance) {
                dropTreasure(block);
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null) return;

        if (!validTreasureItems.isEmpty() && random.nextDouble() < mobKillTreasureChance) {
            dropTreasure(entity);
        }
    }

    // XP Event Handlers
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

    // Villager Trade Discount Handlers
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager)) return;
        if (event.getHand() != EquipmentSlot.HAND && event.getHand() != EquipmentSlot.OFF_HAND) return;

        Villager villager = (Villager) event.getRightClicked();
        List<MerchantRecipe> currentRecipes = villager.getRecipes();
        List<MerchantRecipe> newRecipes = new ArrayList<>();
        boolean updated = false;

        for (MerchantRecipe recipe : currentRecipes) {
            MerchantRecipe newRecipe = new MerchantRecipe(recipe.getResult(), recipe.getUses(), recipe.getMaxUses(), recipe.hasExperienceReward(), recipe.getVillagerExperience(), recipe.getPriceMultiplier());
            newRecipe.setIngredients(recipe.getIngredients());

            if (!recipe.getIngredients().isEmpty()) {
                ItemStack firstIngredient = recipe.getIngredients().get(0);
                int originalPrice = firstIngredient.getAmount();
                int discountedPrice = (int) Math.max(1, Math.round(originalPrice * TRADE_DISCOUNT_MULTIPLIER));

                int specialPriceAdjustment = discountedPrice - originalPrice;

                if (recipe.getSpecialPrice() != specialPriceAdjustment) {
                    newRecipe.setSpecialPrice(specialPriceAdjustment);
                    updated = true;
                } else {
                    newRecipe.setSpecialPrice(recipe.getSpecialPrice());
                }
            }
            newRecipes.add(newRecipe);
        }

        if (updated) {
            villager.setRecipes(newRecipes);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof Merchant) {
            Merchant merchant = (Merchant) event.getInventory().getHolder();
            resetTrades(merchant, event.getView().getTitle());
        }
    }

    private void resetTrades(Merchant merchant, String inventoryTitle) {
        if (merchant instanceof Villager) {
            Villager villager = (Villager) merchant;
            List<MerchantRecipe> currentRecipes = villager.getRecipes();
            List<MerchantRecipe> newRecipes = new ArrayList<>();
            boolean updated = false;

            for (MerchantRecipe recipe : currentRecipes) {
                MerchantRecipe newRecipe = new MerchantRecipe(recipe.getResult(), recipe.getUses(), recipe.getMaxUses(), recipe.hasExperienceReward(), recipe.getVillagerExperience(), recipe.getPriceMultiplier());
                newRecipe.setIngredients(recipe.getIngredients());

                if (recipe.getSpecialPrice() != 0) {
                    newRecipe.setSpecialPrice(0);
                    updated = true;
                } else {
                    newRecipe.setSpecialPrice(recipe.getSpecialPrice());
                }
                newRecipes.add(newRecipe);
            }

            if (updated) {
                villager.setRecipes(newRecipes);
            }
        }
    }

    private void dropTreasure(Block block) {
        if (validTreasureItems.isEmpty()) return;
        Material treasureMaterial = validTreasureItems.get(random.nextInt(validTreasureItems.size()));
        block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), new ItemStack(treasureMaterial, 1));
        playTreasureEffects(block.getLocation().add(0.5, 0.5, 0.5), treasureMaterial.name());
    }

    private void dropTreasure(LivingEntity entity) {
        if (validTreasureItems.isEmpty()) return;
        Material treasureMaterial = validTreasureItems.get(random.nextInt(validTreasureItems.size()));
        entity.getWorld().dropItemNaturally(entity.getEyeLocation(), new ItemStack(treasureMaterial, 1));
        playTreasureEffects(entity.getEyeLocation(), treasureMaterial.name());
    }

    private void playTreasureEffects(Location location, String itemName) {
        World world = location.getWorld();
        if (world == null) return;

        world.spawnParticle(Particle.TOTEM_OF_UNDYING, location, 20, 0.5, 0.5, 0.5, 0.15);
        world.spawnParticle(Particle.FIREWORK, location, 25, 0.6, 0.6, 0.6, 0.08);
        world.playSound(location, Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.NEUTRAL, 1.0f, 1.8f + random.nextFloat() * 0.2f);
        world.playSound(location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 1.0f, 1.5f + random.nextFloat() * 0.3f);
    }

    @Override
    public void onPlayerJoin(Player player) {
        if (plugin.getMoodManager().getCurrentMood() == this) {
            PotionEffect luckEffect = new PotionEffect(
                    PotionEffectType.LUCK,
                    LUCK_DURATION_TICKS,
                    LUCK_AMPLIFIER,
                    true, true, true);
            player.removePotionEffect(PotionEffectType.LUCK);
            player.addPotionEffect(luckEffect);
            player.sendMessage(ChatColor.GOLD + "You've joined during a Lucky Day! You feel fortunate for the next 5 minutes.");
        }
    }

    @Override
    public void onPlayerQuit(Player player) {
    }
}