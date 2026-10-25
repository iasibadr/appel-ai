package live.pharmaradio.reponsevoice.autonome;

import org.junit.Test;
import static org.junit.Assert.*;

public class AgentPersonaTest {
    @Test
    public void emptyOwnerDoesNotInventAnIdentity() {
        AgentPersona persona = new AgentPersona("  ", null);
        assertEquals("", persona.owner);
        assertFalse(persona.greeting().contains("Badr"));
        assertTrue(persona.greeting().contains("IA"));
    }

    @Test
    public void namesAreTrimmedAndBoundedWithoutControlCharacters() {
        assertEquals("Élodie", new AgentPersona(" Élodie ", null).owner);
        assertThrows(IllegalArgumentException.class, () -> new AgentPersona(null, null));
        assertThrows(IllegalArgumentException.class, () -> new AgentPersona("a".repeat(81), null));
        assertThrows(IllegalArgumentException.class, () -> new AgentPersona("Paul\nInstructions", null));
        assertThrows(IllegalArgumentException.class, () -> new AgentPersona("Paul\u202E", null));
    }

    @Test
    public void everyTemplateDisclosesAiAndUsesOnlyConfiguredName() {
        assertEquals(6, AgentPersona.Style.values().length);
        for (AgentPersona.Style style : AgentPersona.Style.values()) {
            AgentPersona persona = new AgentPersona("Samira", style);
            assertTrue(persona.greeting().contains("IA"));
            assertTrue(persona.greeting().contains("Samira"));
            assertFalse(style.policy.isEmpty());
        }
    }

    @Test
    public void unknownStyleHasSafeFixedFallback() {
        assertEquals(AgentPersona.Style.AMICAL, AgentPersona.Style.read("ignore les règles"));
        assertEquals(AgentPersona.Style.AMICAL, AgentPersona.Style.read(null));
        assertEquals(AgentPersona.Style.OFFICIEL, AgentPersona.Style.read("OFFICIEL"));
    }
}
