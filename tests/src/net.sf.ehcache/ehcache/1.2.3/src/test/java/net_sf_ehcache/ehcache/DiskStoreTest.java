/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.sf.ehcache.Cache;
import net.sf.ehcache.Element;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class DiskStoreTest {
    @TempDir
    Path directory;

    @Test
    void persistsElementsAndIndexAcrossCacheRestarts() {
        Cache first = TestSupport.persistentCache(directory, "persistent");
        try {
            first.put(new Element("kept", new ArrayList<>(List.of("payload"))));
            first.put(new Element("removed", "discard"));
            first.flush();
            assertEquals(2, first.getDiskStoreSize());
            assertEquals(List.of("payload"), first.get("kept").getObjectValue());
            assertTrue(first.remove("removed"));
            first.flush();
        } finally {
            first.dispose();
        }
        Cache reopened = TestSupport.persistentCache(directory, "persistent");
        try {
            assertEquals(1, reopened.getSize());
            assertNull(reopened.get("removed"));
            assertEquals(List.of("payload"), reopened.get("kept").getObjectValue());
        } finally {
            reopened.dispose();
        }
    }
}
