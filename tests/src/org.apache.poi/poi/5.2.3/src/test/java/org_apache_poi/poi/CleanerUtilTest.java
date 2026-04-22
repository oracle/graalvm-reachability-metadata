/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_poi.poi;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.poi.poifs.nio.CleanerUtil;
import org.apache.poi.poifs.nio.FileBackedDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class CleanerUtilTest {

    @Test
    void writableFileBackedDataSourceUsesDirectBuffersThatCleanerUtilCanRelease(@TempDir Path tempDir) throws Exception {
        Path path = tempDir.resolve("poi-cleaner.dat");
        Files.writeString(path, "Apache POI mapped buffer", StandardCharsets.UTF_8);

        try (FileBackedDataSource dataSource = new FileBackedDataSource(path.toFile(), false)) {
            ByteBuffer firstBuffer = dataSource.read(10, 0);
            assertThat(firstBuffer.isDirect()).isTrue();
            assertThat(StandardCharsets.UTF_8.decode(firstBuffer.duplicate()).toString()).isEqualTo("Apache POI");

            assertThat(CleanerUtil.UNMAP_SUPPORTED).isEqualTo(CleanerUtil.getCleaner() != null);
            if (CleanerUtil.UNMAP_SUPPORTED) {
                assertThat(CleanerUtil.UNMAP_NOT_SUPPORTED_REASON).isNull();
            } else {
                assertThat(CleanerUtil.UNMAP_NOT_SUPPORTED_REASON).isNotBlank();
            }

            dataSource.releaseBuffer(firstBuffer);

            ByteBuffer secondBuffer = dataSource.read(6, 11);
            assertThat(secondBuffer.isDirect()).isTrue();
            assertThat(StandardCharsets.UTF_8.decode(secondBuffer.duplicate()).toString()).isEqualTo("mapped");
        }
    }
}
