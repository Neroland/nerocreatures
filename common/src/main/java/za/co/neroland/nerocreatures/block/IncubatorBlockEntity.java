package za.co.neroland.nerocreatures.block;

import java.util.Optional;
import java.util.UUID;
import java.util.function.IntSupplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.energy.NeroEnergyStorage;
import za.co.neroland.nerolandcore.platform.EnergyLookup;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.data.CreatureOwnershipState;
import za.co.neroland.nerocreatures.data.OwnedCreature;
import za.co.neroland.nerocreatures.data.OwnershipCaps;
import za.co.neroland.nerocreatures.entity.tame.LivestockCreature;
import za.co.neroland.nerocreatures.entity.tame.TameableCreature;

/**
 * The Incubator's one egg, its progress and its incubation id (rules: {@link IncubatorRules}).
 *
 * <ul>
 *   <li><b>Egg row</b> (decision 004): inserting writes a cap-checked {@code egg} row for the inserting
 *       player, keyed by a random incubation id. The block entity keeps only that id, never the player.
 *       Hatching, taking the egg out or breaking the block removes the row by id.</li>
 *   <li><b>Power</b> is pulled, never pushed: each pulse asks every adjacent block for Nero energy through
 *       Core's {@link EnergyLookup}. No storage, no power mod, or a lookup that fails all mean the plain
 *       unpowered rate.</li>
 *   <li><b>Hatchling</b> (decision 003): an untamed baby with the egg's traits; a tameable species is
 *       flagged so its first feed tames it at 100% through the normal cap-checked path; livestock is born
 *       husbanded.</li>
 * </ul>
 */
public class IncubatorBlockEntity extends HabitatBlockEntity implements HabitatDisplay {

    public static final Identifier ID = IncubatorBlock.ID;

