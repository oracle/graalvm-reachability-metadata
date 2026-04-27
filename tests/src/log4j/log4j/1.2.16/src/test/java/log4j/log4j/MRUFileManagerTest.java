/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package log4j.log4j;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.log4j.lf5.viewer.configure.MRUFileManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class MRUFileManagerTest {

    @Test
    void savesAndLoadsSerializedMruEntries(@TempDir Path temporaryHome) throws Exception {
        String originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", temporaryHome.toString());

        try {
            Path fileEntry = Files.writeString(temporaryHome.resolve("recent.log"), "file-entry", StandardCharsets.UTF_8);
            Path urlEntry = Files.writeString(temporaryHome.resolve("recent-url.log"), "url-entry", StandardCharsets.UTF_8);
            URL fileUrl = urlEntry.toUri().toURL();

            MRUFileManager savedManager = new MRUFileManager(5);
            savedManager.set(fileEntry.toFile());
            savedManager.set(fileUrl);
            savedManager.save();

            MRUFileManager loadedManager = new MRUFileManager(5);

            assertThat(loadedManager.getMRUFileList())
                    .containsExactly(fileUrl.toString(), fileEntry.toFile().getAbsolutePath());

            try (InputStream urlStream = loadedManager.getInputStream(0);
                 InputStream fileStream = loadedManager.getInputStream(1)) {
                assertThat(new String(urlStream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("url-entry");
                assertThat(new String(fileStream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("file-entry");
            }
        } finally {
            restoreProperty("user.home", originalUserHome);
        }
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
            return;
        }
        System.setProperty(name, value);
    }
}
