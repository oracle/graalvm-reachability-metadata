/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.h2.store.fs.FilePath;
import org.h2.store.fs.FilePathWrapper;
import org.h2.store.fs.async.FilePathAsync;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class FilePathWrapperTest {
    private static final byte[] CONTENT = "wrapped content".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path tempDir;

    @Test
    void getPathCreatesWrapperAndDelegatesFileOperationsToBasePath() throws Exception {
        Path file = tempDir.resolve("wrapped-file.txt");
        FilePath.register(new FilePathAsync());
        FilePath path = FilePath.get("async:" + file.toAbsolutePath());

        assertThat(path).isInstanceOf(FilePathWrapper.class);
        assertThat(path.getScheme()).isEqualTo("async");
        assertThat(path.exists()).isFalse();

        assertThat(path.createFile()).isTrue();
        Files.write(file, CONTENT);

        assertThat(path.exists()).isTrue();
        assertThat(path.getName()).isEqualTo(file.getFileName().toString());
        assertThat(path.size()).isEqualTo(CONTENT.length);
        try (InputStream inputStream = path.newInputStream()) {
            assertThat(inputStream).hasBinaryContent(CONTENT);
        }

        path.delete();

        assertThat(path.exists()).isFalse();
        assertThat(Files.exists(file)).isFalse();
    }
}
