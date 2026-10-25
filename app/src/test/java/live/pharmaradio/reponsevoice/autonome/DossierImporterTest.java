package live.pharmaradio.reponsevoice.autonome;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

public class DossierImporterTest {
    @Test
    public void acceptsOnlyReadableBoundedUtf8WithoutSilentTruncation() throws Exception {
        assertEquals("Été, horaires", DossierImporter.utf8("\uFEFFÉté, horaires".getBytes(StandardCharsets.UTF_8)));
        assertThrows(java.nio.charset.CharacterCodingException.class, () -> DossierImporter.utf8(new byte[]{(byte) 0xc3, 0x28}));
        assertThrows(IllegalArgumentException.class, () -> DossierImporter.utf8(new byte[]{0, 1, 2}));
        assertThrows(IllegalArgumentException.class, () -> DossierImporter.utf8("a".repeat(6001).getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class, () -> DossierImporter.utf8(new byte[0]));
    }

    @Test
    public void rejectsOversizedSourceAndPreservesExactBytes() throws Exception {
        byte[] source = "Données fictives".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(source, DossierImporter.bounded(new ByteArrayInputStream(source)));
        assertThrows(IllegalArgumentException.class, () -> DossierImporter.bounded(new ByteArrayInputStream(new byte[DossierImporter.MAX_BYTES + 1])));
    }
}
