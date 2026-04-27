/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.tools.ant.util.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class FileUtilsTest {
    @BeforeEach
    void resetLastModifiedMethodCache() throws ReflectiveOperationException {
        resetStaticField("class$java$io$File");
        resetStaticField("setLastModified");
    }

    @Test
    void updatesLastModifiedTimeThroughPublicApi(@TempDir Path temporaryDirectory) throws IOException {
        Path file = temporaryDirectory.resolve("sample.txt");
        Files.writeString(file, "content");
        long expectedModifiedTime = 946_684_800_000L;

        FileUtils.newFileUtils().setFileLastModified(file.toFile(), expectedModifiedTime);

        assertThat(file.toFile().lastModified()).isEqualTo(expectedModifiedTime);
    }

    private static void resetStaticField(String fieldName) throws ReflectiveOperationException {
        Field field = FileUtils.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, null);
    }
}
