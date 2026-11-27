package za.co.neroland.nerocreatures.mixin;

//? if <1.20.3 {
/*import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import za.co.neroland.nerocreatures.block.EphemeralCrustBlock;

@Mixin(Explosion.class)
abstract class LegacyExplosionMixin {
    @Shadow @Final
    private Level level;

    @Unique
    private Map<BlockPos, BlockState> nerocreatures$crusts;

    @Inject(method = "finalizeExplosion", at = @At("HEAD"))
    private void nerocreatures$captureCrusts(boolean particles, CallbackInfo callback) {
        if (this.level.isClientSide) {
            return;
        }
        for (BlockPos pos : ((Explosion) (Object) this).getToBlow()) {
            BlockState state = this.level.getBlockState(pos);
            if (state.getBlock() instanceof EphemeralCrustBlock) {
                if (this.nerocreatures$crusts == null) {
                    this.nerocreatures$crusts = new HashMap<>();
                }
                this.nerocreatures$crusts.put(pos, state);
            }
        }
    }

    @Inject(method = "finalizeExplosion", at = @At("RETURN"))
    private void nerocreatures$restoreCrusts(boolean particles, CallbackInfo callback) {
        if (this.nerocreatures$crusts != null) {
            this.nerocreatures$crusts.forEach((pos, state) ->
                    EphemeralCrustBlock.restoreFluidAfterRemoval(this.level, pos, state));
            this.nerocreatures$crusts = null;
        }
    }
}
*///?}
