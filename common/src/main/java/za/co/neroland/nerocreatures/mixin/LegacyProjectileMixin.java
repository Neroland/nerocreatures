package za.co.neroland.nerocreatures.mixin;

//? if <1.20.5 {
/*import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import za.co.neroland.nerocreatures.entity.nyxara.Shardback;

@Mixin(Projectile.class)
abstract class LegacyProjectileMixin {
    @Inject(method = "onHit", at = @At("HEAD"), cancellable = true)
    private void nerocreatures$reflectShardbackHit(HitResult hit, CallbackInfo callback) {
        Projectile self = (Projectile) (Object) this;
        if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof Shardback shardback
                && shardback.reflectProjectile(self)) {
            self.setDeltaMovement(self.getDeltaMovement().scale(-1.0D));
            self.setYRot(self.getYRot() + 180.0F);
            callback.cancel();
        }
    }
}
*///?}
