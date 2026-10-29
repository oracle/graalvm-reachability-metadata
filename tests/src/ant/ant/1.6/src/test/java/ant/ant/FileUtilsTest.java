/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.nio.file.Files;

import org.apache.tools.ant.util.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class FileUtilsTest {
    @TempDir
    File directory;

    @Test
    void setsModificationTimeWithoutChangingContent() throws Exception {
        File file = new File(directory, "data.txt");
        Files.writeString(file.toPath(), "contents");
        long timestamp = 1600000000000L;
        FileUtils.newFileUtils().setFileLastModified(file, timestamp);
        assertEquals(timestamp, file.lastModified());
        assertEquals("contents", Files.readString(file.toPath()));
    }
}
