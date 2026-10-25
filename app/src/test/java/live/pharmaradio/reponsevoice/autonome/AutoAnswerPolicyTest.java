package live.pharmaradio.reponsevoice.autonome;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AutoAnswerPolicyTest {
    @Test
    public void onlyExplicitlyEnabledExclusiveVisibleNonEmergencyCallIsAllowed() {
        assertTrue(AutoAnswerPolicy.allowed(true, true, true, true, false));
        assertFalse(AutoAnswerPolicy.allowed(false, true, true, true, false));
        assertFalse(AutoAnswerPolicy.allowed(true, false, true, true, false));
        assertFalse(AutoAnswerPolicy.allowed(true, true, false, true, false));
        assertFalse(AutoAnswerPolicy.allowed(true, true, true, false, false));
        assertFalse(AutoAnswerPolicy.allowed(true, true, true, true, true));
    }
}
