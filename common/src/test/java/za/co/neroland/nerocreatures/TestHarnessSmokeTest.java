package za.co.neroland.nerocreatures;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Smoke test: proves the shared test source set compiles and runs on the test cells. */
class TestHarnessSmokeTest {

    @Test
    void modIdIsStable() {
        assertEquals("nerocreatures", NeroCreaturesCommon.MOD_ID);
    }
}
