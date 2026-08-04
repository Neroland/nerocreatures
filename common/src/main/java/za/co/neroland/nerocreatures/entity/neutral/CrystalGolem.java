package za.co.neroland.nerocreatures.entity.neutral;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.ResetUniversalAngerTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocreatures.config.NeroCreaturesConfig;
import za.co.neroland.nerocreatures.entity.base.CreatureTier;
import za.co.neroland.nerocreatures.entity.base.NeroCreatureEntity;
import za.co.neroland.nerocreatures.registry.ModItems;

/**
 * <b>Crystal Golem</b> — a walking crystal seam that would rather be left alone.
 *
 * <p>It is the roster's first {@link NeutralMob}: it never picks a fight, and it never forgets one.
 * Hit it (or hit one of its neighbours) and it stays angry for {@value #ANGER_MIN_TICKS}–{@value
 * #ANGER_MAX_TICKS_EXCLUSIVE} ticks, chasing the offender the way an iron golem or a polar bear
 * does. Everything about the fight is slow and heavy: high armour, near-total knockback resistance,
 * and a stride that a player can simply walk away from — which is the point. It is an obstacle you
 * choose to mine, not an ambush.
 *
 * <h2>The pickaxe bonus, and why it is code rather than a loot condition</h2>
 *
 * <p>A golem is a resource node with legs: its loot table always yields
 * {@code refined_crystal}, and killing it with a <b>pickaxe-class</b> weapon
 * ({@code #minecraft:pickaxes}) yields extra. That bonus lives in
 * {@link #dropCustomDeathLoot} rather than in the loot table because the entity loot context has no
 * {@code tool} parameter — {@code minecraft:match_tool} works for block loot, not for mobs, so the
 * only honest place to read the killer's weapon is code. The bonus is a <b>bonus roll</b> in the
 * {@code wiki/Drop-Map.md} sense and is therefore the first thing scaled by
 * {@code dropRateMultiplier}; the base table stays untouched so data packs keep full control.
 *
 * <p>Spawns in Core's {@code neroland:space/crystalline_biomes}, rarely and alone.
 *
 * <p><b>Anger is deliberately not persisted.</b> Vanilla's {@code addPersistentAngerSaveData} writes
 * the offending player's UUID into the mob's NBT; NeroCreatures keeps no player identity on an
 * entity (POPIA/GDPR), so a golem that is unloaded and reloaded has calmed down. Anger while loaded
 * behaves exactly like vanilla's.
 */
public class CrystalGolem extends NeroCreatureEntity implements NeutralMob {

    /** Shortest time a provoked golem stays angry, in ticks (20 seconds). */
    private static final int ANGER_MIN_TICKS = 400;

    /** Exclusive upper bound on the anger timer, in ticks (40 seconds). */
    private static final int ANGER_MAX_TICKS_EXCLUSIVE = 800;

    /** Bonus {@code refined_crystal} for a pickaxe kill, before {@code dropRateMultiplier}. */
    private static final int PICKAXE_BONUS_MIN = 1;

    /** Exclusive upper bound of the pickaxe bonus roll. */
    private static final int PICKAXE_BONUS_MAX_EXCLUSIVE = 3;

    /** Game time (ticks) at which anger lapses. Not saved — see the class docs. */
    private long persistentAngerEndTime = NO_ANGER_END_TIME;

    /** Who the golem is angry at, while loaded. Never written to disk (POPIA/GDPR). */
    @Nullable
    private EntityReference<LivingEntity> persistentAngerTarget;

    public CrystalGolem(EntityType<? extends CrystalGolem> type, Level level) {
        super(type, level, CreatureTier.GREATER);
    }

    /**
     * {@link CreatureTier#GREATER} pushed hard towards defence: it hits like an elite but is slower
     * than every other creature in the mod and shrugs off knockback, so the fight is decided by
     * whether you are willing to stand still and trade.
     */
    public static AttributeSupplier.Builder createAttributes() {
        return NeroCreatureEntity.createCreatureAttributes(CreatureTier.GREATER)
                .add(Attributes.MAX_HEALTH, 90.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.17D)
                .add(Attributes.ATTACK_DAMAGE, 9.0D)
                .add(Attributes.ARMOR, 14.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 4.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9D)
                // Short on purpose: a golem notices what is next to it, not what is across the
                // valley. It is neutral, so follow range only matters once it is already angry.
                .add(Attributes.FOLLOW_RANGE, 20.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.6D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        // Retaliation, and only retaliation. There is no unconditional player-targeting goal here:
        // the target goal below fires solely for someone this golem is already angry at, which is
        // what makes it neutral rather than a slow hostile.
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true,
                false, this::isAngryAt));
        this.targetSelector.addGoal(3, new ResetUniversalAngerTargetGoal<>(this, true));
    }

    // --- NeutralMob ---------------------------------------------------------

    @Override
    public long getPersistentAngerEndTime() {
        return this.persistentAngerEndTime;
    }

    @Override
    public void setPersistentAngerEndTime(long endTime) {
        this.persistentAngerEndTime = endTime;
    }

    @Override
    @Nullable
    public EntityReference<LivingEntity> getPersistentAngerTarget() {
        return this.persistentAngerTarget;
    }

    @Override
    public void setPersistentAngerTarget(@Nullable EntityReference<LivingEntity> target) {
        this.persistentAngerTarget = target;
    }

    @Override
    public void startPersistentAngerTimer() {
        this.setTimeToRemainAngry(ANGER_MIN_TICKS
                + this.random.nextInt(ANGER_MAX_TICKS_EXCLUSIVE - ANGER_MIN_TICKS));
    }

    /**
     * Ticks the anger timer down. {@code true} means universal anger (someone attacked one of us) is
     * allowed to spread to nearby golems, which is what makes a crystal field react as a group.
     */
    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        this.updatePersistentAnger(level, true);
    }

    // --- Loot ---------------------------------------------------------------

    /**
     * Adds the pickaxe bonus on top of the loot table. Deliberately additive: the table is the base
     * drop and stays a plain data file, and this only ever adds.
     */
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        if (!(source.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack weapon = player.getWeaponItem();
        if (weapon.isEmpty() || !weapon.typeHolder().is(ItemTags.PICKAXES)) {
            return;
        }
        int rolled = PICKAXE_BONUS_MIN
                + this.random.nextInt(PICKAXE_BONUS_MAX_EXCLUSIVE - PICKAXE_BONUS_MIN + 1);
        int bonus = (int) Math.floor(rolled * NeroCreaturesConfig.DROP_RATE_MULTIPLIER.get());
        if (bonus > 0) {
            this.spawnAtLocation(level, new ItemStack(ModItems.REFINED_CRYSTAL.get(), bonus));
        }
    }

    // --- Sounds -------------------------------------------------------------
    // Mapped vanilla events: the amethyst set for the crystal read, the iron golem's for the weight
    // of a large construct taking a hit.

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.AMETHYST_BLOCK_RESONATE;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.IRON_GOLEM_DEATH;
    }
}
