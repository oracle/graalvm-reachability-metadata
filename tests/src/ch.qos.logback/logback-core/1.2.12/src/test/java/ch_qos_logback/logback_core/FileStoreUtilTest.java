/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.core.rolling.helper.FileStoreUtil;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class FileStoreUtilTest {

    @Test
    void reportsSiblingFilesOnTheSameFileStore(@TempDir Path tempDir) throws Exception {
        Path firstFile = Files.createFile(tempDir.resolve("first.log"));
        Path secondFile = Files.createFile(tempDir.resolve("second.log"));

        boolean areOnSameFileStore = FileStoreUtil.areOnSameFileStore(firstFile.toFile(), secondFile.toFile());

        assertThat(areOnSameFileStore)
                .isEqualTo(Files.getFileStore(firstFile).equals(Files.getFileStore(secondFile)));
    }

    @Test
    void rejectsMissingFiles(@TempDir Path tempDir) throws Exception {
        Path existingFile = Files.createFile(tempDir.resolve("existing.log"));
        Path missingFile = tempDir.resolve("missing.log");

        assertThatThrownBy(() -> FileStoreUtil.areOnSameFileStore(existingFile.toFile(), missingFile.toFile()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist");
    }
}
