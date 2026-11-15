package za.co.neroland.nerocreatures.block;

import java.util.Optional;
import java.util.UUID;
import java.util.function.IntSupplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocreatures.boss.BossContributionState;
import za.co.neroland.nerocreatures.boss.BossController;
import za.co.neroland.nerocreatures.boss.BossFights;
import za.co.neroland.nerocreatures.boss.BossSummons;
import za.co.neroland.nerocreatures.entity.boss.NeroBossEntity;
import za.co.neroland.nerocreatures.item.SigilItem;

/**
 * The Apex Altar's state: its cooldown end, the fight it started (a random per-fight id, never a
 * player) and which sigil lit it. The rules live in {@link ApexAltarRules}; this class only connects
 * them to the world.
 *
 * <p>Saved data: {@code ReadyAt} (game time), {@code FightStartedAt} (game time), {@code Fight}
 * (fight id), {@code Sigil} (sigil kind). No player identity is stored on the altar: the summoner is
 * passed to the boss framework, which records them as a participant of that fight's contribution
 * row (an existing, erasable store) and nowhere else.
 */
public class ApexAltarBlockEntity extends BlockEntity {

    /** The registry id the integration pass registers the block-entity type under. */
    public static final Identifier ID = ApexAltarBlock.ID;

    private static IntSupplier cooldownTicks = () -> ApexAltarRules.DEFAULT_COOLDOWN_TICKS;

    @Nullable
    private static volatile BlockEntityType<ApexAltarBlockEntity> type;

    private final ApexAltarRules.State state = new ApexAltarRules.State();
    private int refresh;

    public ApexAltarBlockEntity(BlockPos pos, BlockState blockState) {
        super(type(), pos, blockState);
    }

    /** Wires the cooldown config key ({@code apexAltarCooldownTicks}); called once at common init. */
    public static void configure(IntSupplier cooldown) {
        if (cooldown != null) {
            cooldownTicks = cooldown;
        }
    }

    /** The registered type, looked up by {@link #ID} once and cached. */
    @SuppressWarnings("unchecked")
    public static BlockEntityType<ApexAltarBlockEntity> type() {
        BlockEntityType<ApexAltarBlockEntity> cached = type;
        if (cached == null) {
            cached = (BlockEntityType<ApexAltarBlockEntity>) BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(ID);
            if (cached == null) {
                throw new IllegalStateException("nerocreatures:apex_altar block entity type is not registered");
            }
            type = cached;
        }
        return cached;
    }

    // --- interaction --------------------------------------------------------

