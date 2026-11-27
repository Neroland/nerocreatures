package za.co.neroland.nerocreatures.registry;

//? if <1.20.5 {
/*import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.platform.Services;
import za.co.neroland.nerolandcore.registry.RegistrationProvider;

public final class LegacyCreatureAttributes {
    public static final Attribute SCALE = new CreatureAttribute("scale", 1.0D, 0.0625D, 16.0D);
    public static final Attribute SAFE_FALL_DISTANCE = new CreatureAttribute("safe_fall_distance", 3.0D, 0.0D, 1024.0D);
    public static final Attribute VANILLA_GRAVITY = new CreatureAttribute("gravity", 0.08D, -1.0D, 1.0D);

    private LegacyCreatureAttributes() {}

    public static void init() {
        RegistrationProvider<Attribute> attributes =
                RegistrationProvider.get(Registries.ATTRIBUTE, NeroCreaturesCommon.MOD_ID);
        attributes.register("scale", key -> SCALE);
        attributes.register("safe_fall_distance", key -> SAFE_FALL_DISTANCE);
        if (Services.PLATFORM.usesVanillaGravity()) {
            attributes.register("gravity", key -> VANILLA_GRAVITY);
        }
    }

    public static Attribute gravity() {
        return Services.PLATFORM.gravityAttribute();
    }

    public static boolean supplies(Attribute attribute) {
        return attribute instanceof CreatureAttribute;
    }

    private static final class CreatureAttribute extends RangedAttribute {
        private CreatureAttribute(String name, double initialValue, double minimum, double maximum) {
            super("attribute.nerocreatures." + name, initialValue, minimum, maximum);
            this.setSyncable(true);
        }
    }
}
*///?}
