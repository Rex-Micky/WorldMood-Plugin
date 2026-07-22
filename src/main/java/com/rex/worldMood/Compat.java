package com.rex.worldMood;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.attribute.Attributable;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Runtime compatibility layer for the Bukkit API across 1.16.5 - 1.21.x.
 * <p>
 * Minecraft 1.20.5 renamed a large number of {@link Particle} and {@link PotionEffectType}
 * constants, and several others simply did not exist on older servers. Referencing any of them
 * directly compiles fine and then dies with {@code NoSuchFieldError} the first time the code path
 * runs on a server of the wrong age — the worst kind of failure, because the plugin loads happily
 * and only breaks once a player triggers it.
 * <p>
 * Everything version-sensitive is therefore resolved <b>by name, once, at class-load</b>. That has
 * two useful properties:
 * <ul>
 *   <li>The same source compiles against both the 1.16.5 and the 1.20.1 API, because it never
 *       names a constant that only exists in one of them.</li>
 *   <li>Anything genuinely unavailable resolves to {@code null} instead of throwing, so a mood can
 *       degrade gracefully rather than take the server down.</li>
 * </ul>
 * <b>Every field here may be null.</b> Use the {@code play*} / {@code spawn*} helpers, which
 * no-op on null, rather than dereferencing the constants directly.
 */
public final class Compat {

    private static final Logger LOG = Logger.getLogger("WorldMood");

    private Compat() {
    }

    // ------------------------------------------------------------------
    // Particles. First candidate is the modern name, then the pre-1.20.5
    // name, then any acceptable visual stand-in for older servers.
    // ------------------------------------------------------------------
    public static final Particle DUST = particle("DUST", "REDSTONE");
    public static final Particle SMOKE = particle("SMOKE", "SMOKE_NORMAL");
    public static final Particle HAPPY_VILLAGER = particle("HAPPY_VILLAGER", "VILLAGER_HAPPY");
    public static final Particle FIREWORK = particle("FIREWORK", "FIREWORKS_SPARK");
    public static final Particle TOTEM_OF_UNDYING = particle("TOTEM_OF_UNDYING", "TOTEM");
    public static final Particle ENCHANTED_HIT = particle("ENCHANTED_HIT", "CRIT_MAGIC");
    public static final Particle EXPLOSION = particle("EXPLOSION", "EXPLOSION_NORMAL");

    /** 1.21.2+; {@code BLOCK_DUST} is the older name and takes the same BlockData payload. */
    public static final Particle BLOCK_CRUMBLE = particle("BLOCK_CRUMBLE", "BLOCK_DUST");
    /** 1.19+. Null on older servers - Void Tension falls back to other effects. */
    public static final Particle SONIC_BOOM = particle("SONIC_BOOM");
    /** 1.17+. Null on 1.16. */
    public static final Particle SPORE_BLOSSOM_AIR = particle("SPORE_BLOSSOM_AIR", "CLOUD");
    /** 1.17+. Null on 1.16 - Shadow Veil falls back to ASH. */
    public static final Particle DUST_COLOR_TRANSITION = particle("DUST_COLOR_TRANSITION");

    // Stable across the whole range, routed through here for uniformity.
    public static final Particle CRIT = particle("CRIT");
    public static final Particle LAVA = particle("LAVA");
    public static final Particle FLAME = particle("FLAME");
    public static final Particle CLOUD = particle("CLOUD");
    public static final Particle PORTAL = particle("PORTAL");
    public static final Particle REVERSE_PORTAL = particle("REVERSE_PORTAL", "PORTAL");
    public static final Particle SQUID_INK = particle("SQUID_INK", "SMOKE_NORMAL");
    public static final Particle DAMAGE_INDICATOR = particle("DAMAGE_INDICATOR");
    public static final Particle ASH = particle("ASH", "SMOKE_NORMAL");
    public static final Particle COMPOSTER = particle("COMPOSTER", "VILLAGER_HAPPY");
    public static final Particle LARGE_SMOKE = particle("LARGE_SMOKE", "SMOKE_LARGE");
    public static final Particle WITCH = particle("WITCH", "SPELL_WITCH");
    public static final Particle DRAGON_BREATH = particle("DRAGON_BREATH");
    public static final Particle SOUL = particle("SOUL", "SMOKE_NORMAL");

