package za.co.neroland.nerocreatures.compat;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

public final class CreatureAttributeModifier {
    private CreatureAttributeModifier() { }

    public static AttributeModifier create(Identifier id, double amount, AttributeModifier.Operation operation) {
        //? if >=1.21 {
        return new AttributeModifier(id, amount, operation);
        //?} else {
        /*return new AttributeModifier(uuid(id), id.toString(), amount, operation);
        *///?}
    }

    public static boolean has(AttributeInstance instance, Identifier id) {
        //? if >=1.21 {
        return instance.hasModifier(id);
        //?} else {
        /*return instance.getModifier(uuid(id)) != null;
        *///?}
    }

    public static void remove(AttributeInstance instance, Identifier id) {
        //? if >=1.21 {
        instance.removeModifier(id);
        //?} else {
        /*instance.removeModifier(uuid(id));
        *///?}
    }

    public static void replacePermanent(AttributeInstance instance, Identifier id, double amount,
            AttributeModifier.Operation operation) {
        //? if >=1.21 {
        instance.addOrReplacePermanentModifier(create(id, amount, operation));
        //?} else {
        /*remove(instance, id);
        instance.addPermanentModifier(create(id, amount, operation));
        *///?}
    }

    //? if <1.21 {
    /*private static java.util.UUID uuid(Identifier id) {
        return java.util.UUID.nameUUIDFromBytes(id.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    *///?}
}
