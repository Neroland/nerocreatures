package za.co.neroland.nerocreatures.registry;

//? if <1.20.5 {
/*import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

public final class LegacyCreatureDimensions {
    private static final Map<EntityType<?>, Float> EYE_HEIGHTS = new IdentityHashMap<>();

    private LegacyCreatureDimensions() {}

    public static <T extends Entity> EntityType<T> withEyeHeight(EntityType<T> type, float height) {
        EYE_HEIGHTS.put(type, height);
        return type;
    }

    @Nullable
    public static Float eyeHeight(EntityType<?> type) {
        return EYE_HEIGHTS.get(type);
    }
}
*///?}
