package za.co.neroland.nerocreatures.entity.boss;

import java.util.List;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import net.minecraft.core.Holder;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.boss.BossController;
import za.co.neroland.nerocreatures.boss.BossPhase;
import za.co.neroland.nerocreatures.entity.ai.FireVolleyGoal;
import za.co.neroland.nerocreatures.entity.ai.GroundSlamGoal;
import za.co.neroland.nerocreatures.entity.ai.SummonAddsGoal;
import za.co.neroland.nerocreatures.entity.base.CreatureTier;
import za.co.neroland.nerocreatures.entity.base.NeroCreatureEntity;
import za.co.neroland.nerocreatures.entity.hostile.PlasmaSlime;
import za.co.neroland.nerocreatures.registry.ModEntities;

/**
 * <b>Cinder Tyrant</b> — the apex of the ember worlds, and the mod's first planet boss.
 *
 * <p>It is themed off the actual character of Nerospace's Cindara biome, which is a hot, ashen,
 * rain-less place ({@code temperature: 2.0}, {@code has_precipitation: false}, brown-black foliage
 * and water, and a member of Core's {@code neroland:space/dark_biomes}). So the Tyrant is a thing of
 * slag and heat: it is <b>immune to fire</b> (you cannot burn something that is already burning),
 * it throws fire, and the reinforcements it calls are lumps of live plasma pulled out of the ground.
 *
 * <h2>The fight</h2>
 *
 * <p>Three phases, driven by {@link BossController}. Melee, floating, looking around and target
 * selection are permanent — {@link #registerGoals()} owns those and the controller never touches
 * them. What each phase adds on top is:
 *
 * <ol>
 *   <li><b>Stomp</b> (from full health) — a telegraphed {@link GroundSlamGoal}. A one-second wind-up,
 *       then a shockwave that throws everything within {@value #SLAM_RADIUS} blocks. It is a fight
 *       about spacing: stand in melee and take the slam, or keep stepping out of it.</li>
 *   <li><b>Volley &amp; adds</b> (below {@value #PHASE_VOLLEY} health) — the Tyrant stops relying on
 *       reach. A {@link FireVolleyGoal} makes cover matter, and a {@link SummonAddsGoal} pulls up
 *       small Plasma Slimes, budgeted under the population caps and capped at
 *       {@value #ADDS_CONCURRENT} alive at once, so killing them is what keeps them coming and
 *       ignoring them is what stops them.</li>
 *   <li><b>Desperation</b> (below {@value #PHASE_DESPERATION} health) — it enrages: faster, harder,
 *       and both its slam and its volley on much shorter cooldowns. This is where the fight is
 *       either won or lost, and it is intentionally the shortest phase.</li>
 * </ol>
 *
 * <p>The arena is anchored where the Tyrant arrives, {@value #ARENA_RADIUS} blocks across. Kite it
 * out of that and it disengages, walks home and heals; kite it much further and it is simply put
 * back at full health. There is no "pull the boss into a hole" strategy, by design.
 *
 * <h2>Entry, and what it pays</h2>
 *
 * <p>A Tyrant that appears on its own drops its loot table:
 * {@code apex_trophy} and a generous pile of {@code refined_crystal} and {@code plasma_cell}. A
 * Tyrant <b>summoned</b> through {@code boss/BossSummons} drops the same table and additionally pays
 * every participant a share scaled by the damage they did — see {@code boss/BossRewards} and
 * {@code wiki/Bosses.md} for the exact rule.
 *
 * <p>Server-authoritative: phases, enrage, adds and rewards are all decided on the server; the only
 * boss-specific thing the client is told is the boss bar.
 */
public class CinderTyrant extends NeroBossEntity {

    /** Health fraction at which the Tyrant switches to ranged fire and reinforcements. */
    private static final float PHASE_VOLLEY = 0.66F;

    /** Health fraction at which the Tyrant enrages. */
    private static final float PHASE_DESPERATION = 0.33F;

    /** How far from where it arrived the Tyrant will be fought, in blocks. */
    private static final double ARENA_RADIUS = 32.0D;

    /** Shockwave radius, in blocks. */
    private static final double SLAM_RADIUS = 5.0D;

    /** How many Plasma Slimes may be alive around the Tyrant at once. */
    private static final int ADDS_CONCURRENT = 4;

    /** Plasma Slime size the Tyrant pulls up: the smallest, so the adds are pressure, not a wall. */
    private static final int ADD_SLIME_SIZE = 1;

    /** Movement-speed multiplier applied once, on enrage. */
    private static final double ENRAGE_SPEED = 1.35D;

    /** Attack-damage multiplier applied once, on enrage. */
    private static final double ENRAGE_DAMAGE = 1.30D;

    public CinderTyrant(EntityType<? extends CinderTyrant> type, Level level) {
        super(type, level, CreatureTier.APEX);
    }

