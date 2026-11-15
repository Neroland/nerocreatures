package za.co.neroland.nerocreatures.entity.tame;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import za.co.neroland.nerocreatures.config.NeroCreaturesConfig;
import za.co.neroland.nerocreatures.entity.ai.shared.DigestState;
import za.co.neroland.nerocreatures.entity.ai.shared.ItemEatingGoal;
import za.co.neroland.nerocreatures.entity.base.CreatureTier;
import za.co.neroland.nerocreatures.entity.visual.VisualPose;
import za.co.neroland.nerocreatures.trait.TraitEffects;
import za.co.neroland.nerocreatures.trait.TraitPool;
import za.co.neroland.nerocreatures.world.CreatureGriefing;
import za.co.neroland.nerocreatures.registry.ModSounds;

/**
 * <b>Slag Beetle</b> (Cindara, tameable): a living furnace for a field base.
 *
 * <h2>Ability: digestion smelting</h2>
 *
 * <ul>
 *   <li>A tamed beetle walks to raw ore its <b>owner threw</b> within
 *       {@value SlagBeetleRules#FEED_RADIUS} blocks (item tag {@link #FOOD_ORES}) and swallows it one
 *       item at a time through the shared {@link ItemEatingGoal}. Taking the item is a
 *       {@code CreatureGriefing} {@code Change.ITEM} decision under {@code slagBeetleSmelting}
 *       (decision 006); denied, the beetle ignores the item.</li>
 *   <li>Each item digests for {@value SlagBeetleRules#DIGEST_TICKS} ticks (a furnace's ten seconds),
 *       in order, up to {@value SlagBeetleRules#STOMACH_CAPACITY} items. The result is the server's own
 *       vanilla smelting recipe for that item, so packs and other mods' raw ores work. Every item
 *       has a {@code 10%} chance of one extra, and Bountiful adds a second {@code 10%} roll (never a
 *       multiplier; {@link SlagBeetleRules}).
 *       Food is raw-ore items only; ore blocks are refused.
 *       An item with no smelting recipe comes back out unchanged.</li>
 *   <li>The stomach is saved with the beetle ({@value #DIGEST_KEY}: item ids and ready times only).
 *       A beetle that dies returns everything still undigested. Nothing is ever lost.</li>
 *   <li>Visuals: {@code stage} 0-3 is the abdomen glow ramp (empty, then the head item's progress);
 *       {@code ability} is the eat (mandibles gather), {@code ability_2} the "cough" that drops the
 *       result.</li>
 * </ul>
 *
 * <p><b>Magnet Crab:</b> fresh items and beetle food near a tamed beetle are claimed for it, and a
 * tamed crab leaves them alone ({@link SlagBeetleClaims}).
 *
 * <p>Perk: a warm shell. A nearby owner thaws out (freezing ticks cleared). Comfort only.
 *
 * <p>Breeding: {@code nerocreatures:food/slag_beetle} (coal, charcoal; never ore, which is its
 * smelting input), egg birth (decision 003).
 */
public class SlagBeetle extends TameableCreature {

