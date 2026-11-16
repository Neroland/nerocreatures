package za.co.neroland.nerocreatures.bestiary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import za.co.neroland.nerocreatures.bestiary.BestiaryCatalog.Entry;
import za.co.neroland.nerocreatures.bestiary.BestiaryRules.Tier;
import za.co.neroland.nerocreatures.network.BestiaryCountsPayload;

/** Two players: what A is sent depends only on A's own data, and unscanned species are omitted. */
class BestiaryCountsIsolationTest {

    private record Data(Map<String, Tier> tiers, Map<String, Long> kills, Map<String, Integer> owned)
            implements BestiaryProgress.PlayerData {
        @Override
        public Tier tier(Entry entry) {
            return this.tiers.getOrDefault(entry.id(), Tier.NONE);
        }

        @Override
        public long kills(Entry entry) {
            return this.kills.getOrDefault(entry.id(), 0L);
        }

        @Override
        public int owned(Entry entry) {
            return this.owned.getOrDefault(entry.id(), 0);
        }
    }

    private static byte[] bytes(BestiaryCountsPayload payload) {
        ByteBuf buf = Unpooled.buffer();
        try {
            BestiaryCountsPayload.STREAM_CODEC.encode(buf, payload);
            byte[] out = new byte[buf.readableBytes()];
            buf.readBytes(out);
            return out;
        } finally {
            buf.release();
        }
    }

    @Test
    void aPlayersAnswerNeverDependsOnAnotherPlayer() {
        Data a = new Data(new HashMap<>(Map.of("lumen_moth", Tier.SCANNED, "echo_shrike", Tier.STUDIED)),
                new HashMap<>(Map.of("echo_shrike", 6L)), new HashMap<>(Map.of("lumen_moth", 2)));
        Data b = new Data(new HashMap<>(Map.of("dust_devil", Tier.SCANNED)), new HashMap<>(Map.of("dust_devil", 99L)),
                new HashMap<>());
        byte[] before = bytes(BestiaryProgress.buildCounts(a, BestiaryProgress::goal));
        b.tiers().put("rime_yak", Tier.STUDIED);
        b.kills().put("echo_shrike", 1_000L);
        b.owned().put("lumen_moth", 9);
        byte[] after = bytes(BestiaryProgress.buildCounts(a, BestiaryProgress::goal));
        assertTrue(java.util.Arrays.equals(before, after));
        BestiaryCountsPayload payload = BestiaryProgress.buildCounts(a, BestiaryProgress::goal);
        assertEquals(2, payload.entries().size(), "only scanned species are sent");
        BestiaryCountsPayload.Entry moth = payload.entries().stream().filter(e -> e.species().equals("lumen_moth"))
                .findFirst().orElseThrow();
        assertEquals(2, moth.owned());
        assertEquals(BestiaryCountsPayload.TRACK_KILLS | BestiaryCountsPayload.TRACK_OWNED, moth.tracked());
        BestiaryCountsPayload.Entry shrike = payload.entries().stream().filter(e -> e.species().equals("echo_shrike"))
                .findFirst().orElseThrow();
        assertEquals(BestiaryCountsPayload.TRACK_KILLS, shrike.tracked());
        assertEquals(shrike.studyGoal(), shrike.studyProgress());
    }

    @Test
    void nothingScannedSendsNothing() {
        Data none = new Data(Map.of(), Map.of(), Map.of());
        assertEquals(0, BestiaryProgress.buildCounts(none, BestiaryProgress::goal).entries().size());
    }
}