    // ------------------------------------------------------------------
    // Potion effects. Renamed wholesale in 1.20.5.
    // ------------------------------------------------------------------
    public static final PotionEffectType SLOWNESS = effect("SLOWNESS", "SLOW");
    public static final PotionEffectType HASTE = effect("HASTE", "FAST_DIGGING");
    public static final PotionEffectType MINING_FATIGUE = effect("MINING_FATIGUE", "SLOW_DIGGING");
    public static final PotionEffectType JUMP_BOOST = effect("JUMP_BOOST", "JUMP");
    public static final PotionEffectType NAUSEA = effect("NAUSEA", "CONFUSION");
    /** 1.19+. Null on older servers; Shadow Veil substitutes short blindness. */
    public static final PotionEffectType DARKNESS = effect("DARKNESS");

    // Stable names, routed through here so no mood touches the enum directly.
    public static final PotionEffectType SPEED = effect("SPEED");
    public static final PotionEffectType REGENERATION = effect("REGENERATION");
    public static final PotionEffectType FIRE_RESISTANCE = effect("FIRE_RESISTANCE");
    public static final PotionEffectType LUCK = effect("LUCK");
    public static final PotionEffectType BLINDNESS = effect("BLINDNESS");
    public static final PotionEffectType INVISIBILITY = effect("INVISIBILITY");
    public static final PotionEffectType LEVITATION = effect("LEVITATION");
    public static final PotionEffectType SLOW_FALLING = effect("SLOW_FALLING");
    public static final PotionEffectType WEAKNESS = effect("WEAKNESS");
    public static final PotionEffectType WITHER = effect("WITHER");

    // ------------------------------------------------------------------
    // Sounds that postdate 1.16.5.
    // ------------------------------------------------------------------
    public static final Sound AMETHYST_CHIME = sound("BLOCK_AMETHYST_BLOCK_CHIME", "BLOCK_NOTE_BLOCK_CHIME");
    public static final Sound DEEPSLATE_BREAK = sound("BLOCK_DEEPSLATE_BREAK", "BLOCK_STONE_BREAK");
    public static final Sound DEEPSLATE_PLACE = sound("BLOCK_DEEPSLATE_PLACE", "BLOCK_STONE_PLACE");
    public static final Sound SCULK_SENSOR_CLICKING = sound("BLOCK_SCULK_SENSOR_CLICKING", "BLOCK_STONE_BUTTON_CLICK_ON");
    public static final Sound SCULK_SHRIEK = sound("BLOCK_SCULK_SHRIEKER_SHRIEK", "BLOCK_SCULK_SENSOR_CLICKING", "ENTITY_ELDER_GUARDIAN_CURSE");
    public static final Sound WARDEN_AMBIENT = sound("ENTITY_WARDEN_AMBIENT", "AMBIENT_CAVE");
    // ENTITY_WOLF_HOWL exists in the 1.20.1 API this jar compiles against, but was removed/renamed
    // by 1.21.11 — a forward-incompatibility that threw NoSuchFieldError mid-event. Resolve by name.
    public static final Sound WOLF_HOWL = sound("ENTITY_WOLF_HOWL", "ENTITY_WOLF_GROWL", "ENTITY_WOLF_AMBIENT");

    // ------------------------------------------------------------------
    // Attributes (renamed in 1.21.3, when Attribute also stopped being an
    // enum) and enchantments (renamed in 1.20.5).
    // ------------------------------------------------------------------
    public static final Attribute MAX_HEALTH = constant(Attribute.class, "MAX_HEALTH", "GENERIC_MAX_HEALTH");
    public static final Attribute ATTACK_DAMAGE = constant(Attribute.class, "ATTACK_DAMAGE", "GENERIC_ATTACK_DAMAGE");
    public static final Attribute MOVEMENT_SPEED = constant(Attribute.class, "MOVEMENT_SPEED", "GENERIC_MOVEMENT_SPEED");
    public static final Enchantment FIRE_PROTECTION = constant(Enchantment.class, "FIRE_PROTECTION", "PROTECTION_FIRE");

