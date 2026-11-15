package za.co.neroland.nerocreatures.entity.greenxertz;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerocreatures.config.NeroCreaturesConfig;
import za.co.neroland.nerocreatures.entity.ai.shared.CadenceTimer;
import za.co.neroland.nerocreatures.entity.ai.shared.EcosystemTags;
import za.co.neroland.nerocreatures.entity.ai.shared.HerdCohesionGoal;
import za.co.neroland.nerocreatures.entity.ai.shared.HerdMember;
import za.co.neroland.nerocreatures.entity.ai.shared.PreyFleeGoal;
import za.co.neroland.nerocreatures.entity.base.CreatureTier;
import za.co.neroland.nerocreatures.entity.tame.LivestockCreature;
import za.co.neroland.nerocreatures.entity.visual.VisualPose;
import za.co.neroland.nerocreatures.world.CreatureGriefing;
import za.co.neroland.nerocreatures.registry.ModSounds;

/**
 * <b>Mossback</b> (Greenxertz, livestock) — a long, low barrel body with a flat moss-carpeted back and
 * a broad shovel muzzle.
 *
 * <ul>
 *   <li><b>Herd grazer and prey.</b> Keeps with its herd ({@link HerdCohesionGoal}) and flees
 *       {@code #nerocreatures:predators} as a herd ({@link PreyFleeGoal}, {@link HerdMember});
 *       listed in {@code #nerocreatures:prey/grazers}, so stalkers and lurkers hunt it.</li>
 *   <li><b>Fertilises the ground it grazes</b> (decision 006, key {@code mossbackFertilises},
 *       {@code Change.PLACE}): see {@link MossbackRules}. On a grass block it grazes every 15 s (the
 *       {@code ability} pose); at most once a minute the grazed grass is bone-mealed through
 *       {@code BoneMealItem.growCrop} (grass and flowers sprout; grass never becomes dirt). Denied:
 *       it grazes with no effect.</li>
 *   <li>Breeds from fern or short grass (live birth, decision 003); drops {@code mossback_hide} and
 *       {@code raw_mossback}. Sleeps (held {@code rest}) when idle after dark.</li>
 * </ul>
 */
public class Mossback extends LivestockCreature implements HerdMember {

    private static final int SLEEP_CHECK_TICKS = 40;

    private final CadenceTimer grazeCadence = new CadenceTimer(MossbackRules.GRAZE_INTERVAL);
    /** Game time of the pending fertilise tick, or {@link Long#MIN_VALUE}. */
    private long fertiliseAt = Long.MIN_VALUE;
    /** No fertilising before this game time. Not saved (a minute's cooldown). */
    private long cooldownUntil = Long.MIN_VALUE;

    private long alarmUntil = Long.MIN_VALUE;
    private Vec3 alarmThreat = Vec3.ZERO;

    public Mossback(EntityType<? extends Mossback> type, Level level) {
        super(type, level, CreatureTier.COMMON);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivestockCreature.createPetAttributes(CreatureTier.COMMON)
                .add(Attributes.MAX_HEALTH, 22.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.2D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(1, new PreyFleeGoal(this, EcosystemTags.PREDATORS, 10.0D, 14.0D, 40, 100, 1.4D));
        this.goalSelector.addGoal(5, new HerdCohesionGoal(this, 20.0D, 7.0D, 60, 1.0D));
    }

    // --- grazing ---------------------------------------------------------------------

    //? if >=1.21.2 {
    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
    //?} else {
    /*@Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel level = (ServerLevel) this.level();
    *///?}
        if (this.tickCount % SLEEP_CHECK_TICKS == 0) {
            this.updateSleep(level);
        }
        long now = level.getGameTime();
        if (this.fertiliseAt != Long.MIN_VALUE && now >= this.fertiliseAt) {
            this.fertiliseAt = Long.MIN_VALUE;
            this.fertilise(level, now);
        }
        if (this.farTick().skip(this) || !this.grazeCadence.ready(now, this.getId())) {
            return;
        }
        if (this.visuals().pose() == VisualPose.REST || !this.getNavigation().isDone()
                || !MossbackRules.mayGraze(!this.isBaby(), this.onGrass(level))) {
            return;
        }
        this.visuals().playPose(VisualPose.ABILITY, MossbackRules.GRAZE_POSE_TICKS);
        ModSounds.MOSSBACK_GRAZE.play(this);
        this.fertiliseAt = now + MossbackRules.FERTILISE_AT_TICK;
    }

    private BlockPos grazed() {
        return this.getBlockPosBelowThatAffectsMyMovement();
    }

    private boolean onGrass(ServerLevel level) {
        BlockPos below = this.grazed();
        return level.isLoaded(below) && level.getBlockState(below).is(Blocks.GRASS_BLOCK);
    }

    private void fertilise(ServerLevel level, long now) {
        BlockPos grass = this.grazed();
        boolean onGrass = this.onGrass(level);
        boolean allowed = onGrass && now >= this.cooldownUntil && CreatureGriefing.mayAlter(level, this, grass,
                NeroCreaturesConfig.MOSSBACK_FERTILISES, CreatureGriefing.Change.PLACE);
        MossbackRules.Graze result = MossbackRules.fertilise(!this.isBaby(), onGrass, now, this.cooldownUntil, allowed);
        if (result == MossbackRules.Graze.NONE) {
            return;
        }
        level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, this.getX(), this.getY() + 0.2D, this.getZ(),
                4, 0.4D, 0.1D, 0.4D, 0.0D);
        if (result == MossbackRules.Graze.FERTILISE
                && BoneMealItem.growCrop(new ItemStack(Items.BONE_MEAL), level, grass)) {
            this.cooldownUntil = now + MossbackRules.FERTILISE_COOLDOWN;
            level.levelEvent(1505, grass, 15); // vanilla bone-meal particles
            level.playSound(null, grass, SoundEvents.BONE_MEAL_USE, SoundSource.NEUTRAL, 0.5F, 1.0F);
        } else {
            level.playSound(null, grass, SoundEvents.GRASS_BREAK, SoundSource.NEUTRAL, 0.4F, 1.0F);
        }
    }

    private void updateSleep(ServerLevel level) {
        boolean sleep = level.isDarkOutside() && this.getNavigation().isDone() && this.getTarget() == null
                && this.getLastHurtByMob() == null && !this.isInLove() && level.getGameTime() >= this.alarmUntil;
        VisualPose pose = this.visuals().pose();
        if (sleep && pose == VisualPose.IDLE) {
            this.visuals().holdPose(VisualPose.REST);
        } else if (!sleep && pose == VisualPose.REST) {
            this.visuals().holdPose(VisualPose.IDLE);
        }
    }

    // --- herd alarm ---------------------------------------------------------------------

    @Override
    public void raiseAlarm(Vec3 threat, long untilGameTime) {
        this.alarmThreat = threat;
        this.alarmUntil = untilGameTime;
    }

    @Override
    public long alarmUntil() {
        return this.alarmUntil;
    }

    @Override
    public Vec3 alarmThreat() {
        return this.alarmThreat;
    }

    // --- sounds (mapped vanilla until the sound pass) -------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.MOSSBACK_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return ModSounds.MOSSBACK_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.MOSSBACK_DEATH.get();
    }
}
