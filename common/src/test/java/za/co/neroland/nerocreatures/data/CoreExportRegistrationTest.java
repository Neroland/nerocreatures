package za.co.neroland.nerocreatures.data;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.data.PlayerDataExport;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;

/**
 * NeroCreatures registers its data export with Core next to its eraser, so one
 * {@code /neroland data exportme} covers the same records {@code /neroland data eraseme} purges.
 */
class CoreExportRegistrationTest {

    @AfterEach
    void tearDown() {
        PlayerDataExport.clearForTests();
    }

    @Test
    void initRegistersTheExporterUnderTheModId() {
        CreatureData.init();
        assertTrue(PlayerDataExport.registeredIds().contains(NeroCreaturesCommon.MOD_ID));
    }
}