    /**
     * {@link CreatureTier#APEX}, pushed towards "you will be here a while" rather than "you will die
     * instantly": a deep health pool, real armour and total knockback resistance, but a damage figure
     * a well-equipped player can survive a few of. The numbers a server actually tunes are
     * {@code bossHpMultiplier} and {@code bossDifficultyMultiplier}, which the controller applies to
     * these base values at spawn.
     */
    public static AttributeSupplier.Builder createAttributes() {
        return NeroCreatureEntity.createCreatureAttributes(CreatureTier.APEX)
                .add(Attributes.MAX_HEALTH, 320.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.26D)
                .add(Attributes.ATTACK_DAMAGE, 14.0D)
                .add(Attributes.ARMOR, 12.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 6.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    /**
     * The permanent goal set. Anything here survives every phase transition — the controller only
     * ever adds and removes the goals a {@link BossPhase} declares.
     */
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.6D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected BossController createController() {
        return new BossController(this, this, bossId(),
                Component.translatable("entity.nerocreatures.cinder_tyrant"),
                BossEvent.BossBarColor.RED, ARENA_RADIUS, this.phases());
    }

    /** The Tyrant's id, as used by {@code boss/BossSummons} and the operator command. */
    public static Identifier bossId() {
        return Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "cinder_tyrant");
    }

    /**
     * The three phases. Each goal list is built by a factory rather than up front, because goals
     * carry cooldown state and must be fresh every time a phase is entered; the cooldowns themselves
     * are run through {@link BossController#scaleCooldown} so {@code bossDifficultyMultiplier}
     * genuinely changes the <em>pacing</em> of the fight and not only its numbers.
     */
    private List<BossPhase> phases() {
        return List.of(
                BossPhase.of("stomp",
                        Component.translatable("boss.nerocreatures.cinder_tyrant.stomp"), 1.0F,
                        () -> List.of(BossPhase.goal(1, new GroundSlamGoal(this, SLAM_RADIUS, 8.0F,
                                BossController.scaleCooldown(140), 20)))),

                BossPhase.of("volley",
                        Component.translatable("boss.nerocreatures.cinder_tyrant.volley"), PHASE_VOLLEY,
                        () -> List.of(
                                BossPhase.goal(1, new FireVolleyGoal(this, 3,
                                        BossController.scaleCooldown(110), 8, 5.0D, 24.0D, 0.18D)),
                                BossPhase.goal(2, new SummonAddsGoal(this, ModEntities.PLASMA_SLIME::get,
                                        3, ADDS_CONCURRENT, BossController.scaleCooldown(320),
                                        CinderTyrant::asCinder)))),

                new BossPhase("desperation",
                        Component.translatable("boss.nerocreatures.cinder_tyrant.desperation"),
                        PHASE_DESPERATION,
                        () -> List.of(
                                BossPhase.goal(1, new GroundSlamGoal(this, SLAM_RADIUS + 1.0D, 10.0F,
                                        BossController.scaleCooldown(70), 12)),
                                BossPhase.goal(2, new FireVolleyGoal(this, 5,
                                        BossController.scaleCooldown(70), 6, 4.0D, 28.0D, 0.22D))),
                        this::enrage));
    }

    /** Shrinks a summoned slime to its smallest size — a cinder, not a boss-sized blob. */
    private static void asCinder(Mob add) {
        if (add instanceof PlasmaSlime slime) {
            slime.setSize(ADD_SLIME_SIZE);
        }
    }

    /**
     * The desperation entry action, run exactly once per fight: base speed and damage go up for
     * good. It edits attribute base values rather than adding modifiers because the controller
     * guarantees a phase entry action runs once — a phase is never re-entered and is not replayed
     * when the boss is loaded from disk, so there is nothing to remove or de-duplicate later.
     */
    private void enrage(ServerLevel level) {
        scaleBase(Attributes.MOVEMENT_SPEED, ENRAGE_SPEED);
        scaleBase(Attributes.ATTACK_DAMAGE, ENRAGE_DAMAGE);
        level.sendParticles(ParticleTypes.LAVA, this.getX(), this.getY() + this.getBbHeight() * 0.5D,
                this.getZ(), 40, 1.2D, 1.2D, 1.2D, 0.1D);
        level.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY() + this.getBbHeight() * 0.5D,
                this.getZ(), 60, 1.4D, 1.4D, 1.4D, 0.05D);
        this.playSound(SoundEvents.RAVAGER_ROAR, 2.0F, 0.6F);
    }

    private void scaleBase(Holder<Attribute> attribute, double factor) {
        AttributeInstance instance = this.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(instance.getBaseValue() * factor);
        }
    }

    /**
     * Cindara native: fire and lava are its element. The entity type is also built
     * {@code fireImmune()}, which covers direct burning; this covers the effect that would otherwise
     * be applied by, say, a splash of harming from a player who has decided to fight fire with fire.
     */
    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        if (effect.getEffect().is(MobEffects.POISON) || effect.getEffect().is(MobEffects.WITHER)) {
            return false;
        }
        return super.canBeAffected(effect);
    }

    // --- Sounds -------------------------------------------------------------
    // Mapped vanilla events, like the rest of the roster: the ravager set for the weight of the
    // thing, the blaze set for what it is made of. NeroCreatures ships no audio.

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.BLAZE_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.RAVAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.RAVAGER_DEATH;
    }
}
