package za.co.neroland.nerocreatures.entity.humanoid;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.config.NeroCreaturesConfig;
import za.co.neroland.nerocreatures.entity.base.CreatureTier;
import za.co.neroland.nerocreatures.entity.base.NeroCreatureEntity;

/**
 * <b>Space Pirate</b> — the humanoid raider of the shipping lanes.
 *
 * <p>One entity type, one model, one loot table; what varies is the <b>kit</b>
 * ({@link PirateLoadout}), rolled in {@link #finalizeSpawn} from a small table and applied as real
 * vanilla equipment. That single decision drives the rest of the creature:
 *
 * <ul>
 *   <li>A crossbow kit makes {@link #isRanged()} true, which is the gate on the ranged goal; a blade
 *       kit leaves the melee goal as the only thing that can run. Goals are registered in the
 *       constructor and the kit is chosen at spawn, so gating is the only ordering-safe design —
 *       see {@link PirateLoadout} for the full reasoning behind one type instead of two.</li>
 *   <li>The kit is real equipment, so the weapon's own attack-damage modifier applies on top of the
 *       creature's attribute, and the worn pieces can drop (at {@link PirateLoadout#GEAR_DROP_CHANCE}
 *       per slot) as the "worn gear" half of the loot.</li>
 * </ul>
 *
 * <h2>Who they attack</h2>
 *
 * <p>Players, always. Villagers and colony NPCs <b>only</b> when {@code pirateVillagerAggression} is
 * turned on, and that is off by default — an off-world raider band wandering into a village and
 * wiping it out is exactly the kind of surprise a server owner should have to ask for. The set of
 * "raidable" mobs is the datapack tag {@code nerocreatures:pirate_raid_targets} rather than a Java
 * class, so a colony mod can opt its own NPCs in without NeroCreatures ever referencing it.
 *
 * <p>Spawns in Core's {@code neroland:space/planet_biomes} at low weight, in bands of 2–3. The
 * primary use is deliberate: {@link PirateSpawner} exists so a future NeroEvents raid can place a
 * themed group with one call.
 *
 * <p>Server-authoritative: the kit is rolled, applied and persisted on the server. No player
 * identity is stored on the entity (POPIA/GDPR).
 */
public class SpacePirate extends NeroCreatureEntity implements Enemy, RangedAttackMob {

