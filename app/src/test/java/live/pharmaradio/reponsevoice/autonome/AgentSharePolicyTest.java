package live.pharmaradio.reponsevoice.autonome;

import org.junit.Test;
import static org.junit.Assert.*;

public class AgentSharePolicyTest {
    @Test public void restrictsNamesAndLifetime() {
        assertTrue(AgentSharePolicy.filename("12345678-1234-1234-1234-123456789012.apk"));
        assertTrue(AgentSharePolicy.filename("12345678-1234-1234-1234-123456789012.json"));
        for (String name : new String[]{"../preferences.xml", "/base.apk", "x.json", "12345678-1234-1234-1234-123456789012.apk.pending"}) {
            assertFalse(AgentSharePolicy.filename(name));
        }
        assertTrue(AgentSharePolicy.fresh(1000, 1000));
        assertFalse(AgentSharePolicy.fresh(0, 1000));
        assertFalse(AgentSharePolicy.fresh(1001, 1000));
        assertFalse(AgentSharePolicy.fresh(1000, 1000 + AgentSharePolicy.LIFETIME_MS));
    }

    @Test public void rejectsKeysAndHiddenInstructions() {
        for (String objective : new String[]{"sk-proj-" + "A".repeat(35), "-----BEGIN RSA PRIVATE KEY-----", "Texte\u202E caché", "Texte\u0001 caché"}) {
            assertThrows(IllegalArgumentException.class, () -> AgentSharePolicy.validate(new MissionTemplate("Modèle", objective, "", "", "", false)));
        }
    }

    @Test public void previewsAllSharedFieldsWithoutImplicitProfile() {
        MissionTemplate template = new MissionTemplate("Titre", "Objectif", "Question", "Limite", "Reprise", true);
        assertSame(template, AgentSharePolicy.validate(template));
        String preview = AgentSharePolicy.preview(template);
        for (String field : new String[]{"Titre", "Objectif", "Question", "Limite", "Reprise", "oui"}) assertTrue(preview.contains(field));
        assertFalse(preview.contains("owner-name"));
    }
}
