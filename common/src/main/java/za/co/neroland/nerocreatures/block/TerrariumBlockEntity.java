package za.co.neroland.nerocreatures.block;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;

/**
 * The Terrarium's one captured creature, kept exactly as the Capture Pod carried it
 * ({@link CapturedCreature}). Only the species id goes to clients (for the display); the entity data
 * stays on the server. Nothing about any player is stored: the capture format carries no owner.
 */
public class TerrariumBlockEntity extends HabitatBlockEntity implements HabitatDisplay {

    public static final Identifier ID = TerrariumBlock.ID;

    /** Which items are Capture Pods: {@code #nerocreatures:capture_pods}. */
    public static final TagKey<Item> CAPTURE_PODS =
            TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "capture_pods"));

    /** Which creatures fit: {@code #nerocreatures:terrarium_small}. */
    public static final TagKey<EntityType<?>> SMALL =
            TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "terrarium_small"));

    private static final String CAPTURED_KEY = "Captured";
    private static final String SPECIES_KEY = "Species";

    @Nullable
    private CompoundTag captured;
    private String species = "";
    private ItemStack shown = ItemStack.EMPTY;
    private String shownFor = "";

    public TerrariumBlockEntity(BlockPos pos, BlockState state) {
        super(type(), pos, state);
    }

    public static BlockEntityType<TerrariumBlockEntity> type() {
        return lookup(ID);
    }

    /** Server: holds a creature. Client: the synced species says so (for the no-mining rule). */
    public boolean occupied() {
        return this.captured != null || !this.species.isEmpty();
    }

    /** Whether a species fits a Terrarium. */
    public static boolean fits(String speciesId) {
        return IncubatorBlockEntity.entityType(speciesId).map(type -> type.builtInRegistryHolder().is(SMALL)).orElse(false);
    }

    /** A player used a Capture Pod on the Terrarium. Server thread only. */
    void usePod(Player player, ItemStack pod) {
        boolean filled = CapturedCreature.isFilled(pod);
        DisplayRules.Action action = DisplayRules.decide(this.occupied(), !filled, true);
        if (pod.getCount() != 1) {
            HabitatBlock.tell(player, Component.translatable("message.nerocreatures.terrarium.one_pod"));
            return;
        }
        switch (action) {
            case INSERT -> {
                Optional<CapturedCreature> capture = CapturedCreature.read(pod);
                if (capture.isEmpty() || !capture.get().understood() || !fits(capture.get().species())) {
                    HabitatBlock.tell(player, Component.translatable("message.nerocreatures.terrarium.too_big"));
                    return;
                }
                this.captured = capture.get().toTag();
                this.species = capture.get().species();
                CapturedCreature.clear(pod);
                this.changed();
            }
            case TAKE -> {
                Optional<CapturedCreature> capture = CapturedCreature.fromTag(this.captured);
                if (capture.isEmpty()) {
                    return;
                }
                capture.get().write(pod);
                this.captured = null;
                this.species = "";
                this.changed();
            }
            case REFUSE_FULL -> HabitatBlock.tell(player,
                    Component.translatable("message.nerocreatures.terrarium.full"));
            default -> HabitatBlock.tell(player, Component.translatable("message.nerocreatures.terrarium.empty"));
        }
    }

    /**
     * Any removal of an occupied Terrarium (creative break, {@code /setblock}, a block-breaking machine,
     * an explosion that beats its blast resistance) hands the creature back as a filled Capture Pod
     * dropped in place, the capture kept verbatim. It is never deleted. Pistons cannot move it (a block
     * entity), and a survival player cannot mine it while full.
     */
    @Override
    protected void onBlockRemoved(ServerLevel level, BlockPos pos) {
        if (this.captured == null) {
            return;
        }
        ItemStack pod = emptyPod();
        if (pod.isEmpty()) {
            return;
        }
        CapturedCreature.writeTag(pod, this.captured);
        Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, pod);
        this.captured = null;
        this.species = "";
    }

    private static ItemStack emptyPod() {
        Identifier id = Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "capture_pod");
        if (!BuiltInRegistries.ITEM.containsKey(id)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(id));
        return stack.is(Items.AIR) ? ItemStack.EMPTY : stack;
    }

    @Override
    public void emptyForGallery() {
        this.captured = null;
        this.species = "";
        this.changed();
    }

    /** Gallery build: holds the creature of a filled Capture Pod (built with no owner). */
    @Override
    public void showForGallery(ItemStack pod) {
        Optional<CapturedCreature> capture = CapturedCreature.read(pod);
        if (capture.isPresent() && capture.get().understood() && fits(capture.get().species())) {
            this.captured = capture.get().toTag();
            this.species = capture.get().species();
            this.changed();
        }
    }

    /** Empty-hand use: what is inside. */
    void describe(Player player) {
        if (!this.occupied()) {
            HabitatBlock.tell(player, Component.translatable("message.nerocreatures.terrarium.empty"));
            return;
        }
        Component name = IncubatorBlockEntity.entityType(this.species).map(EntityType::getDescription)
                .orElse(Component.literal("?"));
        HabitatBlock.tell(player, Component.translatable("message.nerocreatures.terrarium.holds", name));
    }

    // --- display ---------------------------------------------------------------------------

    @Override
    protected boolean syncsToClient() {
        return true;
    }

    /** Clients get the species only, never the entity data. */
    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString(SPECIES_KEY, this.species);
        return tag;
    }

    /** The species' spawn egg, until a creature model renderer replaces it (art pass). */
    @Override
    public ItemStack displayStack() {
        if (!this.species.equals(this.shownFor)) {
            this.shownFor = this.species;
            this.shown = spawnEggFor(this.species);
        }
        return this.shown;
    }

    @Override
    public float displayHeight() {
        return 0.4F;
    }

    @Override
    public float displayScale() {
        return 0.6F;
    }

    private static ItemStack spawnEggFor(String speciesId) {
        Identifier id = Identifier.tryParse(speciesId);
        if (id == null || speciesId.isEmpty()) {
            return ItemStack.EMPTY;
        }
        Identifier egg = Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath() + "_spawn_egg");
        if (!BuiltInRegistries.ITEM.containsKey(egg)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(egg));
        return stack.is(Items.AIR) ? ItemStack.EMPTY : stack;
    }

    // --- persistence -----------------------------------------------------------------------

    //? if >=1.21.6 {
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
    //?} else {
    /*@Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ValueOutput output = ValueOutput.of(tag, registries);
    *///?}
        if (this.captured != null) {
            output.store(CAPTURED_KEY, CompoundTag.CODEC, this.captured);
        }
        output.putString(SPECIES_KEY, this.species);
    }

    //? if >=1.21.6 {
    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
    //?} else {
    /*@Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ValueInput input = ValueInput.of(tag, registries);
    *///?}
        // A client update carries only the species; the server keeps the creature itself.
        this.captured = input.read(CAPTURED_KEY, CompoundTag.CODEC).orElse(null);
        this.species = input.getStringOr(SPECIES_KEY, "");
    }
}
