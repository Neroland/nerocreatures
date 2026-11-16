package za.co.neroland.nerocreatures.entity.mount;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocreatures.data.OwnedCreature;
import za.co.neroland.nerocreatures.entity.base.CreatureTier;
import za.co.neroland.nerocreatures.entity.tame.PetCommand;
import za.co.neroland.nerocreatures.entity.tame.TameableCreature;
import za.co.neroland.nerocreatures.menu.MenuOpener;
import za.co.neroland.nerocreatures.network.CreatureActions;
import za.co.neroland.nerocreatures.trait.TraitEffects;
import za.co.neroland.nerocreatures.trait.TraitPool;

/**
 * The shared mount base: Dune Loper (ground, bounding leap) and Void Manta (flying,
 * blink) subclass this and nothing else. One code path on every line from 1.21.1 to 26.3: the riding
 * API it uses ({@code getControllingPassenger}, {@code tickRidden}, {@code getRiddenInput},
 * {@code getRiddenSpeed}, {@code travel}, {@link PlayerRideableJumping}) was verified identical on
 * 1.21.1, 1.21.5 and 26.3, so there are no {@code //?} blocks for riding; the only line splits are
 * the save/load signatures shared with every entity.
 *
 * <h2>Player contract</h2>
 * <ul>
 *   <li><b>Taming</b> is the pet path (species food, cap-checked) but indexed as
 *       {@link OwnedCreature.Kind#MOUNT} against {@code maxMountsPerPlayer}; mounts never count
 *       against the pet cap.</li>
 *   <li><b>Saddle</b>: the owner uses a {@code creature_saddle} on a tamed adult. Own item and own
 *       synced flag, never the vanilla saddle, so the 1.21.5 saddle-slot split does not apply.</li>
 *   <li><b>Riding</b>: empty hand on a saddled mount. Owner-only by default ({@code mountOwnerOnly});
 *       with it off anyone may ride, but only the owner saddles, equips or opens panniers, or gives
 *       orders. A refused rider gets an action-bar line that never names the owner.</li>
 *   <li><b>Input</b>: vanilla only. WASD steers, look pitch climbs and dives on a flying mount, the
 *       jump bar triggers the ability, sneak dismounts. No custom payload.</li>
 *   <li><b>Stamina</b> 0-{@value #MAX_STAMINA}, synced; regains one point every
 *       {@code mountStaminaRegenTicks} ticks while the ability is not in use; the ability costs
 *       {@code mountAbilityCost} (Thrifty cuts it by 20%). Below the cost the jump bar does
 *       nothing.</li>
 *   <li><b>Panniers</b>: the owner uses a chest on a saddled mount. 9 slots, 18 with Stout. Opened
 *       by the owner sneak-using the mount with an empty hand (vanilla {@code ChestMenu}, through the
 *       guarded opener in {@link MountHooks}). Without panniers, sneak-use cycles the standing order
 *       as for any pet.</li>
 *   <li><b>Shears</b> (owner): take the panniers off (chest and contents drop), or, with no panniers,
 *       take the saddle off.</li>
 *   <li>Saddle, panniers and contents drop on death, on release and on erasure-release.</li>
 * </ul>
 *
 * <h2>Ability split</h2>
 * The client that controls the mount calls {@link #onPlayerJump}; the server receives
 * {@link #handleStartJump} from vanilla's riding-jump packet. Stamina is spent on the server only.
 * Movement abilities (the leap) run on the controlling side in {@link #performAbilityMotion}, like a
 * horse jump; server abilities (the blink) override {@link #onAbilityServer}.
 *
 * <p><b>Privacy.</b> Nothing here stores or logs player identity beyond vanilla's owner UUID on
 * {@code TamableAnimal}; pannier contents are items, not personal data.
 */
public abstract class MountCreature extends TameableCreature implements PlayerRideableJumping {

    public static final int MAX_STAMINA = 100;

    /** Ticks after an ability before stamina starts regaining. */
    private static final int REGEN_PAUSE_TICKS = 20;

    /** Owner must be this close to keep a pannier menu open, squared. */
    private static final double PANNIER_REACH_SQR = 8.0D * 8.0D;

    private static final String MOUNT_KEY = "nerocreatures:mount";
    private static final String PANNIERS_KEY = "nerocreatures:mount.panniers";

