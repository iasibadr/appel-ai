package live.pharmaradio.reponsevoice.autonome;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class MissionDossierTest {
    @Test
    public void snapshotIsImmutableAndPrivateFlagIsPreserved() {
        List<MissionDocument> source = new ArrayList<>();
        source.add(new MissionDocument("Budget", "Plafond indicatif privé", false));
        MissionDossier dossier = new MissionDossier(source);
        source.clear();
        assertEquals(1, dossier.documents.size());
        assertFalse(dossier.documents.get(0).shareable);
        assertThrows(UnsupportedOperationException.class, () -> dossier.documents.clear());
        MissionDossier updated = dossier.add(new MissionDocument("Horaires", "9 h à 18 h", true));
        assertEquals(1, dossier.documents.size());
        assertEquals(2, updated.documents.size());
        assertEquals(1, updated.remove(0).documents.size());
    }

    @Test
    public void boundsAndInvalidDocumentsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new MissionDocument(" ", "Texte", false));
        assertThrows(IllegalArgumentException.class, () -> new MissionDocument("Titre\n", "Texte", false));
        assertThrows(IllegalArgumentException.class, () -> new MissionDocument("Titre", " ", false));
        assertThrows(IllegalArgumentException.class, () -> new MissionDocument("Titre", "a".repeat(6001), false));
        assertThrows(IllegalArgumentException.class, () -> new MissionDocument("Titre", "binaire\0", false));
        MissionDocument document = new MissionDocument("Titre", "a".repeat(5999), false);
        assertThrows(IllegalArgumentException.class, () -> new MissionDossier(List.of(document, document)));
        assertThrows(IllegalArgumentException.class, () -> new MissionDossier(java.util.Collections.nCopies(6, new MissionDocument("T", "x", false))));
    }

    @Test
    public void dossierRequiresSameDestinationAndOneConfirmedCall() {
        CallAuthorization authorization = new CallAuthorization();
        MissionDossier dossier = MissionDossier.empty().add(new MissionDocument("Renseignement", "Donnée synthétique", true));
        authorization.arm("+15555550123", "Question", dossier, 100);
        assertNull(authorization.consumePrepared("+15555550124", 101));
        PreparedMission mission = authorization.consumePrepared("+15555550123", 102);
        assertEquals("Question", mission.brief);
        assertSame(dossier, mission.dossier);
        assertNull(authorization.consumePrepared("+15555550123", 103));
        authorization.arm("+15555550123", "Question", dossier, 1000);
        assertNull(authorization.consumePrepared("+15555550123", 91000));
    }

    @Test
    public void failedRearmingDoesNotLeavePreviousDocumentsAuthorized() {
        CallAuthorization authorization = new CallAuthorization();
        authorization.arm("+15555550123", "Question", MissionDossier.empty(), 100);
        assertThrows(IllegalArgumentException.class, () -> authorization.arm("+15555550123", "Question", null, 101));
        assertNull(authorization.consumePrepared("+15555550123", 102));
    }
}
