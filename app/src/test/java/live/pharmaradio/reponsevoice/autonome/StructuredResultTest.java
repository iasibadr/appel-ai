package live.pharmaradio.reponsevoice.autonome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class StructuredResultTest {
    @Test
    public void rendersThreeSectionsAndDoesNotExecuteTheSuggestion() {
        StructuredResult result = new StructuredResult(List.of("Horaire annoncé : 9 h"), List.of("Tarif non confirmé"), "Demander un devis écrit.");
        assertTrue(result.render().contains("Informations obtenues"));
        assertTrue(result.render().contains("Points non résolus"));
        assertTrue(result.render().contains("proposée (non exécutée)"));
        assertTrue(result.render().length() <= 1500);
    }

    @Test
    public void missingFactsAreNotReplacedWithInventedAnswers() {
        StructuredResult result = new StructuredResult(List.of(), List.of(), "Aucune action proposée.");
        assertTrue(result.render().contains("Non renseigné."));
    }

    @Test
    public void fieldsAndTotalAreBounded() {
        assertThrows(IllegalArgumentException.class, () -> new StructuredResult(null, List.of(), "Aucune"));
        assertThrows(IllegalArgumentException.class, () -> new StructuredResult(Collections.nCopies(6, "Point"), List.of(), "Aucune"));
        assertThrows(IllegalArgumentException.class, () -> new StructuredResult(List.of(" "), List.of(), "Aucune"));
        assertThrows(IllegalArgumentException.class, () -> new StructuredResult(List.of("a".repeat(241)), List.of(), "Aucune"));
        assertThrows(IllegalArgumentException.class, () -> new StructuredResult(List.of(), List.of(), " "));
        assertThrows(IllegalArgumentException.class, () -> new StructuredResult(List.of(), List.of(), "a".repeat(301)));
        assertThrows(IllegalArgumentException.class, () -> new StructuredResult(Collections.nCopies(5, "a".repeat(240)), List.of("b".repeat(240)), "c".repeat(300)));
    }

    @Test
    public void storedResultCannotOutliveMessageConsentRetention() {
        StructuredResult result = new StructuredResult(List.of("Fait rapporté"), List.of("À vérifier"), "Vérifier personnellement.");
        MessageHistory.Entry entry = new MessageHistory.Entry("call:tool", "tel:test", "Fictif", "", result.render(), "outgoing", 1000, result);
        MessageHistory history = new MessageHistory(new ArrayList<>(), 1000);
        history.add(entry, 1000);
        assertSame(result, history.all(1000).get(0).result);
        assertTrue(history.all(1000 + MessageHistory.RETENTION_MS).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new MessageHistory.Entry("call:tool", "tel:test", "", "", "Autre texte", "outgoing", 1000, result));
    }

    @Test
    public void reusedIdCannotChangeStructuredFieldsWithIdenticalRenderedText() {
        StructuredResult first = new StructuredResult(List.of("A\n• B"), List.of(), "Aucune");
        StructuredResult second = new StructuredResult(List.of("A", "B"), List.of(), "Aucune");
        assertEquals(first.render(), second.render());
        MessageHistory history = new MessageHistory(new ArrayList<>(), 1000);
        history.add(new MessageHistory.Entry("call:tool", "tel:test", "", "", first.render(), "outgoing", 1000, first), 1000);
        assertThrows(IllegalArgumentException.class, () -> history.add(new MessageHistory.Entry("call:tool", "tel:test", "", "", second.render(), "outgoing", 1000, second), 1000));
    }
}
