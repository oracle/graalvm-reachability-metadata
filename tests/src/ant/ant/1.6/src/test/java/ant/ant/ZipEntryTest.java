/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import org.apache.tools.zip.ZipEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ZipEntryTest {
    @BeforeEach
    void resetCompressedSizeCompatibilityCache() throws ReflectiveOperationException {
        resetStaticField("class$java$util$zip$ZipEntry", null);
        resetStaticField("setCompressedSizeMethod", null);
        resetStaticField("triedToGetMethod", false);
    }

    @Test
    void setCompressedSizeThroughAntCompatibilityApi() {
        ZipEntry entry = new ZipEntry("content/data.txt");

        entry.setComprSize(4096L);

        assertThat(entry.getCompressedSize()).isEqualTo(4096L);
    }

    private static void resetStaticField(String fieldName, Object value) throws ReflectiveOperationException {
        Field field = ZipEntry.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, value);
    }
}