    /**
     * Mobs a pirate will raid when {@code pirateVillagerAggression} is on. A tag, not a class:
     * {@code minecraft:villager} and {@code minecraft:wandering_trader} ship as optional members and
     * any colony mod can add its own NPCs from a data pack.
     */
    public static final TagKey<EntityType<?>> RAID_TARGETS = TagKey.create(Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "pirate_raid_targets"));

    /** Ticks between shots. 40 = one bolt every two seconds. */
    private static final int SHOT_INTERVAL_TICKS = 40;

    /** Effective range of the ranged goal, in blocks. */
    private static final float SHOT_RANGE = 15.0F;

    /** Bolt launch speed. Vanilla skeletons use the same figure. */
    private static final float BOLT_VELOCITY = 1.6F;

    /** Bolt spread in degrees; lower is more accurate. */
    private static final float BOLT_INACCURACY = 10.0F;

    /** Arc added to the shot so a bolt drops onto a distant target rather than under it. */
    private static final double BOLT_ARC = 0.2D;

    /** The kit this pirate is carrying. Null only in the instant between construction and spawn. */
    private PirateLoadout loadout = PirateLoadout.RECRUIT_BLADE;

    public SpacePirate(EntityType<? extends SpacePirate> type, Level level) {
        super(type, level, CreatureTier.COMMON);
    }

    /**
     * {@link CreatureTier#COMMON} with a low base attack: a pirate's damage is mostly its weapon,
     * which is what makes the kit tiers readable in a fight rather than just a texture change.
     */
    public static AttributeSupplier.Builder createAttributes() {
        return NeroCreatureEntity.createCreatureAttributes(CreatureTier.COMMON)
                .add(Attributes.MAX_HEALTH, 26.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.29D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // Ranged outranks melee, and self-gates on the kit: a blade pirate never runs it at all.
        this.goalSelector.addGoal(2, new BoltVolleyGoal(this));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0D, false));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3, new RaidTargetGoal(this));
    }

    /** The kit this pirate is carrying. */
    public PirateLoadout loadout() {
        return this.loadout;
    }

    /** Applies a kit outside the normal spawn path — used by {@link PirateSpawner}. */
    public void setLoadout(PirateLoadout loadout) {
        this.loadout = loadout;
        loadout.applyTo(this);
    }

    /** Whether this pirate fights at range. The gate on {@link BoltVolleyGoal}. */
    public boolean isRanged() {
        return this.loadout.ranged();
    }

    /**
     * Rolls and applies the kit. Natural bands get the recruit tier; anything placed deliberately
     * (a structure, an event, {@link PirateSpawner}) may ask for better by calling
     * {@link #setLoadout} afterwards.
     */
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
            EntitySpawnReason spawnReason, SpawnGroupData groupData) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnReason, groupData);
        this.setLoadout(PirateLoadout.roll(this.random, PirateLoadout.RECRUIT_TIER));
        // Pirates loot nothing off the ground: picking up player gear would let a band snowball out
        // of the balance the Drop-Map describes.
        this.setCanPickUpLoot(false);
        return result;
    }

    /** Fires one bolt at {@code target}. Called by the ranged goal, server-side only. */
    @Override
    public void performRangedAttack(LivingEntity target, float velocityScale) {
        ItemStack weapon = this.getMainHandItem();
        Arrow bolt = new Arrow(this.level(), this, new ItemStack(Items.ARROW), weapon);
        double deltaX = target.getX() - this.getX();
        double deltaY = target.getY() + target.getBbHeight() * 0.33D - bolt.getY();
        double deltaZ = target.getZ() - this.getZ();
        double horizontal = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        bolt.shoot(deltaX, deltaY + horizontal * BOLT_ARC, deltaZ, BOLT_VELOCITY, BOLT_INACCURACY);
        this.playSound(SoundEvents.CROSSBOW_SHOOT, 1.0F, 1.0F / (this.random.nextFloat() * 0.4F + 0.8F));
        this.level().addFreshEntity(bolt);
    }

    // --- Persistence --------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("Loadout", this.loadout.name());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        // Only the kit identity is restored; the equipment itself is ordinary mob equipment and is
        // already saved by vanilla, so re-applying it here would overwrite battle damage.
        this.loadout = PirateLoadout.byName(input.getStringOr("Loadout", PirateLoadout.RECRUIT_BLADE.name()));
    }

    // --- Sounds -------------------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.PILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.PILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PILLAGER_DEATH;
    }

    /**
     * Vanilla's ranged attack goal, gated on the kit.
     *
     * <h2>Cost profile</h2>
     *
     * <ul>
     *   <li>{@link #canUse()} adds one boolean field read to vanilla's own check — a blade pirate
     *       fails it immediately and the goal never ticks.</li>
     *   <li>Everything else is vanilla {@code RangedAttackGoal}: one strafe/navigation update per
     *       tick while engaged, one projectile every {@value #SHOT_INTERVAL_TICKS} ticks.</li>
     * </ul>
     */
    private static final class BoltVolleyGoal extends RangedAttackGoal {

        private final SpacePirate pirate;

        BoltVolleyGoal(SpacePirate pirate) {
            super(pirate, 1.0D, SHOT_INTERVAL_TICKS, SHOT_RANGE);
            this.pirate = pirate;
        }

        @Override
        public boolean canUse() {
            return this.pirate.isRanged() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return this.pirate.isRanged() && super.canContinueToUse();
        }
    }

    /**
     * Targets villagers and colony NPCs — but only while the server has opted in.
     *
     * <h2>Cost profile</h2>
     *
     * <ul>
     *   <li>With the config off (the default) {@link #canUse()} is a single config read and the goal
     *       never scans for entities at all.</li>
     *   <li>With it on, this is vanilla's nearest-target scan on a {@code randomInterval} of 10
     *       ticks, narrowed by an entity-type tag test.</li>
     * </ul>
     */
    private static final class RaidTargetGoal extends NearestAttackableTargetGoal<Mob> {

        RaidTargetGoal(SpacePirate pirate) {
            super(pirate, Mob.class, 10, true, false,
                    // Via the type's registry holder: EntityType itself carries no tag test in 26.x.
                    (candidate, level) -> candidate.getType().builtInRegistryHolder().is(RAID_TARGETS));
        }

        @Override
        public boolean canUse() {
            return NeroCreaturesConfig.PIRATE_VILLAGER_AGGRESSION.get() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return NeroCreaturesConfig.PIRATE_VILLAGER_AGGRESSION.get() && super.canContinueToUse();
        }
    }
}