    /** What a beetle will eat and smelt. Hand-authored; packs may extend it. */
    public static final TagKey<Item> FOOD_ORES = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath("nerocreatures", "slag_beetle_ores"));

    /** Save key of the stomach. */
    public static final String DIGEST_KEY = "nerocreatures:digest";

    private static final int SCAN_INTERVAL_TICKS = 20;
    private static final int DIGEST_POLL_TICKS = 20;
    private static final int EAT_POSE_TICKS = 10;
    private static final int COUGH_POSE_TICKS = 20;

    // No initialisers: registerGoals() runs from the Mob constructor, before field initialisers would.
    private DigestState stomach;
    private ItemEatingGoal eating;
    private int lastStomachSize;
    private int digestPollCooldown;

    public SlagBeetle(EntityType<? extends SlagBeetle> type, Level level) {
        super(type, level, CreatureTier.LESSER);
    }

    /** Small and armoured: a tall carapace dome on six stub legs. */
    public static AttributeSupplier.Builder createAttributes() {
        return TameableCreature.createPetAttributes(CreatureTier.LESSER)
                .add(Attributes.MAX_HEALTH, 18.0D)
                .add(Attributes.ARMOR, 6.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.24D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.FOLLOW_RANGE, 16.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3D);
    }

    @Override
    public Item tamingFood() {
        return Items.COAL;
    }

    @Override
    public boolean breedable() {
        return true;
    }

    /** The stomach, created on first use (it is needed while the super constructor runs). */
    public DigestState stomach() {
        if (this.stomach == null) {
            this.stomach = new DigestState(SlagBeetleRules.STOMACH_CAPACITY, SlagBeetleRules.DIGEST_TICKS);
        }
        return this.stomach;
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.eating = new ItemEatingGoal(this, this.stomach(), SlagBeetleRules.FEED_RADIUS, SCAN_INTERVAL_TICKS,
                1.15D, (eater, item) -> this.acceptsThrown(item), this::mayTake, this::digested) {
            @Override
            public boolean canUse() {
                // Wild beetles never eat (owner-thrown items only), so they never scan either.
                return SlagBeetle.this.isTame() && super.canUse();
            }
        };
        // Above follow-owner (5): the owner throws ore near it and it goes to eat.
        this.goalSelector.addGoal(4, this.eating);
    }

    /** Filter: tamed, beetle food, thrown by this beetle's owner. */
    private boolean acceptsThrown(ItemEntity item) {
        return this.isTame() && item.getItem().is(FOOD_ORES) && ItemEatingGoal.thrownBy(item, this.ownerId());
    }

    /** Permission: decision 006, {@code Change.ITEM} under {@code slagBeetleSmelting}. */
    private boolean mayTake(ServerLevel level, PathfinderMob eater, ItemEntity item) {
        boolean food = item.getItem().is(FOOD_ORES);
        boolean thrown = ItemEatingGoal.thrownBy(item, this.ownerId());
        return SlagBeetleRules.mayEat(this.isTame(), thrown, food, food && thrown
                && CreatureGriefing.mayAlter(level, eater, item.blockPosition(),
                        NeroCreaturesConfig.SLAG_BEETLE_SMELTING, CreatureGriefing.Change.ITEM));
    }

    /** Output: the smelted result (plus bonus), or the eaten item back if nothing smelts it. */
    private void digested(ServerLevel level, PathfinderMob eater, Item eaten) {
        ItemStack raw = new ItemStack(eaten);
        ItemStack result = smeltResult(level, raw);
        if (result.isEmpty()) {
            this.spawnAtLocation(level, raw);
            return;
        }
        int rolls = SlagBeetleRules.bonusRolls(TraitEffects.productRolls(this.traits(), TraitPool.get(), this.random));
        int hits = 0;
        for (int i = 0; i < rolls; i++) {
            if (this.random.nextDouble() < SlagBeetleRules.BONUS_CHANCE) {
                hits++;
            }
        }
        int count = SlagBeetleRules.outputCount(result.getCount(), hits);
        result.setCount(Math.max(1, Math.min(count, result.getMaxStackSize())));
        this.spawnAtLocation(level, result);
        this.visuals().playPose(VisualPose.ABILITY_2, COUGH_POSE_TICKS);
        ModSounds.SLAG_BEETLE_COUGH.play(this);
        level.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.4D, this.getZ(),
                4, 0.2D, 0.1D, 0.2D, 0.01D);
    }

    /** The vanilla smelting result for one item, or empty. Server recipe manager only. */
    public static ItemStack smeltResult(ServerLevel level, ItemStack raw) {
        if (raw.isEmpty()) {
            return ItemStack.EMPTY;
        }
        SingleRecipeInput input = new SingleRecipeInput(raw);
        //? if >=1.21.2 {
        var match = level.recipeAccess().getRecipeFor(RecipeType.SMELTING, input, level);
        //?} else {
        /*var match = level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, input, level);
        *///?}
        if (match.isEmpty()) {
            return ItemStack.EMPTY;
        }
        //? if >=26.1 {
        ItemStack out = match.get().value().assemble(input);
        //?} else {
        /*ItemStack out = match.get().value().assemble(input, level.registryAccess());
        *///?}
        return out == null ? ItemStack.EMPTY : out.copy();
    }

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
        DigestState belly = this.stomach();
        int size = belly.size();
        if (size > this.lastStomachSize) {
            this.visuals().playPose(VisualPose.ABILITY, EAT_POSE_TICKS);
            ModSounds.SLAG_BEETLE_EAT.play(this);
        }
        this.lastStomachSize = size;
        if (--this.digestPollCooldown > 0) {
            return;
        }
        this.digestPollCooldown = DIGEST_POLL_TICKS;
        // The eating goal also polls from canUse, but follow-owner can keep it from being asked.
        if (this.eating != null && !belly.isEmpty()) {
            this.eating.tickDigestion(level);
        }
        this.lastStomachSize = belly.size();
        int stage = belly.stage(level.getGameTime());
        if (stage != this.visuals().stage() && stage > 0) {
            ModSounds.SLAG_BEETLE_DIGEST.play(this);
        }
        this.visuals().setStage(stage);
    }

    /** A warm shell: an owner nearby stops freezing. */
    @Override
    protected void applyOwnerPerk(ServerLevel level, Player owner) {
        if (owner.getTicksFrozen() > 0) {
            owner.setTicksFrozen(0);
            level.sendParticles(ParticleTypes.SMALL_FLAME, this.getX(), this.getY() + this.getBbHeight(),
                    this.getZ(), 2, 0.2D, 0.1D, 0.2D, 0.0D);
        }
    }

    /** Undigested items come back out; nothing a player fed it is lost. */
    @Override
    public void die(DamageSource source) {
        if (this.level() instanceof ServerLevel level) {
            for (String id : this.stomach().drain()) {
                Identifier key = Identifier.tryParse(id);
                if (key != null && BuiltInRegistries.ITEM.containsKey(key)) {
                    this.spawnAtLocation(level, new ItemStack(BuiltInRegistries.ITEM.getValue(key)));
                }
            }
            this.visuals().setStage(0);
        }
        super.die(source);
    }

    //? if >=1.21.6 {
    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
    //?} else {
    /*@Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        ValueOutput output = ValueOutput.of(nbt, this.registryAccess());
    *///?}
        String encoded = this.stomach().encode();
        if (!encoded.isEmpty()) {
            output.putString(DIGEST_KEY, encoded);
        }
    }

    //? if >=1.21.6 {
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
    //?} else {
    /*@Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        ValueInput input = ValueInput.of(nbt, this.registryAccess());
    *///?}
        this.stomach().decode(input.getStringOr(DIGEST_KEY, ""));
        this.lastStomachSize = this.stomach().size();
    }

    // Mapped vanilla events, pitched down: a small armoured crawler.

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.SLAG_BEETLE_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return ModSounds.SLAG_BEETLE_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.SLAG_BEETLE_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 0.6F;
    }
}
