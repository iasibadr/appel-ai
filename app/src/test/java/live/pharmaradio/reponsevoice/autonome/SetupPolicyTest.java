package live.pharmaradio.reponsevoice.autonome;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class SetupPolicyTest {
    @Test
    public void completionRequiresAllThreeChecks() {
        for (int mask = 0; mask < 8; mask++) {
            assertEquals(mask == 7, SetupPolicy.canFinish((mask & 1) != 0, (mask & 2) != 0, (mask & 4) != 0));
        }
    }

    @Test
    public void existingAdbHelperDoesNotRequireShizuku() {
        assertEquals("Service audio déjà démarré", SetupPolicy.activationStatus(true, false, false));
    }

    @Test
    public void unavailableOrUnauthorizedShizukuIsNotShownAsActivated() {
        assertEquals("Démarrez Shizuku pour activer le service audio", SetupPolicy.activationStatus(false, false, false));
        assertEquals("Autorisez Appel AI dans Shizuku", SetupPolicy.activationStatus(false, true, false));
        assertEquals("Prêt à activer le service audio", SetupPolicy.activationStatus(false, true, true));
    }
}
