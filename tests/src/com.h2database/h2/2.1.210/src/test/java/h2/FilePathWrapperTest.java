/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.h2.store.fs.FilePath;
import org.h2.store.fs.FilePathWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class FilePathWrapperTest {
    @TempDir
    Path tempDir;

    @Test
    void createsWrapperForRegisteredFileSystemScheme() throws Exception {
        Path file = tempDir.resolve("wrapped-data.txt");
        Files.writeString(file, "wrapped-data", StandardCharsets.UTF_8);
        String basePath = toH2Path(file);

        FilePath wrapped = FilePath.get("split:4:" + basePath);

        assertThat(wrapped).isInstanceOf(FilePathWrapper.class);
        assertThat(wrapped.getScheme()).isEqualTo("split");
        assertThat(wrapped.exists()).isTrue();
        assertThat(wrapped.size()).isEqualTo(Files.size(file));
        assertThat(wrapped.unwrap().toString()).isEqualTo(basePath);
    }

    @Test
    void wrapsDerivedBasePathsWithTheSameFileSystemScheme() throws Exception {
        Path file = tempDir.resolve("nested-wrapper-data.txt");
        Files.writeString(file, "nested-data", StandardCharsets.UTF_8);
        String basePath = toH2Path(file);

        FilePath wrapped = FilePath.get("split:4:" + basePath);
        FilePath parent = wrapped.getParent();

        assertThat(parent).isInstanceOf(FilePathWrapper.class);
        assertThat(parent.getScheme()).isEqualTo("split");
        assertThat(parent.toString()).isEqualTo("split:4:" + toH2Path(tempDir));
        assertThat(parent.unwrap().toString()).isEqualTo(toH2Path(tempDir));
    }

    private static String toH2Path(Path path) {
        return path.toAbsolutePath().normalize().toString().replace('\\', '/');
    }
}
