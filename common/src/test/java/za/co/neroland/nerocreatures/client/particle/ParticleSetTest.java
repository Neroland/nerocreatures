package za.co.neroland.nerocreatures.client.particle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;

import org.junit.jupiter.api.Test;

/** The particle cap is 16: every type has a tuned style and a definition. */
class ParticleSetTest {

    @Test
    void sixteenStyledTypesEachWithADefinition() throws Exception {
        assertEquals(16, ParticleStyle.ids().size());
        for (String id : ParticleStyle.ids()) {
            try (InputStream in = ParticleSetTest.class.getResourceAsStream("/assets/nerocreatures/particles/" + id + ".json")) {
                assertNotNull(in, id);
            }
            ParticleStyle style = ParticleStyle.of(id);
            assertTrue(style.lifeMin() > 0 && style.lifeMax() >= style.lifeMin(), id);
        }
    }
}
