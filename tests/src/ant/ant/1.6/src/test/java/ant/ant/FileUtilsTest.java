/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.tools.ant.util.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

public class FileUtilsTest {
    private static final long EXPECTED_LAST_MODIFIED_TIME = 1_600_000_000_000L;

    @TempDir
    Path tempDirectory;

    @Test
    void setsFileLastModifiedThroughPublicUtilityMethod() throws IOException {
        Path file = Files.createFile(tempDirectory.resolve("timestamped-file.txt"));
        File targetFile = file.toFile();

        FileUtils.newFileUtils().setFileLastModified(targetFile, EXPECTED_LAST_MODIFIED_TIME);

        assertThat(targetFile.lastModified())
                .isCloseTo(
                        EXPECTED_LAST_MODIFIED_TIME,
                        offset(FileUtils.FAT_FILE_TIMESTAMP_GRANULARITY));
    }
}
