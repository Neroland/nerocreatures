package za.co.neroland.nerocreatures.bestiary;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** {@code nerocreatures:bestiary/complete}: open only once every catalogue species is Studied. */
class BestiaryCompleteTest {

    @Test
    void completeNeedsEverySpecies() {
        List<BestiaryCatalog.Entry> entries = BestiaryCatalog.entries();
        Set<String> studied = new HashSet<>();
        for (BestiaryCatalog.Entry entry : entries) {
            assertFalse(BestiaryProgress.allStudied(entries, studied::contains));
            studied.add(entry.id());
        }
        assertTrue(BestiaryProgress.allStudied(entries, studied::contains));
    }

    @Test
    void anEmptyCatalogueIsNeverComplete() {
        assertFalse(BestiaryProgress.allStudied(List.of(), id -> true));
    }

    @Test
    void newGatesAreShipped() throws Exception {
        for (String path : new String[] {"tamed/10", "tamed/25", "bestiary/complete"}) {
            try (InputStream in = BestiaryCompleteTest.class.getResourceAsStream(
                    "/data/nerocreatures/neroland_gates/" + path + ".json")) {
                assertNotNull(in, path);
            }
        }
    }
}
