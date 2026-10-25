package live.pharmaradio.reponsevoice.autonome;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ApiUsageTest {
    @Test
    public void countsTextAudioAndCacheAtDifferentRates() {
        assertEquals(0.000920, ApiUsage.estimate(RealtimeModel.FULL, 100, 20, 50, 10, 5, 4), 0.000000001);
    }

    @Test
    public void invalidCacheCannotProduceCost() {
        assertNull(ApiUsage.estimate(RealtimeModel.FULL, 10, 5, 11, 0, 0, 0));
        assertNull(ApiUsage.estimate(RealtimeModel.FULL, 10, 5, 0, 6, 0, 0));
        assertNull(ApiUsage.estimate(RealtimeModel.FULL, -1, 0, 0, 0, 0, 0));
    }

    @Test
    public void responsesAreAccumulatedWithoutDoubleCounting() {
        ApiUsage usage = new ApiUsage();
        usage.record("first", 100, 20, 0.01);
        usage.record("first", 100, 20, 0.01);
        usage.record("second", 200, 30, 0.02);
        assertTrue(usage.summary().contains("entrée 300, sortie 50"));
        assertTrue(usage.summary().contains("0,030000 USD"));
    }

    @Test
    public void missingBreakdownDoesNotInventTotalCost() {
        ApiUsage usage = new ApiUsage();
        usage.record("first", 100, 20, null);
        usage.record("second", 200, 30, 0.02);
        assertTrue(usage.summary().contains("coût total non calculable"));
        assertTrue(usage.summary().contains("entrée 300, sortie 50"));
    }

    @Test
    public void noReportIsNotZeroCost() {
        assertTrue(new ApiUsage().summary().contains("ne prouve pas un coût nul"));
    }

    @Test
    public void interruptionWithUnreportedResponseIsPartial() {
        ApiUsage usage = new ApiUsage();
        usage.record("finished", 100, 20, 0.01);
        usage.started("pending");
        assertTrue(usage.summary().contains("coût total non calculable"));
        usage.record("pending", 200, 30, 0.02);
        assertTrue(usage.summary().contains("0,030000 USD"));
    }

    @Test
    public void budgetAlertOnlyUsesACompleteKnownEstimate() {
        ApiUsage usage = new ApiUsage();
        assertNull(usage.estimatedTotal());
        usage.started("first");
        assertNull(usage.estimatedTotal());
        usage.record("first", 100, 20, 0.2);
        assertEquals(0.2, usage.estimatedTotal(), 0.000001);
        usage.started("second");
        assertNull(usage.estimatedTotal());
    }
}
