/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tools.zip.ZipEntry;
import org.junit.jupiter.api.Test;

public class ZipEntryTest {
    @Test
    void setCompressedSizeThroughAntCompatibilityApi() {
        ZipEntry entry = new ZipEntry("content/data.txt");

        entry.setComprSize(4096L);

        assertThat(entry.getCompressedSize()).isEqualTo(4096L);
    }
}
