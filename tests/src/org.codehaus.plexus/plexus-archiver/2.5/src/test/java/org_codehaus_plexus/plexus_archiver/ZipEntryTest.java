/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_archiver;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ZipEntryTest {

    @Test
    void setCompressedSizeUpdatesCompressedSizeThroughJdkZipEntryApi() throws Exception {
        ZipArchiveEntry entry = new ZipArchiveEntry("compressed-entry.txt");
        long compressedSize = 23L;

        entry.setCompressedSize(compressedSize);

        assertThat(entry.getCompressedSize()).isEqualTo(compressedSize);
    }
}
