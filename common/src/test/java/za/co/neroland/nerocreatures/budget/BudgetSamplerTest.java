package za.co.neroland.nerocreatures.budget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BudgetSamplerTest {

    @Test
    void reportsMillisecondsPerTickPerKeyAndTotal() {
        BudgetSampler<String> sampler = new BudgetSampler<>();
        String moth = "nerocreatures:lumen_moth";
        String yak = "nerocreatures:rime_yak";
        for (int i = 0; i < 200; i++) {
            sampler.record(moth, 50_000L);   // 0.05 ms each, 2 per tick over 100 ticks
        }
        for (int i = 0; i < 100; i++) {
            sampler.record(yak, 300_000L);   // 0.3 ms each, 1 per tick
        }
        BudgetSampler.Report report = sampler.report(100, k -> k);
        assertEquals(100L, report.ticks());
        assertEquals(2, report.rows().size());
        assertEquals(yak, report.rows().get(0).name(), "most expensive first");
        assertEquals(0.3D, report.rows().get(0).msPerTick(), 1e-9);
        assertEquals(0.1D, report.rows().get(1).msPerTick(), 1e-9);
        assertEquals(2.0D, report.rows().get(1).callsPerTick(), 1e-9);
        assertEquals(0.4D, report.totalMsPerTick(), 1e-9);
        assertEquals(300L, report.totalCalls());
    }

    @Test
    void zeroTicksAndNegativeTimesAreSafe() {
        BudgetSampler<String> sampler = new BudgetSampler<>();
        assertTrue(sampler.isEmpty());
        sampler.record("a", -5L);
        assertFalse(sampler.isEmpty());
        BudgetSampler.Report report = sampler.report(0, k -> k);
        assertEquals(1L, report.ticks());
        assertEquals(0.0D, report.totalMsPerTick(), 0.0D);
        sampler.clear();
        assertTrue(sampler.report(10, k -> k).rows().isEmpty());
    }
}
