/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_poi.poi;

import org.apache.poi.poifs.nio.CleanerUtil;
import org.apache.poi.poifs.nio.FileBackedDataSource;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class CleanerUtilDynamicAccessTest {

    @Test
    void mapsAndReleasesFileBackedBuffers() throws Exception {
        Path file = Files.createTempFile("poi-cleaner", ".bin");
        Files.write(file, new byte[]{1, 2, 3, 4});

        try (FileBackedDataSource dataSource = new FileBackedDataSource(file.toFile(), false)) {
            ByteBuffer buffer = dataSource.read(4, 0);

            assertThat(buffer.isDirect()).isTrue();
            assertThat(buffer.get(0)).isEqualTo((byte) 1);

            dataSource.releaseBuffer(buffer);

            if (CleanerUtil.UNMAP_SUPPORTED) {
                assertThat(CleanerUtil.getCleaner()).isNotNull();
            } else {
                assertThat(CleanerUtil.UNMAP_NOT_SUPPORTED_REASON).isNotBlank();
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
