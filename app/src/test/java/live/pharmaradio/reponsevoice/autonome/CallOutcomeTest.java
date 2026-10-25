package live.pharmaradio.reponsevoice.autonome;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CallOutcomeTest {
    @Test
    public void preparationIsNotACompletedCall() {
        CallOutcome outcome = new CallOutcome();
        assertEquals("PREPARING", outcome.state());
        assertFalse(outcome.terminal());
        outcome.active();
        assertEquals("ACTIVE", outcome.state());
    }

    @Test
    public void laterPhoneDisconnectDoesNotEraseFailure() {
        CallOutcome outcome = new CallOutcome();
        outcome.active();
        outcome.finish("Transport interrompu", true, false);
        outcome.finish("Appel terminé", false, false);
        outcome.active();
        assertEquals("INTERRUPTED", outcome.state());
        assertEquals("Transport interrompu", outcome.reason());
        assertTrue(outcome.terminal());
    }

    @Test
    public void handoffIsExplicitAndNotInferredFromWording() {
        CallOutcome outcome = new CallOutcome();
        outcome.finish("Vous parlez directement au correspondant.", false, true);
        assertEquals("HANDOFF", outcome.state());
        CallOutcome normal = new CallOutcome();
        normal.finish("Fin après adieux", false, false);
        assertEquals("FINISHED", normal.state());
    }
}
