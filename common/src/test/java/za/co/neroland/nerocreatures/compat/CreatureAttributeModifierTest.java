package za.co.neroland.nerocreatures.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import org.junit.jupiter.api.Test;

class CreatureAttributeModifierTest {
    @Test
    void changingMagnitudeKeepsTheModifierIdentity() {
        var id = Identifier.fromNamespaceAndPath("nerocreatures", "test_modifier");
        var other = Identifier.fromNamespaceAndPath("nerocreatures", "other_test_modifier");
        var operation = AttributeModifier.Operation.ADD_VALUE;

        var first = CreatureAttributeModifier.create(id, 1.0, operation);
        var updated = CreatureAttributeModifier.create(id, 2.0, operation);
        var separate = CreatureAttributeModifier.create(other, 2.0, operation);

        assertEquals(first.id(), updated.id());
        assertNotEquals(first.id(), separate.id());
    }
}
