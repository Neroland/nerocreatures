package za.co.neroland.nerocreatures.mixin;

//? if <1.20.5 {
/*import java.util.function.Consumer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import za.co.neroland.nerocreatures.registry.LegacyCreatureAttributes;

@Mixin(AttributeSupplier.class)
abstract class LegacyAttributeSupplierMixin {
    @Inject(method = "createInstance", at = @At("RETURN"), cancellable = true)
    private void nerocreatures$createMissing(Consumer<AttributeInstance> changed, Attribute attribute,
            CallbackInfoReturnable<AttributeInstance> result) {
        if (result.getReturnValue() == null && LegacyCreatureAttributes.supplies(attribute)) {
            result.setReturnValue(new AttributeInstance(attribute, changed));
        }
    }

    @Inject(method = {"getBaseValue", "getValue"}, at = @At("HEAD"), cancellable = true)
    private void nerocreatures$defaultValue(Attribute attribute, CallbackInfoReturnable<Double> result) {
        if (LegacyCreatureAttributes.supplies(attribute) && !this.instances.containsKey(attribute)) {
            result.setReturnValue(attribute.getDefaultValue());
        }
    }

    @Inject(method = "hasAttribute", at = @At("HEAD"), cancellable = true)
    private void nerocreatures$hasAttribute(Attribute attribute, CallbackInfoReturnable<Boolean> result) {
        if (LegacyCreatureAttributes.supplies(attribute)) {
            result.setReturnValue(true);
        }
    }

    @org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final
    private java.util.Map<Attribute, AttributeInstance> instances;
}
*///?}