    /** Which items are eggs: {@code #nerocreatures:creature_eggs}. */
    public static final TagKey<Item> EGGS =
            TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "creature_eggs"));

    /** Called when an egg hatches, before its row is removed (Stage 8's {@code egg_hatched} event reads it). */
    @FunctionalInterface
    public interface HatchListener {
        void hatched(ServerLevel level, UUID incubation, Identifier species);
    }

    private static IntSupplier hatchTicks = () -> IncubatorRules.DEFAULT_HATCH_TICKS;
    private static IntSupplier poweredHatchTicks = () -> IncubatorRules.DEFAULT_POWERED_HATCH_TICKS;
    private static IntSupplier energyPerTick = () -> IncubatorRules.DEFAULT_ENERGY_PER_TICK;
    @Nullable
    private static volatile HatchListener hatchListener;

    private ItemStack egg = ItemStack.EMPTY;
    private long progress;
    @Nullable
    private UUID incubation;
    private int pulse;

    public IncubatorBlockEntity(BlockPos pos, BlockState state) {
        super(type(), pos, state);
    }

    public static BlockEntityType<IncubatorBlockEntity> type() {
        return lookup(ID);
    }

    /** Wires {@code incubatorHatchTicks}, {@code incubatorPoweredHatchTicks}, {@code incubatorEnergyPerTick}. */
    public static void configure(IntSupplier hatch, IntSupplier powered, IntSupplier energy) {
        if (hatch != null) {
            hatchTicks = hatch;
        }
        if (powered != null) {
            poweredHatchTicks = powered;
        }
        if (energy != null) {
            energyPerTick = energy;
        }
    }

    public static void bindHatchListener(@Nullable HatchListener listener) {
        hatchListener = listener;
    }

    public boolean occupied() {
        return !this.egg.isEmpty();
    }

    // --- interaction ------------------------------------------------------------------------

    /** A player used an egg on the incubator. Server thread only. */
    void insert(ServerLevel level, ServerPlayer player, ItemStack held) {
        // A creature egg with no data (creative, a command) is resolved from its item id + fresh traits.
        Optional<EggData> data = EggLaying.resolve(held, level.getRandom());
        boolean readable = data.isPresent()
                && entityType(data.get().speciesId()).isPresent();
        IncubatorRules.Insert verdict = IncubatorRules.decideInsert(this.occupied(), readable, true);
        if (verdict == IncubatorRules.Insert.ACCEPTED) {
            Identifier species = Identifier.tryParse(data.get().speciesId());
            long now = level.getGameTime();
            OwnedCreature row = OwnedCreature.egg(species, level.dimension().identifier(),
                    IncubatorRules.hatchAtEstimate(now, 0L, hatchTicks.getAsInt()));
            int cap = OwnershipCaps.capFor(OwnedCreature.Kind.EGG);
            if (!CreatureOwnershipState.get(level.getServer()).tryAdd(player.getUUID(), row, cap)) {
                verdict = IncubatorRules.Insert.CAP;
            } else {
                this.egg = held.copyWithCount(1);
                data.get().write(this.egg); // fixes the resolved traits on the stored egg
                this.progress = 0L;
                this.incubation = row.entity();
                held.consume(1, player);
                this.changed();
                level.playSound(null, this.worldPosition, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS,
                        1.0F, 0.8F);
                HabitatBlock.tell(player, Component.translatable("message.nerocreatures.incubator.inserted",
                        IncubatorRules.secondsLeft(0L, hatchTicks.getAsInt())));
                return;
            }
        }
        HabitatBlock.tell(player, switch (verdict) {
            case OCCUPIED -> Component.translatable("message.nerocreatures.incubator.occupied");
            case CAP -> Component.translatable("message.nerocreatures.egg_cap",
                    OwnershipCaps.capFor(OwnedCreature.Kind.EGG));
            default -> Component.translatable("message.nerocreatures.incubator.not_an_egg");
        });
    }

    /** Sneak-use with an empty hand: the egg comes back out (traits intact), and its row goes. */
    void takeOut(Player player) {
        if (!this.occupied() || !(this.level instanceof ServerLevel level)) {
            return;
        }
        ItemStack out = this.egg;
        this.releaseRow(level);
        this.clear();
        HabitatBlock.give(player, out);
    }

    /** Empty-hand use: how long is left (unpowered worst case). */
    void status(Player player) {
        if (!this.occupied()) {
            HabitatBlock.tell(player, Component.translatable("message.nerocreatures.incubator.empty"));
            return;
        }
        HabitatBlock.tell(player, Component.translatable("message.nerocreatures.incubator.status",
                IncubatorRules.secondsLeft(this.progress, hatchTicks.getAsInt())));
    }

    // --- tick ------------------------------------------------------------------------------

    @Override
    void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (!this.occupied() || ++this.pulse < IncubatorRules.PULSE_TICKS) {
            return;
        }
        this.pulse = 0;
        int hatch = Math.max(1, hatchTicks.getAsInt());
        long wanted = IncubatorRules.energyWanted(IncubatorRules.PULSE_TICKS, energyPerTick.getAsInt());
        long got = wanted > 0L ? pullEnergy(level, pos, wanted) : 0L;
        this.progress += IncubatorRules.progressFor(IncubatorRules.PULSE_TICKS, got, wanted, hatch,
                poweredHatchTicks.getAsInt());
        this.setChanged();
        if (IncubatorRules.ready(this.progress, hatch)) {
            this.hatch(level, pos);
        }
    }

    /** Pulls up to {@code wanted} NE from the six neighbours; never loads a chunk, never throws. */
    static long pullEnergy(ServerLevel level, BlockPos pos, long wanted) {
        long got = 0L;
        try {
            for (Direction side : Direction.values()) {
                BlockPos neighbour = pos.relative(side);
                if (got >= wanted || !level.isLoaded(neighbour)) {
                    continue;
                }
                NeroEnergyStorage storage = EnergyLookup.INSTANCE.find(level, neighbour, side.getOpposite());
                if (storage != null) {
                    got += Math.max(0L, storage.extract(wanted - got, false));
                }
            }
        } catch (RuntimeException | LinkageError e) {
            // No usable power surface: the unpowered rate applies. Nothing to log per pulse.
            return got;
        }
        return Math.min(got, wanted);
    }

    private void hatch(ServerLevel level, BlockPos pos) {
        Optional<EggData> data = EggData.read(this.egg);
        Optional<EntityType<?>> type = data.flatMap(d -> entityType(d.speciesId()));
        Entity entity = type.isPresent() ? type.get().create(level, EntitySpawnReason.BREEDING) : null;
        if (entity == null) {
            // The species is gone (a removed mod or a bad egg): give the egg back rather than loop.
            Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, this.egg);
            this.releaseRow(level);
            this.clear();
            return;
        }
        entity.snapTo(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, level.getRandom().nextFloat() * 360.0F, 0.0F);
        HatchRules outcome = HatchRules.of(entity instanceof TameableCreature c && c.isTameable(),
                entity instanceof LivestockCreature);
        if (outcome.baby() && entity instanceof AgeableMob ageable) {
            ageable.setBaby(true);
        }
        if (entity instanceof TameableCreature creature) {
            creature.setTraits(data.get().traits());
            creature.setHatchling(outcome.hatchling());
            if (outcome.husbanded() && creature instanceof LivestockCreature livestock) {
                livestock.markHusbanded();
            }
        }
        level.addFreshEntity(entity);
        level.playSound(null, pos, SoundEvents.SNIFFER_EGG_HATCH, SoundSource.BLOCKS, 1.0F, 1.0F);
        HatchListener listener = hatchListener;
        if (listener != null && this.incubation != null) {
            try {
                listener.hatched(level, this.incubation, EntityType.getKey(entity.getType()));
            } catch (RuntimeException e) {
                NeroCreaturesCommon.LOGGER.warn("[NeroCreatures] Hatch listener failed: {}", e.getClass().getSimpleName());
            }
        }
        this.releaseRow(level);
        this.clear();
    }

    /** The registered entity type for an id, if any (never the registry's default). */
    static Optional<EntityType<?>> entityType(String id) {
        Identifier key = Identifier.tryParse(id);
        return key == null ? Optional.empty() : BuiltInRegistries.ENTITY_TYPE.getOptional(key);
    }

    private void releaseRow(ServerLevel level) {
        if (this.incubation != null) {
            CreatureOwnershipState.get(level.getServer()).removeRow(this.incubation);
            this.incubation = null;
        }
    }

    private void clear() {
        this.egg = ItemStack.EMPTY;
        this.progress = 0L;
        this.incubation = null;
        this.changed();
    }

    @Override
    protected void onBlockRemoved(ServerLevel level, BlockPos pos) {
        if (this.occupied()) {
            Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, this.egg);
            this.releaseRow(level);
            this.egg = ItemStack.EMPTY;
        }
    }

    // --- display ---------------------------------------------------------------------------

    @Override
    protected boolean syncsToClient() {
        return true;
    }

    @Override
    public ItemStack displayStack() {
        return this.egg;
    }

    @Override
    public float displayHeight() {
        return 0.45F;
    }

    @Override
    public float displayScale() {
        return 0.45F;
    }

    // --- persistence -----------------------------------------------------------------------

    //? if >=1.21.6 {
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
    //?} else {
    /*@Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ValueOutput output = ValueOutput.of(tag, registries);
    *///?}
        if (!this.egg.isEmpty()) {
            output.store("Egg", ItemStack.CODEC, this.egg);
        }
        output.putLong("Progress", this.progress);
        if (this.incubation != null) {
            output.putString("Incubation", this.incubation.toString());
        }
    }

    //? if >=1.21.6 {
    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
    //?} else {
    /*@Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ValueInput input = ValueInput.of(tag, registries);
    *///?}
        this.egg = input.read("Egg", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        this.progress = Math.max(0L, input.getLongOr("Progress", 0L));
        String stored = input.getStringOr("Incubation", "");
        UUID id = null;
        if (!stored.isEmpty()) {
            try {
                id = UUID.fromString(stored);
            } catch (IllegalArgumentException ignored) {
                id = null;
            }
        }
        this.incubation = id;
    }
}
