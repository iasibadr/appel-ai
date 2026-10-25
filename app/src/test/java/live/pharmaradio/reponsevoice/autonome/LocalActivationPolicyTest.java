package live.pharmaradio.reponsevoice.autonome;

import org.junit.Test;
import static org.junit.Assert.*;

public class LocalActivationPolicyTest {
    @Test
    public void bootstrapOnlyAcceptsTheInstalledApkNotUserCommands() {
        String command = LocalActivationPolicy.activationCommand("/data/app/~~abc==/live.pharmaradio.reponsevoice.autonome-Xy_12==/base.apk");
        assertTrue(command.contains("ShellAudioBridge --activate-only"));
        for (String path : new String[]{null, "", "/sdcard/base.apk", "/data/app/../base.apk", "/data/app/./base.apk",
                "/data/app/a/base.apk; id", "/data/app/a/'base.apk", "/data/app/a/base.apk\n", "https://example.invalid/base.apk"}) {
            assertThrows(IllegalArgumentException.class, () -> LocalActivationPolicy.activationCommand(path));
        }
    }

    @Test
    public void pairingInputIsExactlySixAsciiDigits() {
        assertTrue(LocalActivationPolicy.pairingCode("000001"));
        for (String value : new String[]{null, "", "12345", "1234567", "123 45", "abcdef", "１２３４５６", "123456\n"}) {
            assertFalse(LocalActivationPolicy.pairingCode(value));
        }
    }

    @Test
    public void portsExpireAndNeverAcceptInvalidOrFutureDiscovery() {
        assertTrue(LocalActivationPolicy.freshPort(40000, 1, 59999));
        assertFalse(LocalActivationPolicy.freshPort(40000, 0, 60000));
        assertFalse(LocalActivationPolicy.freshPort(40000, 100, 99));
        assertFalse(LocalActivationPolicy.freshPort(40000, -1, 1));
        assertFalse(LocalActivationPolicy.freshPort(0, 0, 0));
        assertFalse(LocalActivationPolicy.freshPort(65536, 0, 0));
        assertFalse(LocalActivationPolicy.freshPort(555, 0, 0));
    }

    @Test
    public void legacyHelperCannotProveLocalActivationOrSkipPairing() {
        assertEquals(LocalActivationPolicy.Stage.PAIR, LocalActivationPolicy.next(true, false, false, true, true, true, true));
        assertEquals(LocalActivationPolicy.Stage.ACTIVATE, LocalActivationPolicy.next(true, true, false, true, true, true, true));
        assertEquals(LocalActivationPolicy.Stage.VERIFY, LocalActivationPolicy.next(true, true, true, true, false, false, false));
    }

    @Test
    public void readyRequiresCurrentHelperAndConfirmedLocalRoute() {
        for (int mask = 0; mask < 128; mask++) {
            boolean key = (mask & 1) != 0;
            boolean paired = (mask & 2) != 0;
            boolean activated = (mask & 4) != 0;
            boolean helper = (mask & 8) != 0;
            boolean fresh = (mask & 16) != 0;
            LocalActivationPolicy.Stage next = LocalActivationPolicy.next(key, paired, activated, helper, fresh, (mask & 32) != 0, (mask & 64) != 0);
            assertEquals(key && paired && activated && helper && fresh, next == LocalActivationPolicy.Stage.READY);
        }
    }
}
