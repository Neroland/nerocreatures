package za.co.neroland.nerocreatures.mixin;

//? if <1.20.5 {
/*import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import za.co.neroland.nerocreatures.registry.LegacyCreatureAttributes;

@Mixin(LivingEntity.class)
abstract class LegacyLivingEntityMixin {
    @Unique
    private float nerocreatures$appliedScale = 1.0F;

    @ModifyConstant(method = "calculateFallDamage", constant = @Constant(floatValue = 3.0F))
    private float nerocreatures$fallDistance(float vanillaDistance) {
        return (float) ((LivingEntity) (Object) this).getAttributeValue(LegacyCreatureAttributes.SAFE_FALL_DISTANCE);
    }

    @Inject(method = "getScale", at = @At("RETURN"), cancellable = true)
    private void nerocreatures$scale(CallbackInfoReturnable<Float> result) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.getAttributes() != null) {
            float scale = (float) self.getAttributeValue(LegacyCreatureAttributes.SCALE);
            if (scale != 1.0F) {
                result.setReturnValue(result.getReturnValueF() * scale);
            }
        }
    }

    @Inject(method = "getStandingEyeHeight", at = @At("HEAD"), cancellable = true)
    private void nerocreatures$eyeHeight(Pose pose, EntityDimensions dimensions, CallbackInfoReturnable<Float> result) {
        LivingEntity self = (LivingEntity) (Object) this;
        Float eyeHeight = za.co.neroland.nerocreatures.registry.LegacyCreatureDimensions.eyeHeight(self.getType());
        if (eyeHeight != null) {
            result.setReturnValue(eyeHeight * dimensions.height / self.getType().getDimensions().height);
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void nerocreatures$refreshScale(CallbackInfo callback) {
        LivingEntity self = (LivingEntity) (Object) this;
        float scale = (float) self.getAttributeValue(LegacyCreatureAttributes.SCALE);
        if (scale != this.nerocreatures$appliedScale) {
            this.nerocreatures$appliedScale = scale;
            self.refreshDimensions();
        }
    }
}
*///?}
