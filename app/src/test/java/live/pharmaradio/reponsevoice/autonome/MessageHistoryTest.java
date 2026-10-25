package live.pharmaradio.reponsevoice.autonome;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class MessageHistoryTest {
    private MessageHistory.Entry entry(String id, String contact, String text, long created) {
        return new MessageHistory.Entry(id, contact, "Contact fictif", "+15555550123", text, "incoming", created);
    }

    @Test
    public void messagesSurviveReconstructionAndRemainSeparated() {
        MessageHistory history = new MessageHistory(new ArrayList<>(), 1000);
        history.add(entry("one", "tel:first", "Premier message", 1000), 1000);
        history.add(entry("two", "tel:second", "Autre message", 1001), 1001);
        MessageHistory restored = new MessageHistory(history.all(1001), 1002);
        assertEquals(2, restored.all(1002).size());
        assertEquals("tel:second", restored.all(1002).get(0).contactKey);
        assertEquals("tel:first", restored.all(1002).get(1).contactKey);
    }

    @Test
    public void expiryIsEnforcedAtTwentyFourHours() {
        MessageHistory history = new MessageHistory(new ArrayList<>(), 1000);
        history.add(entry("one", "tel:first", "Message", 1000), 1000);
        assertEquals(1, history.all(1000 + MessageHistory.RETENTION_MS - 1).size());
        assertTrue(history.all(1000 + MessageHistory.RETENTION_MS).isEmpty());
    }

    @Test
    public void expiredMessagesAreNotReloaded() {
        List<MessageHistory.Entry> saved = new ArrayList<>();
        saved.add(entry("old", "tel:first", "Ancien", 1000));
        saved.add(entry("new", "tel:first", "Nouveau", 2000));
        MessageHistory restored = new MessageHistory(saved, 1000 + MessageHistory.RETENTION_MS);
        assertEquals("new", restored.all(1000 + MessageHistory.RETENTION_MS).get(0).id);
        assertEquals(1, restored.all(1000 + MessageHistory.RETENTION_MS).size());
    }

    @Test
    public void repeatedToolDoesNotDuplicateMessage() {
        MessageHistory history = new MessageHistory(new ArrayList<>(), 1000);
        history.add(entry("same", "tel:first", "Message", 1000), 1000);
        history.add(entry("same", "tel:first", "Message", 1001), 1001);
        assertEquals(1, history.all(1001).size());
        assertEquals(1000, history.all(1001).get(0).created);
        assertThrows(IllegalArgumentException.class, () -> history.add(entry("same", "tel:first", "Autre contenu", 1001), 1001));
    }

    @Test
    public void deletionDoesNotAffectOtherCorrespondent() {
        MessageHistory history = new MessageHistory(new ArrayList<>(), 1000);
        history.add(entry("one", "tel:first", "Message", 1000), 1000);
        history.add(entry("two", "tel:second", "Autre", 1000), 1000);
        history.remove("one");
        assertEquals("two", history.all(1000).get(0).id);
        assertEquals(1, history.all(1000).size());
    }

    @Test
    public void fullMailboxDoesNotSilentlyDropOlderMessages() {
        MessageHistory history = new MessageHistory(new ArrayList<>(), 1000);
        for (int index = 0; index < MessageHistory.MAX_MESSAGES; index++) {
            history.add(entry("message-" + index, "tel:first", "Message", 1000), 1000);
        }
        assertThrows(IllegalStateException.class, () -> history.add(entry("overflow", "tel:second", "Autre", 1000), 1000));
        assertEquals(MessageHistory.MAX_MESSAGES, history.all(1000).size());
    }

    @Test
    public void invalidAndEmptyMessagesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> entry("one", "tel:first", " ", 1000));
        assertThrows(IllegalArgumentException.class, () -> entry("one", "tel:first", "x".repeat(1501), 1000));
    }

    @Test
    public void anonymousCallsHaveDifferentGroups() {
        MessageHistory history = new MessageHistory(new ArrayList<>(), 1000);
        history.add(entry("one", "anonymous:call-one", "Premier appel masqué", 1000), 1000);
        history.add(entry("two", "anonymous:call-two", "Second appel masqué", 1000), 1000);
        assertEquals(2, history.all(1000).stream().map(value -> value.contactKey).distinct().count());
    }
}