    private static final EntityDataAccessor<Integer> DATA_STAMINA =
            SynchedEntityData.defineId(MountCreature.class, EntityDataSerializers.INT);
    /** Server's {@code mountOwnerOnly}, inverted, synced so client and server agree on who controls. */
    private static final EntityDataAccessor<Boolean> DATA_OPEN_RIDING =
            SynchedEntityData.defineId(MountCreature.class, EntityDataSerializers.BOOLEAN);

    @Nullable
    private PannierContainer panniers;

    /** Controlling side only: a released jump bar waiting to be turned into motion. */
    private float pendingAbility;

    private int regenPause;
    private int regenCounter;

    protected MountCreature(EntityType<? extends MountCreature> type, Level level, CreatureTier tier) {
        super(type, level, tier);
    }

    // --- species hooks -----------------------------------------------------

    /** {@code true} for a flying mount (Void Manta): pitch steers climb and dive while ridden. */
    public boolean isFlyingMount() {
        return false;
    }

    /**
     * Controlling side (normally the rider's client): turn a released jump bar into motion. Default:
     * a bounding leap from the ground, strength proportional to the bar.
     */
    protected void performAbilityMotion(Player rider, float strength, Vec3 input) {
        if (!this.onGround() || this.isFlyingMount()) {
            return;
        }
        Vec3 motion = this.getDeltaMovement();
        double up = 0.45D + 0.55D * strength;
        double forward = input.z > 0.0D ? 0.6D * strength : 0.0D;
        float yaw = this.getYRot() * Mth.DEG_TO_RAD;
        this.setDeltaMovement(motion.x - Mth.sin(yaw) * forward, up, motion.z + Mth.cos(yaw) * forward);
    }

    /** Server: the ability fired and its stamina is already spent. Default: nothing (leap is motion-only). */
    protected void onAbilityServer(ServerLevel level, ServerPlayer rider, float strength) {
    }

    @Override
    public OwnedCreature.Kind ownershipKind() {
        return OwnedCreature.Kind.MOUNT;
    }

    // --- state ---------------------------------------------------------------

    /** Synced through the visuals flags (bit 14); saved under {@value #MOUNT_KEY}. */
    public boolean isCreatureSaddled() {
        return this.visuals().saddled();
    }

    /** Synced through the visuals flags (bit 15); saved under {@value #MOUNT_KEY}. */
    public boolean hasPanniers() {
        return this.visuals().panniers();
    }

    private void setSaddled(boolean saddled) {
        this.visuals().setSaddled(saddled);
    }

    private void setPanniers(boolean panniers) {
        this.visuals().setPanniers(panniers);
    }

    /** Gallery paddock only: shows the saddle (and panniers) without the item path or an owner. */
    public void showSaddledForGallery(boolean panniers) {
        this.setSaddled(true);
        this.setPanniers(panniers);
    }

    public int stamina() {
        return this.entityData.get(DATA_STAMINA);
    }

    protected void setStamina(int stamina) {
        this.entityData.set(DATA_STAMINA, Mth.clamp(stamina, 0, MAX_STAMINA));
    }

    /** The ability's stamina cost after Thrifty. */
    public int abilityCost() {
        double multiplier = TraitEffects.cooldownMultiplier(this.traits(), TraitPool.get());
        return Math.max(1, (int) Math.round(MountHooks.abilityCost() * multiplier));
    }

    /** Pannier slots: 9, or 18 with Stout. */
    public int pannierSlots() {
        return TraitEffects.carrySlots(this.traits(), TraitPool.get());
    }

