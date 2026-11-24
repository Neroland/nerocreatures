package za.co.neroland.nerocreatures.entity.alpha;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.entity.EntityTypeTest;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.compat.CreatureAttributeModifier;
import za.co.neroland.nerocreatures.entity.base.NeroCreatureEntity;
import za.co.neroland.nerocreatures.entity.boss.NeroBossEntity;

/**
 * Alphas: any hostile NeroCreature may spawn as a rare, larger alpha with one rolled modifier and
 * one guaranteed rare drop, capped per dimension. All numbers are data
 * ({@link AlphaData}); this class applies them.
 *
 * <ul>
 *   <li><b>Eligible:</b> implements {@link Enemy}, is not a {@link NeroBossEntity}, and is not in
 *       {@code #nerocreatures:alpha_excluded} (elites go there).</li>
 *   <li><b>When:</b> only {@code NATURAL} and {@code CHUNK_GENERATION} spawns roll. Spawn eggs,
 *       events, summons and structures never do.</li>
 *   <li><b>Cap:</b> the loaded alphas in the dimension are counted (a bounded query, run only after
 *       a roll succeeds, so about once per fifty hostile spawns) and must be under the cap.</li>
 *   <li><b>Rendering:</b> {@link NeroCreatureEntity#isAlpha()} is synced; size comes from the vanilla
 *       {@code minecraft:scale} attribute, so the model needs no change. The crimson
 *       accent, 85% body brightness and ember tell are renderer work keyed on the flag.</li>
 *   <li><b>Drop:</b> one weighted pick from the species' drop file when killed by a player.</li>
 * </ul>
 *
 * <p>Server-authoritative; no player data is read or stored.
 */
public final class AlphaSystem {