    /**
     * A player used a sigil on this altar. Server thread only. Consumes the sigil exactly when a fight
     * started; tells the player why otherwise.
     */
    public void trySummon(ServerLevel level, BlockPos pos, ServerPlayer player, ItemStack stack,
            SigilItem.Kind kind) {
        long now = level.getGameTime();
        Identifier bossId = kind.bossId();
        ApexAltarRules.State.Outcome outcome = this.state.attempt(now, !stack.isEmpty(),
                BossSummons.isKnown(bossId), level.getDifficulty() == Difficulty.PEACEFUL,
                hasHeadroom(level, pos, bossId), this.fightProbe(level),
                cooldownTicks.getAsInt(), kind.name(), () -> {
                    Optional<NeroBossEntity> boss = kind.summoner().summon(level, pos.above(), player.getUUID());
                    return boss.map(b -> b.bossController().fightId()).orElse(null);
                });
        if (outcome.consumed()) {
            stack.consume(1, player);
            this.setChanged();
            this.applyLock(level, pos, this.getBlockState(), now, true);
            level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 1.0F, 0.7F);
            return;
        }
        player.sendSystemMessage(message(outcome.verdict(), now, this.state.readyAt()), true);
    }

    private static Component message(ApexAltarRules.Verdict verdict, long now, long readyAt) {
        return switch (verdict) {
            case COOLING_DOWN -> Component.translatable("message.nerocreatures.apex_altar.cooling",
                    ApexAltarRules.secondsLeft(now, readyAt));
            case FIGHT_LIVE -> Component.translatable("message.nerocreatures.apex_altar.fight_live");
            case UNKNOWN_BOSS -> Component.translatable("message.nerocreatures.apex_altar.unknown_boss");
            case PEACEFUL -> Component.translatable("message.nerocreatures.apex_altar.peaceful");
            case NO_ROOM -> Component.translatable("message.nerocreatures.apex_altar.no_room");
            default -> Component.translatable("message.nerocreatures.apex_altar.failed");
        };
    }

    /**
     * The boss stands on the altar: its whole body, plus a margin, must fit without touching a block
     * ({@link ApexAltarRules#summonBox}). Built from the boss type's real size, so a 3-block-wide boss
     * is never summoned into a 1-block shaft. An unknown type has no room.
     */
    private static boolean hasHeadroom(ServerLevel level, BlockPos pos, Identifier bossId) {
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(bossId)) {
            return false;
        }
        EntityType<?> bossType = BuiltInRegistries.ENTITY_TYPE.getValue(bossId);
        EntityDimensions size = bossType.getDimensions();
        BlockPos feet = pos.above();
        double[] b = ApexAltarRules.summonBox(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D,
                size.width(), size.height());
        AABB box = new AABB(b[0], b[1], b[2], b[3], b[4], b[5]);
        BlockPos min = BlockPos.containing(b[0], b[1], b[2]);
        BlockPos max = BlockPos.containing(b[3], b[4], b[5]);
        if (!level.isLoaded(min) || !level.isLoaded(max) || !level.isLoaded(new BlockPos(min.getX(), min.getY(), max.getZ()))
                || !level.isLoaded(new BlockPos(max.getX(), min.getY(), min.getZ()))) {
            return false;
        }
        return level.noCollision(box);
    }

    /** Whether the world still holds this altar's fight: loaded and alive, or a contribution row. */
    private boolean fightProbe(ServerLevel level) {
        UUID fight = this.state.fight();
        if (fight == null) {
            return false;
        }
        for (BossController controller : BossFights.active()) {
            if (fight.equals(controller.fightId())) {
                return true;
            }
        }
        return BossContributionState.get(level.getServer()).participantCount(fight) > 0;
    }

    // --- tick ---------------------------------------------------------------

    /** Every {@value ApexAltarRules#REFRESH_TICKS} ticks: release the lock when it is over. */
    void serverTick(ServerLevel level, BlockPos pos, BlockState blockState) {
        if (++this.refresh < ApexAltarRules.REFRESH_TICKS) {
            return;
        }
        this.refresh = 0;
        long now = level.getGameTime();
        if (this.state.clampToNow(now)) {
            this.setChanged();
        }
        boolean probe = this.fightProbe(level);
        UUID before = this.state.fight();
        this.state.clearFightIfOver(now, probe);
        if (before != null && this.state.fight() == null) {
            this.setChanged();
        }
        this.applyLock(level, pos, blockState, now, this.state.locked(now, probe));
    }

    /** Writes the lock into the block state (glow, sync and the unbreakable rule). */
    private void applyLock(ServerLevel level, BlockPos pos, BlockState blockState, long now, boolean locked) {
        if (!blockState.hasProperty(ApexAltarBlock.SIGIL)) {
            return;
        }
        ApexAltarBlock.Sigil wanted = ApexAltarBlock.Sigil.NONE;
        if (locked) {
            wanted = glowOf(this.state.sigil());
        }
        if (blockState.getValue(ApexAltarBlock.SIGIL) != wanted) {
            level.setBlock(pos, blockState.setValue(ApexAltarBlock.SIGIL, wanted), Block.UPDATE_CLIENTS);
        }
    }

    private static ApexAltarBlock.Sigil glowOf(String kindName) {
        for (SigilItem.Kind kind : SigilItem.Kind.values()) {
            if (kind.name().equals(kindName)) {
                return kind.glow();
            }
        }
        // A lock with no readable sigil still has to show as locked.
        return ApexAltarBlock.Sigil.NYXARA;
    }

    // --- persistence --------------------------------------------------------

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
        output.putLong("ReadyAt", this.state.readyAt());
        output.putLong("FightStartedAt", this.state.fightStartedAt());
        UUID fight = this.state.fight();
        if (fight != null) {
            output.putString("Fight", fight.toString());
        }
        output.putString("Sigil", this.state.sigil());
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
        UUID fight = null;
        String stored = input.getStringOr("Fight", "");
        if (!stored.isEmpty()) {
            try {
                fight = UUID.fromString(stored);
            } catch (IllegalArgumentException ignored) {
                fight = null;
            }
        }
        this.state.restore(input.getLongOr("ReadyAt", 0L), input.getLongOr("FightStartedAt", 0L), fight,
                input.getStringOr("Sigil", ""));
    }
}
