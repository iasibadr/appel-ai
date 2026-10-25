package live.pharmaradio.reponsevoice.autonome;

import org.junit.Test;
import static org.junit.Assert.*;

public class SetupFlowTest {
    @Test
    public void missingKeyAlwaysComesFirst() {
        assertEquals(SetupFlow.Stage.KEY, SetupFlow.next(false, true, true, true, true, true, true, true, true));
    }

    @Test
    public void existingAudioSkipsAllShizukuAndAndroidSteps() {
        assertEquals(SetupFlow.Stage.VERIFY, SetupFlow.next(true, true, false, false, false, false, false, false, false));
        assertEquals(SetupFlow.Stage.READY, SetupFlow.next(true, true, true, false, false, false, false, false, false));
    }

    @Test
    public void firstInstallationShowsOnlyTheNextMissingAction() {
        assertEquals(SetupFlow.Stage.INSTALL, SetupFlow.next(true, false, false, false, false, false, false, false, false));
        assertEquals(SetupFlow.Stage.DEVELOPER, SetupFlow.next(true, false, false, true, false, false, false, false, false));
        assertEquals(SetupFlow.Stage.WIRELESS, SetupFlow.next(true, false, false, true, false, false, false, true, false));
        assertEquals(SetupFlow.Stage.START, SetupFlow.next(true, false, false, true, false, false, false, true, true));
    }

    @Test
    public void runningShizukuDoesNotRequireWifiSettingsAgain() {
        assertEquals(SetupFlow.Stage.WRONG_MODE, SetupFlow.next(true, false, false, true, true, false, true, false, false));
        assertEquals(SetupFlow.Stage.AUTHORIZE, SetupFlow.next(true, false, false, true, true, true, false, false, false));
        assertEquals(SetupFlow.Stage.ACTIVATE, SetupFlow.next(true, false, false, true, true, true, true, false, false));
    }

    @Test
    public void automaticActionsRequireConsentAndNeverRetryOrOpenSettings() {
        for (SetupFlow.Stage stage : SetupFlow.Stage.values()) {
            boolean operation = stage == SetupFlow.Stage.ACTIVATE || stage == SetupFlow.Stage.VERIFY;
            assertEquals(operation, SetupFlow.automatic(stage, true, false, false));
            assertFalse(SetupFlow.automatic(stage, false, false, false));
            assertFalse(SetupFlow.automatic(stage, true, true, false));
            assertFalse(SetupFlow.automatic(stage, true, false, true));
        }
    }

    @Test
    public void readyRequiresKeyAndCurrentAudioEvenIfDiagnosticWasSuccessful() {
        for (int mask = 0; mask < 512; mask++) {
            boolean key = (mask & 1) != 0;
            boolean helper = (mask & 2) != 0;
            boolean diagnostic = (mask & 4) != 0;
            SetupFlow.Stage stage = SetupFlow.next(key, helper, diagnostic, (mask & 8) != 0,
                    (mask & 16) != 0, (mask & 32) != 0, (mask & 64) != 0, (mask & 128) != 0, (mask & 256) != 0);
            assertEquals(key && helper && diagnostic, stage == SetupFlow.Stage.READY);
        }
    }

    @Test
    public void quotaFailuresHaveAnActionableExplanation() {
        assertTrue(SetupFlow.diagnosticMessage("Erreur : credit_balance_exhausted").contains("facturation"));
        assertTrue(SetupFlow.diagnosticMessage("insufficient_quota").contains("ChatGPT"));
        assertFalse(SetupFlow.diagnosticMessage(null).isEmpty());
        assertEquals("Erreur réseau", SetupFlow.diagnosticMessage("Erreur réseau"));
    }
}
