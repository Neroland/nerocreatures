package za.co.neroland.nerocreatures.mixin;

//? if <1.20.5 {
/*import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import za.co.neroland.nerocreatures.registry.LegacyCreatureAttributes;

@Mixin(LivingEntity.class)
abstract class LegacyFabricGravityMixin {
    @ModifyConstant(method = "travel", constant = @Constant(doubleValue = 0.08D))
    private double nerocreatures$verticalGravity(double vanillaGravity) {
        return ((LivingEntity) (Object) this).getAttributeValue(LegacyCreatureAttributes.VANILLA_GRAVITY);
    }
}
*///?}
