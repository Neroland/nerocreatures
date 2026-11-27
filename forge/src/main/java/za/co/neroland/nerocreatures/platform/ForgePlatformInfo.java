package za.co.neroland.nerocreatures.platform;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;

/**
 * Forge implementation of {@link PlatformInfo}. Registered via
 * {@code META-INF/services/za.co.neroland.nerocreatures.platform.PlatformInfo}.
 *
 * <p>Forge diverges from NeoForge here: {@code FMLEnvironment.production} / {@code .dist} are
 * fields (not methods), {@code ModList.isLoaded} is static, and there is no NeoForge-style
 * {@code ModList.get().getModContainerById(...)} shape — the instance lookup is reached through
 * the static {@code ModList.getModContainerById} / {@code ModList.getMods()} pair instead.
 */
public final class ForgePlatformInfo implements PlatformInfo {
    //? if <1.20.5 {
    /*@Override
    public net.minecraft.world.entity.ai.attributes.Attribute gravityAttribute() {
        return net.minecraftforge.common.ForgeMod.ENTITY_GRAVITY.get();
    }
    *///?}


    @Override
    public String getPlatformName() {
        return "Forge";
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLEnvironment.production;
    }

    @Override
    public boolean isClient() {
        return FMLEnvironment.dist == Dist.CLIENT;
    }

    @Override
    public String getModVersion() {
        return ModList.getModContainerById(NeroCreaturesCommon.MOD_ID)
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse("unknown");
    }

    @Override
    public List<String> getLoadedModIds() {
        return ModList.getMods().stream()
                .map(m -> m.getModId() + " " + m.getVersion())
                .sorted()
                .toList();
    }

    /** Fires Forge's mob-griefing event (defaults to the {@code mobGriefing} game rule). */
    @Override
    public boolean canMobGrief(ServerLevel level, Mob actor) {
        return ForgeEventFactory.getMobGriefingEvent(level, actor);
    }

    /**
     * Fires Forge's {@code EntityPlaceEvent} for the position as a pre-check (claim mods cancel it).
     * Only called for loaded positions, so the snapshot never loads a chunk.
     */
    @Override
    public boolean mayPlace(ServerLevel level, Mob actor, BlockPos pos, @Nullable BlockState newState) {
        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos);
        return !ForgeEventFactory.onBlockPlace(actor, snapshot, Direction.UP);
    }
}