    /**
     * Reads a public static constant by name, trying each candidate in turn.
     * <p>
     * Deliberately reflective rather than {@code valueOf}: {@code Attribute} was an enum through
     * 1.21.2 and became a registry interface afterwards, so only field access works on both.
     */
    private static <T> T constant(Class<T> type, String... names) {
        for (String name : names) {
            try {
                Object value = type.getField(name).get(null);
                if (type.isInstance(value)) {
                    return type.cast(value);
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Not on this server version - try the next candidate.
            }
        }
        LOG.warning("[Compat] No " + type.getSimpleName() + " available for " + names[0] + ".");
        return null;
    }

    /** Null-safe attribute lookup; returns null if the attribute is unavailable on this version. */
    public static AttributeInstance attribute(Attributable holder, Attribute attribute) {
        return (holder == null || attribute == null) ? null : holder.getAttribute(attribute);
    }

    /** Enchantment level that yields 0 rather than throwing when the enchantment is unavailable. */
    public static int enchantLevel(ItemStack item, Enchantment enchantment) {
        return (item == null || enchantment == null) ? 0 : item.getEnchantmentLevel(enchantment);
    }

    // ------------------------------------------------------------------
    // Resolution
    // ------------------------------------------------------------------

    private static Particle particle(String... candidates) {
        for (String name : candidates) {
            try {
                return Particle.valueOf(name);
            } catch (IllegalArgumentException ignored) {
                // Not on this server version - try the next candidate.
            }
        }
        LOG.warning("[Compat] No particle available for " + candidates[0] + "; that effect is disabled.");
        return null;
    }

    @SuppressWarnings("deprecation")
    private static PotionEffectType effect(String... candidates) {
        for (String name : candidates) {
            PotionEffectType type = PotionEffectType.getByName(name);
            if (type != null) {
                return type;
            }
        }
        LOG.warning("[Compat] No potion effect available for " + candidates[0] + "; that effect is disabled.");
        return null;
    }

    private static Sound sound(String... candidates) {
        for (String name : candidates) {
            try {
                return Sound.valueOf(name);
            } catch (IllegalArgumentException ignored) {
                // Not on this server version - try the next candidate.
            }
        }
        LOG.warning("[Compat] No sound available for " + candidates[0] + "; it will be silent.");
        return null;
    }

    // ------------------------------------------------------------------
    // Null-safe helpers. Prefer these over touching the constants directly.
    // ------------------------------------------------------------------

    public static void playSound(World world, Location at, Sound sound, SoundCategory category, float volume, float pitch) {
        if (world != null && at != null && sound != null) {
            world.playSound(at, sound, category, volume, pitch);
        }
    }

    public static void playSound(Player player, Sound sound, SoundCategory category, float volume, float pitch) {
        if (player != null && sound != null) {
            player.playSound(player.getLocation(), sound, category, volume, pitch);
        }
    }

    public static void spawn(World world, Particle particle, Location at, int count,
                             double dx, double dy, double dz, double extra) {
        if (world != null && particle != null && at != null) {
            world.spawnParticle(particle, at, count, dx, dy, dz, extra);
        }
    }

    public static <T> void spawn(World world, Particle particle, Location at, int count,
                                 double dx, double dy, double dz, double extra, T data) {
        if (world != null && particle != null && at != null) {
            world.spawnParticle(particle, at, count, dx, dy, dz, extra, data);
        }
    }

    // ------------------------------------------------------------------
    // Things whose SHAPE, not just name, changed between versions.
    // ------------------------------------------------------------------

    private static final Constructor<?> DUST_TRANSITION_CTOR = findDustTransitionConstructor();

    private static Constructor<?> findDustTransitionConstructor() {
        try {
            Class<?> cls = Class.forName("org.bukkit.Particle$DustTransition");
            return cls.getConstructor(Color.class, Color.class, float.class);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            return null; // 1.16 and earlier - callers fall back to a plain particle.
        }
    }

    /**
     * Builds a {@code Particle.DustTransition} payload, or null on servers predating 1.17.
     * Returned as {@link Object} so the shared source never names a class the old API lacks.
     */
    public static Object dustTransition(Color from, Color to, float size) {
        if (DUST_TRANSITION_CTOR == null) {
            return null;
        }
        try {
            return DUST_TRANSITION_CTOR.newInstance(from, to, size);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /**
     * Registers a sidebar objective across API generations.
     * <p>
     * 1.20.1 added {@code registerNewObjective(String, Criteria, String)} and deprecated the
     * older all-String form. Naming {@code Criteria} directly would break the 1.16.5 build, so
     * the modern overload is invoked reflectively with the legacy one as a fallback.
     */
    @SuppressWarnings("deprecation")
    public static Objective registerObjective(Scoreboard scoreboard, String name, String displayName) {
        try {
            Class<?> criteriaClass = Class.forName("org.bukkit.scoreboard.Criteria");
            Object dummy = criteriaClass.getField("DUMMY").get(null);
            Method register = Scoreboard.class.getMethod("registerNewObjective", String.class, criteriaClass, String.class);
            return (Objective) register.invoke(scoreboard, name, dummy, displayName);
        } catch (ReflectiveOperationException e) {
            // Pre-1.20.1: only the all-String form exists.
            return scoreboard.registerNewObjective(name, "dummy", displayName);
        }
    }

    // MerchantRecipe special pricing arrived in 1.18, so Lucky Day's villager discount is
    // reflective: it stays fully functional on 1.18+ and simply does nothing on older servers.
    private static final Method GET_SPECIAL_PRICE = method(MerchantRecipe.class, "getSpecialPrice");
    private static final Method SET_SPECIAL_PRICE = method(MerchantRecipe.class, "setSpecialPrice", int.class);

    private static Method method(Class<?> owner, String name, Class<?>... params) {
        try {
            return owner.getMethod(name, params);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /** Whether this server can apply villager trade discounts at all (1.18+). */
    public static boolean supportsTradeDiscounts() {
        return GET_SPECIAL_PRICE != null && SET_SPECIAL_PRICE != null;
    }

    public static int getSpecialPrice(MerchantRecipe recipe) {
        if (GET_SPECIAL_PRICE == null || recipe == null) return 0;
        try {
            return ((Number) GET_SPECIAL_PRICE.invoke(recipe)).intValue();
        } catch (ReflectiveOperationException e) {
            return 0;
        }
    }

    public static void setSpecialPrice(MerchantRecipe recipe, int price) {
        if (SET_SPECIAL_PRICE == null || recipe == null) return;
        try {
            SET_SPECIAL_PRICE.invoke(recipe, price);
        } catch (ReflectiveOperationException ignored) {
            // Older server - the discount simply is not applied.
        }
    }

    /** Hard ceiling Mojang enforces on {@link WorldBorder#setSize(double)}. */
    private static final double FALLBACK_MAX_BORDER = 5.9999968E7D;

    /**
     * The largest legal world border size. Anything above it throws IllegalArgumentException,
     * which is how Blood Moon used to break. {@code getMaxSize()} is not on the oldest APIs.
     */
    public static double maxBorderSize(WorldBorder border) {
        try {
            Method getMaxSize = WorldBorder.class.getMethod("getMaxSize");
            return ((Number) getMaxSize.invoke(border)).doubleValue();
        } catch (ReflectiveOperationException e) {
            return FALLBACK_MAX_BORDER;
        }
    }

    /** Logs once at startup so a server owner can see what their version does and does not support. */
    public static void logSupportSummary() {
        StringBuilder missing = new StringBuilder();
        if (DARKNESS == null) missing.append(" Darkness-effect");
        if (SONIC_BOOM == null) missing.append(" Sonic-boom-particle");
        if (DUST_TRANSITION_CTOR == null) missing.append(" Dust-colour-transition");

        LOG.info("[Compat] Running on " + Bukkit.getBukkitVersion() + ".");
        if (missing.length() > 0) {
            LOG.info("[Compat] Not available on this version (visual only, everything still works):"
                    + missing + ".");
        }
    }
}
