/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.tools.zip.ZipEntry;
import org.junit.jupiter.api.Test;

public class ZipEntryTest {
    @Test
    void copiesCompressedSizeAndEntryAttributes() throws Exception {
        ZipEntry entry = new ZipEntry("data.txt");
        entry.setMethod(ZipEntry.DEFLATED);
        entry.setSize(100);
        entry.setComprSize(60);
        entry.setCrc(1234);
        ZipEntry copy = new ZipEntry(entry);
        assertEquals("data.txt", copy.getName());
        assertEquals(100, copy.getSize());
        assertEquals(60, copy.getCompressedSize());
        assertEquals(1234, copy.getCrc());
    }
}