    /** Entity types that never become alphas (elites, and anything a pack adds). */
    public static final TagKey<EntityType<?>> EXCLUDED = TagKey.create(Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "alpha_excluded"));

    private static final String MODIFIER_PREFIX = "alpha/";

    private static volatile BooleanSupplier enabled = () -> true;
    private static volatile DoubleSupplier chanceMultiplier = () -> 1.0D;
    private static volatile IntSupplier capOverride = () -> -1;

    private AlphaSystem() {
    }

    /**
     * Wires config values in (called once from common init by whoever owns the config).
     *
     * @param isEnabled       master switch
     * @param chanceScale     multiplies the data chance (1.0 = as authored)
     * @param maxPerDimension overrides the data cap when 0 or more; negative keeps the data value
     */
    public static void configure(BooleanSupplier isEnabled, DoubleSupplier chanceScale, IntSupplier maxPerDimension) {
        enabled = isEnabled;
        chanceMultiplier = chanceScale;
        capOverride = maxPerDimension;
    }

    /** Whether {@code creature} may ever be an alpha. */
    public static boolean eligible(NeroCreatureEntity creature) {
        return creature instanceof Enemy && !(creature instanceof NeroBossEntity)
                && !creature.getType().builtInRegistryHolder().is(EXCLUDED);
    }

    /** Called from {@code NeroCreatureEntity.finalizeSpawn}. Rolls, checks the cap, applies. */
    public static void onFinalizeSpawn(NeroCreatureEntity creature, ServerLevel level, EntitySpawnReason reason) {
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) {
            return;
        }
        if (creature.isAlpha() || !eligible(creature) || !enabled.getAsBoolean()) {
            return;
        }
        AlphaData.Snapshot data = AlphaData.get(level.getServer());
        double chance = data.settings().chance() * Math.max(0.0D, chanceMultiplier.getAsDouble());
        if (!AlphaRules.rolls(chance, creature.getRandom().nextDouble())) {
            return;
        }
        int cap = AlphaRules.effectiveCap(data.settings().maxPerDimension(), capOverride.getAsInt());
        if (!AlphaRules.capAllows(countLoaded(level, cap), cap)) {
            return;
        }
        apply(creature, data, AlphaRules.pickWeighted(data.modifiers(), AlphaModifier::weight,
                creature.getRandom().nextInt(Integer.MAX_VALUE)));
    }

    /**
     * Makes {@code creature} an alpha now, ignoring chance and cap (commands, gallery). Returns false
     * if it is not eligible or already an alpha.
     *
     * @param modifierId a modifier id, or null for a weighted roll
     */
    public static boolean forceAlpha(NeroCreatureEntity creature, ServerLevel level, @Nullable String modifierId) {
        if (creature.isAlpha() || !eligible(creature)) {
            return false;
        }
        AlphaData.Snapshot data = AlphaData.get(level.getServer());
        AlphaModifier chosen = null;
        if (modifierId != null) {
            for (AlphaModifier m : data.modifiers()) {
                if (m.id().equals(modifierId)) {
                    chosen = m;
                }
            }
        }
        if (chosen == null) {
            chosen = AlphaRules.pickWeighted(data.modifiers(), AlphaModifier::weight,
                    creature.getRandom().nextInt(Integer.MAX_VALUE));
        }
        apply(creature, data, chosen);
        return true;
    }

    /** Loaded alphas in {@code level}, counting no further than {@code limit}. */
    public static int countLoaded(ServerLevel level, int limit) {
        if (limit <= 0) {
            return 0;
        }
        List<NeroCreatureEntity> found = new ArrayList<>(limit);
        level.getEntities(EntityTypeTest.forClass(NeroCreatureEntity.class),
                c -> c.isAlpha() && !za.co.neroland.nerocreatures.gallery.GalleryDisplays.isDisplay(c), found, limit); // gallery alphas never count
        return found.size();
    }

    private static void apply(NeroCreatureEntity creature, AlphaData.Snapshot data, @Nullable AlphaModifier modifier) {
        int n = 0;
        addModifier(creature, "minecraft:scale", data.settings().scaleBonus(), "add_multiplied_base", n++);
        for (AlphaModifier.AttributeBonus bonus : data.settings().base()) {
            addModifier(creature, bonus.attribute(), bonus.amount(), bonus.operation(), n++);
        }
        if (modifier != null) {
            for (AlphaModifier.AttributeBonus bonus : modifier.attributes()) {
                addModifier(creature, bonus.attribute(), bonus.amount(), bonus.operation(), n++);
            }
            for (AlphaModifier.EffectBonus effect : modifier.effects()) {
                Holder<MobEffect> holder = effectHolder(effect.effect());
                if (holder != null) {
                    creature.addEffect(new MobEffectInstance(holder, MobEffectInstance.INFINITE_DURATION,
                            effect.amplifier(), true, false));
                }
            }
        }
        creature.setAlphaState(true, modifier != null ? modifier.id() : "");
        creature.setHealth(creature.getMaxHealth());
    }

    private static void addModifier(NeroCreatureEntity creature, String attributeId, double amount, String op, int n) {
        if (amount == 0.0D) {
            return;
        }
        Holder<Attribute> attribute = "minecraft:scale".equals(attributeId) ? Attributes.SCALE : attributeHolder(attributeId);
        if (attribute == null) {
            return;
        }
        AttributeInstance instance = creature.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        Identifier id = Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, MODIFIER_PREFIX + n);
        CreatureAttributeModifier.remove(instance, id);
        instance.addPermanentModifier(CreatureAttributeModifier.create(id, amount, operation(op)));
    }

    private static AttributeModifier.Operation operation(String op) {
        return switch (op.toLowerCase(Locale.ROOT)) {
            case "add_value" -> AttributeModifier.Operation.ADD_VALUE;
            case "add_multiplied_total" -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
            default -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
        };
    }

    @Nullable
    private static Holder<Attribute> attributeHolder(String raw) {
        Identifier id = Identifier.tryParse(raw);
        if (id == null) {
            return null;
        }
        if (!BuiltInRegistries.ATTRIBUTE.containsKey(id)) {
            // Lines before 1.21.2 prefix vanilla attribute ids with "generic.".
            id = Identifier.fromNamespaceAndPath(id.getNamespace(), "generic." + id.getPath());
            if (!BuiltInRegistries.ATTRIBUTE.containsKey(id)) {
                return null;
            }
        }
        return BuiltInRegistries.ATTRIBUTE.wrapAsHolder(BuiltInRegistries.ATTRIBUTE.getValue(id));
    }

    @Nullable
    private static Holder<MobEffect> effectHolder(String raw) {
        Identifier id = Identifier.tryParse(raw);
        if (id == null || !BuiltInRegistries.MOB_EFFECT.containsKey(id)) {
            return null;
        }
        return BuiltInRegistries.MOB_EFFECT.wrapAsHolder(BuiltInRegistries.MOB_EFFECT.getValue(id));
    }

    /**
     * Drops the guaranteed rare drop. Called from {@code NeroCreatureEntity.dropCustomDeathLoot}
     * when the creature is an alpha and was killed by a player.
     */
    public static void dropReward(NeroCreatureEntity creature, ServerLevel level) {
        AlphaData.Snapshot data = AlphaData.get(level.getServer());
        String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(creature.getType()).toString();
        AlphaDrop drop = AlphaRules.pickWeighted(data.dropsFor(entityId), AlphaDrop::weight,
                creature.getRandom().nextInt(Integer.MAX_VALUE));
        if (drop == null) {
            return;
        }
        Identifier itemId = Identifier.tryParse(drop.item());
        if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) {
            return;
        }
        Item item = BuiltInRegistries.ITEM.getValue(itemId);
        creature.spawnAtLocation(level, new ItemStack(item, drop.count()));
    }
}
