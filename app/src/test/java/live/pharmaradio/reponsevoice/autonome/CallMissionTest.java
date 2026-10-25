package live.pharmaradio.reponsevoice.autonome;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class CallMissionTest {
    @Test
    public void emptyOrOversizedMissionIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> CallMission.build(" ", "", "", "", true));
        assertThrows(IllegalArgumentException.class, () -> CallMission.build("x".repeat(4000), "", "", "", true));
    }

    @Test
    public void structuredMissionIncludesLimitsAndOptionalConsentBasedReport() {
        String mission = CallMission.build("Horaires", "Samedi ?", "Pas de rendez-vous", "Si décision nécessaire", true);
        assertTrue(mission.contains("Horaires"));
        assertTrue(mission.contains("Samedi ?"));
        assertTrue(mission.contains("Pas de rendez-vous"));
        assertTrue(mission.contains("Si décision nécessaire"));
        assertTrue(mission.contains("attendre l’accord explicite"));
        assertFalse(CallMission.build("Horaires", "", "", "", false).contains("bilan"));
    }
}
