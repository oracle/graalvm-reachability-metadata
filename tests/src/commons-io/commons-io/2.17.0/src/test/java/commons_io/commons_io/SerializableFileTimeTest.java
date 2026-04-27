/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_io.commons_io;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import org.apache.commons.io.monitor.FileEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class SerializableFileTimeTest {

    @Test
    void preservesFileEntryLastModifiedTimeAcrossSerialization(@TempDir final Path tempDirectory) throws Exception {
        final Instant lastModifiedInstant = Instant.parse("2024-03-04T05:06:07.123456789Z");
        final FileTime lastModifiedTime = FileTime.from(lastModifiedInstant);
        final Path monitoredFile = tempDirectory.resolve("monitored-file.txt");
        final FileEntry entry = new FileEntry(monitoredFile.toFile());
        entry.setExists(true);
        entry.setLastModified(lastModifiedTime);
        entry.setLength(128L);

        final FileEntry restored = deserialize(serialize(entry));

        assertThat(restored.getFile()).isEqualTo(entry.getFile());
        assertThat(restored.isExists()).isTrue();
        assertThat(restored.getLastModifiedFileTime()).isEqualTo(lastModifiedTime);
        assertThat(restored.getLastModified()).isEqualTo(lastModifiedTime.toMillis());
        assertThat(restored.getLength()).isEqualTo(128L);
    }

    private static byte[] serialize(final FileEntry entry) throws IOException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(entry);
        }

        return outputStream.toByteArray();
    }

    private static FileEntry deserialize(final byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (FileEntry) objectInputStream.readObject();
        }
    }
}