    /** Whether this player may steer the mount (tamed, and owner-only respected). */
    public boolean mayRide(Player player) {
        if (za.co.neroland.nerocreatures.gallery.GalleryDisplays.isPaddock(this)) {
            return player.getAbilities().instabuild; // gallery paddock: any creative player, no owner read
        }
        return this.isTame() && (this.entityData.get(DATA_OPEN_RIDING) || this.isOwnedBy(player));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STAMINA, MAX_STAMINA);
        builder.define(DATA_OPEN_RIDING, false); // owner-only until the server's config says otherwise
    }

    // --- riding ----------------------------------------------------------------

    @Override
    @Nullable
    public LivingEntity getControllingPassenger() {
        if (this.isCreatureSaddled() && this.getFirstPassenger() instanceof Player rider && this.mayRide(rider)) {
            return rider;
        }
        return null;
    }

    @Override
    protected void tickRidden(Player rider, Vec3 input) {
        super.tickRidden(rider, input);
        this.setRot(rider.getYRot(), rider.getXRot() * 0.5F);
        this.yRotO = this.getYRot();
        this.yBodyRot = this.getYRot();
        this.yHeadRot = this.getYRot();
    }

    @Override
    protected Vec3 getRiddenInput(Player rider, Vec3 selfInput) {
        float sideways = rider.xxa * 0.5F;
        float forward = rider.zza;
        if (forward <= 0.0F) {
            forward *= 0.25F;
        }
        return new Vec3(sideways, 0.0D, forward);
    }

    @Override
    protected float getRiddenSpeed(Player rider) {
        return (float) this.getAttributeValue(Attributes.MOVEMENT_SPEED);
    }

    /**
     * Only called where movement is simulated (the controlling client, or the server for an
     * unridden mount), on every line. Turns a pending jump bar into motion, and flies a ridden flying
     * mount.
     */
    @Override
    public void travel(Vec3 input) {
        if (this.getControllingPassenger() instanceof Player rider) {
            if (this.pendingAbility > 0.0F) {
                float strength = this.pendingAbility;
                this.pendingAbility = 0.0F;
                this.performAbilityMotion(rider, strength, input);
            }
            if (this.isFlyingMount()) {
                this.travelFlyingRidden(rider, input);
                return;
            }
        }
        super.travel(input);
    }

    /** Ridden flight: no gravity, look pitch turns forward input into climb or dive. */
    private void travelFlyingRidden(Player rider, Vec3 input) {
        float speed = this.getSpeed();
        double pitch = rider.getXRot() * Mth.DEG_TO_RAD;
        double forward = input.z;
        this.moveRelative(speed * 0.1F, new Vec3(input.x, 0.0D, forward * Math.cos(pitch)));
        Vec3 motion = this.getDeltaMovement();
        double vertical = -Math.sin(pitch) * forward * speed * 1.1D;
        this.setDeltaMovement(motion.x, vertical, motion.z);
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.setDeltaMovement(this.getDeltaMovement().multiply(0.91D, 0.6D, 0.91D));
        this.resetFallDistance();
    }

    // --- jump bar = ability ---------------------------------------------------------

    @Override
    public boolean canJump() {
        return this.isCreatureSaddled() && this.stamina() >= this.abilityCost();
    }

    /** Controlling client: remember the released bar; {@link #travel} applies it. */
    @Override
    public void onPlayerJump(int amount) {
        if (amount > 0 && this.canJump()) {
            this.pendingAbility = jumpScale(amount);
        }
    }

    /** Server, from vanilla's riding-jump packet: spend stamina, then the server half of the ability. */
    @Override
    public void handleStartJump(int amount) {
        if (!(this.level() instanceof ServerLevel level) || !this.canJump()) {
            return;
        }
        this.setStamina(this.stamina() - this.abilityCost());
        this.regenPause = REGEN_PAUSE_TICKS;
        if (this.getControllingPassenger() instanceof ServerPlayer rider) {
            this.onAbilityServer(level, rider, jumpScale(amount));
        }
    }

    @Override
    public void handleStopJump() {
    }

    /** Vanilla's horse curve: 0.4 at a tap, 1.0 at 90% bar and above. */
    public static float jumpScale(int amount) {
        return amount >= 90 ? 1.0F : 0.4F + 0.4F * amount / 90.0F;
    }

    // --- tick ----------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (za.co.neroland.nerocreatures.gallery.GalleryDisplays.isPaddock(this)) {
            za.co.neroland.nerocreatures.gallery.GalleryDisplays.tickPaddock(this, serverLevel); // gallery: stays in its yard, snaps back
            return;
        }
        if (this.regenPause > 0) {
            this.regenPause--;
        } else if (this.stamina() < MAX_STAMINA && ++this.regenCounter >= MountHooks.staminaRegenTicks()) {
            this.regenCounter = 0;
            this.setStamina(this.stamina() + 1);
        }
        if (this.tickCount % 20 == 0) {
            this.entityData.set(DATA_OPEN_RIDING, !MountHooks.ownerOnly());
            // A rider who may no longer steer (owner-only switched on, or the mount was released).
            if (this.getFirstPassenger() instanceof Player rider && !this.mayRide(rider)) {
                this.ejectPassengers();
            }
        }
    }

    // --- interaction ---------------------------------------------------------------

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        // A gallery display does nothing (a paddock mount may be ridden by a creative player).
        if (za.co.neroland.nerocreatures.gallery.GalleryDisplays.isDisplay(this)) {
            return za.co.neroland.nerocreatures.gallery.GalleryDisplays.interact(this, player, hand);
        }
        if (!this.isTame() || this.isBaby()) {
            return super.mobInteract(player, hand);
        }
        ItemStack held = player.getItemInHand(hand);
        boolean owner = this.isOwnedBy(player);
        boolean server = this.level() instanceof ServerLevel;

        if (MountHooks.isSaddle(held) && owner && !this.isCreatureSaddled()) {
            if (server) {
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                this.setSaddled(true);
                //? if >=1.21.5 {
                this.playSound(SoundEvents.HORSE_SADDLE.value(), 0.8F, 1.0F);
                //?} else {
                /*this.playSound(SoundEvents.HORSE_SADDLE, 0.8F, 1.0F);
                *///?}
                return InteractionResult.SUCCESS_SERVER;
            }
            return InteractionResult.SUCCESS;
        }
        if (held.is(Items.CHEST) && owner && this.isCreatureSaddled() && !this.hasPanniers()) {
            if (server) {
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                this.setPanniers(true);
                this.pannierContainer();
                this.playSound(SoundEvents.DONKEY_CHEST, 1.0F, 1.0F);
                return InteractionResult.SUCCESS_SERVER;
            }
            return InteractionResult.SUCCESS;
        }
        if (held.is(Items.SHEARS) && owner && (this.hasPanniers() || this.isCreatureSaddled())) {
            if (this.level() instanceof ServerLevel level) {
                if (this.hasPanniers()) {
                    this.dropPanniers(level);
                } else {
                    this.ejectPassengers();
                    this.dropSaddle(level);
                }
                this.playSound(SoundEvents.SHEEP_SHEAR, 1.0F, 1.0F);
                return InteractionResult.SUCCESS_SERVER;
            }
            return InteractionResult.SUCCESS;
        }
        if (held.isEmpty() && player.isSecondaryUseActive() && owner && this.hasPanniers()) {
            if (player instanceof ServerPlayer serverPlayer) {
                this.openPanniers(serverPlayer);
                return InteractionResult.SUCCESS_SERVER;
            }
            return InteractionResult.SUCCESS;
        }
        if (held.isEmpty() && !player.isSecondaryUseActive() && this.isCreatureSaddled() && !this.isVehicle()) {
            if (!this.mayRide(player)) {
                if (server) {
                    // Never names the owner (POPIA/GDPR).
                    notifyOwner(player, Component.translatable("message.nerocreatures.mount_not_yours"));
                    return InteractionResult.SUCCESS_SERVER;
                }
                return InteractionResult.SUCCESS;
            }
            if (server) {
                if (this.command() == PetCommand.SIT || this.command() == PetCommand.STAY) {
                    this.setCommand(PetCommand.FOLLOW);
                }
                this.getNavigation().stop();
                this.setTarget(null);
                player.setYRot(this.getYRot());
                player.setXRot(this.getXRot());
                player.startRiding(this);
                return InteractionResult.SUCCESS_SERVER;
            }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    // --- panniers ------------------------------------------------------------------

    private PannierContainer pannierContainer() {
        if (this.panniers == null) {
            this.panniers = new PannierContainer(this.pannierSlots());
        }
        return this.panniers;
    }

    private void openPanniers(ServerPlayer player) {
        PannierContainer container = this.pannierContainer();
        int rows = Mth.clamp(container.getContainerSize() / 9, 1, 6);
        MenuType<ChestMenu> type = switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
        MenuProvider provider = new SimpleMenuProvider(
                (id, inventory, opener) -> new ChestMenu(type, id, inventory, container, rows),
                this.getDisplayName());
        MenuOpener.open(player, provider);
    }

    // --- owned-creature actions ------------------------------

    /**
     * Registers the two mount actions with the shared dispatcher, which has already checked that the
     * sender owns this mount and is within reach. Call once during common init.
     */
    public static void registerActions() {
        CreatureActions.register(CreatureActions.MOUNT_OPEN_PANNIERS, MountCreature.class, 6.0D,
                (player, mount) -> {
                    if (za.co.neroland.nerocreatures.gallery.GalleryDisplays.isDisplay(mount)) {
                        player.sendSystemMessage(Component.translatable("hud.nerocreatures.scanner.gallery"), true);
                    } else if (mount.hasPanniers()) {
                        mount.openPanniers(player);
                    }
                });
        CreatureActions.register(CreatureActions.MOUNT_UNSADDLE, MountCreature.class, 6.0D,
                (player, mount) -> {
                    if (mount.level() instanceof ServerLevel level && !za.co.neroland.nerocreatures.gallery.GalleryDisplays.isDisplay(mount)) {
                        mount.dropMountGear(level);
                    }
                });
    }

    /** The pannier inventory: valid only for the owner, near a living mount that still has panniers. */
    private final class PannierContainer extends SimpleContainer {

        PannierContainer(int size) {
            super(size);
        }

        @Override
        public boolean stillValid(Player player) {
            MountCreature mount = MountCreature.this;
            return mount.isAlive() && mount.hasPanniers() && mount.isOwnedBy(player)
                    && mount.distanceToSqr(player) <= PANNIER_REACH_SQR;
        }
    }

    // --- gear drops -----------------------------------------------------------------

    private void dropPanniers(ServerLevel level) {
        if (this.panniers != null) {
            Containers.dropContents(level, this, this.panniers);
            this.panniers = null;
        }
        if (this.hasPanniers()) {
            this.spawnAtLocation(level, new ItemStack(Items.CHEST));
            this.setPanniers(false);
        }
    }

    private void dropSaddle(ServerLevel level) {
        if (this.isCreatureSaddled()) {
            ItemStack saddle = MountHooks.saddleStack();
            if (!saddle.isEmpty()) {
                this.spawnAtLocation(level, saddle);
            }
            this.setSaddled(false);
        }
    }

    /** Drops everything the mount carries: pannier contents, the chest, then the saddle. */
    protected void dropMountGear(ServerLevel level) {
        this.ejectPassengers();
        this.dropPanniers(level);
        this.dropSaddle(level);
    }

    @Override
    protected void releaseToWild(boolean notifyOwner) {
        if (this.level() instanceof ServerLevel level) {
            this.dropMountGear(level);
        }
        super.releaseToWild(notifyOwner);
    }

    @Override
    public void die(DamageSource source) {
        if (this.level() instanceof ServerLevel level) {
            this.dropMountGear(level);
        }
        super.die(source);
    }

    // --- persistence -------------------------------------------------------------------

    /** {@code nerocreatures:mount}: {saddled, panniers, stamina}. */
    private record MountState(boolean saddled, boolean panniers, int stamina) {
        static final Codec<MountState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("saddled", false).forGetter(MountState::saddled),
                Codec.BOOL.optionalFieldOf("panniers", false).forGetter(MountState::panniers),
                Codec.INT.optionalFieldOf("stamina", MAX_STAMINA).forGetter(MountState::stamina)
        ).apply(instance, MountState::new));
    }

    /** One non-empty pannier slot. */
    private record PannierSlot(int slot, ItemStack item) {
        static final Codec<PannierSlot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(0, 53).fieldOf("slot").forGetter(PannierSlot::slot),
                ItemStack.CODEC.fieldOf("item").forGetter(PannierSlot::item)
        ).apply(instance, PannierSlot::new));
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
        output.store(MOUNT_KEY, MountState.CODEC,
                new MountState(this.isCreatureSaddled(), this.hasPanniers(), this.stamina()));
        if (this.panniers != null) {
            List<PannierSlot> slots = new ArrayList<>();
            for (int i = 0; i < this.panniers.getContainerSize(); i++) {
                ItemStack stack = this.panniers.getItem(i);
                if (!stack.isEmpty()) {
                    slots.add(new PannierSlot(i, stack.copy()));
                }
            }
            output.store(PANNIERS_KEY, PannierSlot.CODEC.listOf(), slots);
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
        MountState state = input.read(MOUNT_KEY, MountState.CODEC).orElse(new MountState(false, false, MAX_STAMINA));
        // The saved flags are the source of truth; the visuals bits are their synced copy and are
        // re-written here after the root restored its own copy.
        this.setSaddled(state.saddled());
        this.setPanniers(state.panniers());
        this.setStamina(state.stamina());
        List<PannierSlot> slots = input.read(PANNIERS_KEY, PannierSlot.CODEC.listOf()).orElse(List.of());
        if (state.panniers() || !slots.isEmpty()) {
            // Never smaller than the highest saved slot, so a save never loses an item.
            int highest = 0;
            for (PannierSlot slot : slots) {
                highest = Math.max(highest, slot.slot() + 1);
            }
            int size = Math.max(this.pannierSlots(), Math.min(54, (highest + 8) / 9 * 9));
            this.panniers = new PannierContainer(size);
            for (PannierSlot slot : slots) {
                this.panniers.setItem(slot.slot(), slot.item());
            }
            if (!slots.isEmpty()) {
                this.setPanniers(true);
            }
        }
    }
}
