package za.co.neroland.nerocreatures.breeding;

import java.util.Locale;

import com.mojang.serialization.Codec;

/** How a species reproduces. Lenient codec: anything unrecognised reads as {@link #LIVE}. */
public enum BirthMode {

    /** The vanilla {@code spawnChildFromBreeding} path: a baby appears beside the parents. */
    LIVE,

    /** Breeding drops a {@code <species>_egg} carrying the rolled traits; it hatches in an Incubator. */
    EGG;

    public static final Codec<BirthMode> CODEC = Codec.STRING.xmap(BirthMode::fromKey, BirthMode::key);

    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public static BirthMode fromKey(String key) {
        return "egg".equalsIgnoreCase(key) ? EGG : LIVE;
    }
}
