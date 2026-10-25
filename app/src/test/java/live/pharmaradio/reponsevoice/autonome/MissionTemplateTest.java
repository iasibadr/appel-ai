package live.pharmaradio.reponsevoice.autonome;

import org.junit.Test;
import static org.junit.Assert.*;

public class MissionTemplateTest {
    @Test
    public void examplesHaveStructuredSafeMissionsWithoutRecipients() {
        assertEquals(5, MissionTemplate.examples().length);
        java.util.Set<String> titles = new java.util.HashSet<>();
        for (MissionTemplate template : MissionTemplate.examples()) {
            assertTrue(titles.add(template.title));
            assertTrue(template.limits.contains("Aucun engagement"));
            assertTrue(template.handoff.contains("une personne"));
            assertTrue(CallMission.build(template.objective, template.questions, template.limits, template.handoff, template.report).length() <= 4000);
        }
    }

    @Test
    public void rejectsInvalidTitlesAndFields() {
        assertThrows(IllegalArgumentException.class, () -> new MissionTemplate(" ", "Sujet", "", "", "", false));
        assertThrows(IllegalArgumentException.class, () -> new MissionTemplate("Titre\n", "Sujet", "", "", "", false));
        assertThrows(IllegalArgumentException.class, () -> new MissionTemplate("Titre", "", "", "", "", false));
        assertThrows(IllegalArgumentException.class, () -> new MissionTemplate("Titre", "a".repeat(1601), "", "", "", false));
        assertThrows(IllegalArgumentException.class, () -> new MissionTemplate("Titre", "Sujet", null, "", "", false));
        assertThrows(IllegalArgumentException.class, () -> new MissionTemplate("Titre", "Sujet", "a".repeat(901), "", "", false));
        assertThrows(IllegalArgumentException.class, () -> new MissionTemplate("Titre", "Sujet", "", "a".repeat(451), "", false));
        assertThrows(IllegalArgumentException.class, () -> new MissionTemplate("Titre", "Sujet", "", "", "a".repeat(451), false));
    }

    @Test
    public void retainsExplicitReportChoiceAndTrimsFields() {
        MissionTemplate template = new MissionTemplate(" Favori ", " Objectif ", " Question ", " Limite ", " Reprise ", false);
        assertEquals("Favori", template.title);
        assertEquals("Objectif", template.objective);
        assertEquals("Question", template.questions);
        assertEquals("Limite", template.limits);
        assertEquals("Reprise", template.handoff);
        assertFalse(template.report);
    }
}
