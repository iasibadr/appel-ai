package live.pharmaradio.reponsevoice.autonome;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public class CallAuthorizationTest {
    private static final String DESTINATION = "+15555550123";

    @Test
    public void noMissionWithoutConfirmation() {
        assertNull(new CallAuthorization().consume(DESTINATION, 100));
    }

    @Test
    public void exactDestinationAndSingleUse() {
        CallAuthorization authorization = new CallAuthorization();
        authorization.arm(DESTINATION, " Demander les horaires. ", 100);
        assertNull(authorization.consume("+15555550124", 200));
        assertEquals("Demander les horaires.", authorization.consume(DESTINATION, 201));
        assertNull(authorization.consume(DESTINATION, 202));
    }

    @Test
    public void expiredMissionCannotAttach() {
        CallAuthorization authorization = new CallAuthorization();
        authorization.arm(DESTINATION, "Test", 100);
        assertNull(authorization.consume(DESTINATION, 90100));
    }

    @Test
    public void cancellationClearsMission() {
        CallAuthorization authorization = new CallAuthorization();
        authorization.arm(DESTINATION, "Test", 100);
        authorization.clear();
        assertNull(authorization.consume(DESTINATION, 101));
    }

    @Test
    public void oldTimerDoesNotClearNewAuthorization() {
        CallAuthorization authorization = new CallAuthorization();
        authorization.arm(DESTINATION, "Premier sujet", 100);
        authorization.arm(DESTINATION, "Deuxième sujet", 200);
        authorization.expire(90100);
        assertEquals("Deuxième sujet", authorization.consume(DESTINATION, 90101));
    }

    @Test
    public void invalidBriefClearsPreviousAuthorization() {
        CallAuthorization authorization = new CallAuthorization();
        authorization.arm(DESTINATION, "Test", 100);
        assertThrows(IllegalArgumentException.class, () -> authorization.arm(DESTINATION, " ", 101));
        assertNull(authorization.consume(DESTINATION, 102));
        assertThrows(IllegalArgumentException.class, () -> authorization.arm(DESTINATION, "x".repeat(4001), 103));
    }

    @Test
    public void shortCodesAndUssdAreRejected() {
        CallAuthorization authorization = new CallAuthorization();
        assertThrows(IllegalArgumentException.class, () -> authorization.arm("112", "Test", 100));
        assertThrows(IllegalArgumentException.class, () -> authorization.arm("*123#", "Test", 100));
        assertThrows(IllegalArgumentException.class, () -> authorization.arm("+15555550123;42", "Test", 100));
    }
}
