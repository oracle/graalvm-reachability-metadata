/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import java.io.File;
import java.nio.file.attribute.FileTime;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.apache.commons.io.monitor.FileEntry;
import org.testcontainers.shaded.org.apache.commons.lang3.SerializationUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class SerializableFileTimeTest {
    @Test
    void preservesFileTimesWhenFileEntriesAreSerialized() {
        FileTime modified = FileTime.fromMillis(1_234_567L);
        FileEntry entry = new FileEntry(new File("observed-file"));
        entry.setLastModified(modified);

        FileEntry restored = SerializationUtils.roundtrip(entry);

        assertThat(restored.getLastModifiedFileTime()).isEqualTo(modified);
        assertThat(restored.getFile()).isEqualTo(entry.getFile());
    }
}
