/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import org.apache.tools.zip.ZipEntry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ZipEntryTest {

    @Test
    void setComprSizeUpdatesCompressedSize() {
        ZipEntry entry = new ZipEntry("archive/member.txt");

        entry.setComprSize(4096L);

        assertThat(entry.getCompressedSize()).isEqualTo(4096L);
    }
}
