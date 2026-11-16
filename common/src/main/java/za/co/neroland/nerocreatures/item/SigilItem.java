package za.co.neroland.nerocreatures.item;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.block.ApexAltarBlock;
import za.co.neroland.nerocreatures.entity.boss.BloomSovereign;
import za.co.neroland.nerocreatures.entity.boss.CinderTyrant;
import za.co.neroland.nerocreatures.entity.boss.FacetColossus;
import za.co.neroland.nerocreatures.entity.boss.NeroBossEntity;
import za.co.neroland.nerocreatures.entity.boss.RimeMatriarch;

/**
 * A boss sigil (one per boss). Used on an Apex Altar, it summons that boss as a
 * contribution-tracked fight; the altar consumes it exactly once, and only when the fight started.
 * On its own the item does nothing.
 */
public class SigilItem extends Item {

    /** Summons one boss as a tracked fight at a position, recording the optional initiator. */
    @FunctionalInterface
    public interface Summoner {
        Optional<NeroBossEntity> summon(ServerLevel level, BlockPos pos, @Nullable UUID initiator);
    }

    /** The four sigils, the boss each one calls and the altar glow it lights. */
    public enum Kind {
        CINDER_TYRANT("cinder_tyrant", ApexAltarBlock.Sigil.CINDARA, CinderTyrant::summon),
        RIME_MATRIARCH("rime_matriarch", ApexAltarBlock.Sigil.GLACIRA, RimeMatriarch::summon),
        BLOOM_SOVEREIGN("bloom_sovereign", ApexAltarBlock.Sigil.GREENXERTZ, BloomSovereign::summon),
        FACET_COLOSSUS("facet_colossus", ApexAltarBlock.Sigil.NYXARA, FacetColossus::summon);

        private final String boss;
        private final ApexAltarBlock.Sigil glow;
        private final Summoner summoner;

        Kind(String boss, ApexAltarBlock.Sigil glow, Summoner summoner) {
            this.boss = boss;
            this.glow = glow;
            this.summoner = summoner;
        }

        /** The boss id ({@code nerocreatures:<boss>}). */
        public Identifier bossId() {
            return id(this.boss);
        }

        /** The sigil item id ({@code <boss>_sigil}). */
        public String itemPath() {
            return this.boss + "_sigil";
        }

        public ApexAltarBlock.Sigil glow() {
            return this.glow;
        }

        public Summoner summoner() {
            return this.summoner;
        }

        /** The kind whose altar glow is {@code glow}, or null for none. */
        @Nullable
        public static Kind forGlow(ApexAltarBlock.Sigil glow) {
            for (Kind kind : values()) {
                if (kind.glow == glow) {
                    return kind;
                }
            }
            return null;
        }
    }

    private final Kind kind;

    public SigilItem(Item.Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
    }

    public Kind kind() {
        return this.kind;
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, path);
    }
}
