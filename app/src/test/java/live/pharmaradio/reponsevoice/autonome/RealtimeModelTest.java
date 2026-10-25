package live.pharmaradio.reponsevoice.autonome;

import org.junit.Test;
import static org.junit.Assert.*;

public class RealtimeModelTest {
    @Test
    public void modelIdsAreAnExplicitAllowlist() {
        assertEquals(2, RealtimeModel.values().length);
        assertEquals(RealtimeModel.MINI, RealtimeModel.find("gpt-realtime-2.1-mini"));
        assertEquals(RealtimeModel.FULL, RealtimeModel.find("gpt-realtime-2.1"));
        assertNull(RealtimeModel.find("gpt-live-1"));
        assertNull(RealtimeModel.find("unknown"));
        assertNull(RealtimeModel.find(null));
    }

    @Test
    public void audioMinuteEquivalentsAreNotCallDurationPrices() {
        assertEquals(0.0192, RealtimeModel.FULL.inputMinute(), 0.000000001);
        assertEquals(0.0768, RealtimeModel.FULL.outputMinute(), 0.000000001);
        assertEquals(0.048, RealtimeModel.FULL.balancedMinute(), 0.000000001);
        assertEquals(0.006, RealtimeModel.MINI.inputMinute(), 0.000000001);
        assertEquals(0.024, RealtimeModel.MINI.outputMinute(), 0.000000001);
        assertEquals(0.015, RealtimeModel.MINI.balancedMinute(), 0.000000001);
        assertTrue(RealtimeModel.MINI.minutePrices().contains("Audio neuf seul"));
        assertTrue(RealtimeModel.MINI.minutePrices().contains("30 s"));
    }

    @Test
    public void cachedAudioAndTextUseSeparateMiniRates() {
        assertEquals(0.000228, ApiUsage.estimate(RealtimeModel.MINI, 100, 20, 50, 10, 5, 4), 0.000000001);
        assertEquals(0.000920, ApiUsage.estimate(RealtimeModel.FULL, 100, 20, 50, 10, 5, 4), 0.000000001);
        assertEquals(0.000006, ApiUsage.estimate(RealtimeModel.MINI, 100, 0, 100, 0, 0, 0), 0.000000001);
        assertEquals(0.000030, ApiUsage.estimate(RealtimeModel.MINI, 0, 100, 0, 100, 0, 0), 0.000000001);
    }

    @Test
    public void unknownModelAndInvalidBreakdownCannotProduceAnEstimate() {
        assertNull(ApiUsage.estimate(null, 0, 0, 0, 0, 0, 0));
        for (RealtimeModel model : RealtimeModel.values()) {
            assertNull(ApiUsage.estimate(model, 1, 1, 2, 0, 0, 0));
            assertNull(ApiUsage.estimate(model, 1, 1, 0, 2, 0, 0));
            assertNull(ApiUsage.estimate(model, 0, 0, 0, 0, -1, 0));
            assertEquals(0.0, ApiUsage.estimate(model, 0, 0, 0, 0, 0, 0), 0.0);
        }
    }
}
