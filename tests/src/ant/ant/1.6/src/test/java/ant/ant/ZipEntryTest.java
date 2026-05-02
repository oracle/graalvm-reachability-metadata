/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import java.lang.reflect.Field;

import org.apache.tools.zip.ZipEntry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ZipEntryTest {

    @Test
    void setComprSizeUpdatesCompressedSize() {
        ZipEntry entry = new ZipEntry("archive/member.txt");

        entry.setComprSize(4096L);

        assertThat(entry.getCompressedSize()).isEqualTo(4096L);
    }

    @Test
    void setComprSizeInitializesCompressedSizeMethodWhenSyntheticClassCacheIsEmpty() throws Exception {
        resetCompressedSizeLookupCache();
        ZipEntry entry = new ZipEntry("archive/uncached-member.txt");

        entry.setComprSize(8192L);

        assertThat(entry.getCompressedSize()).isEqualTo(8192L);
    }

    private static void resetCompressedSizeLookupCache() throws Exception {
        setStaticField("setCompressedSizeMethod", null);
        setStaticField("class$java$util$zip$ZipEntry", null);
        setStaticBooleanField("triedToGetMethod", false);
    }

    private static void setStaticField(String name, Object value) throws Exception {
        Field field = ZipEntry.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static void setStaticBooleanField(String name, boolean value) throws Exception {
        Field field = ZipEntry.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setBoolean(null, value);
    }
}
