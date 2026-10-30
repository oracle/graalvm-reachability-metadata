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

@Timeout(120)
public class DiskStoreAnonymous1Test {
    @TempDir
    Path directory;

    @Test
    void readsDiskValuesWithContextAndFallbackClassResolution() {
        Cache cache = TestSupport.persistentCache(directory, "resolution");
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            cache.put(new Element("context", new ArrayList<>(List.of("one"))));
            cache.put(new Element("fallback", new ArrayList<>(List.of("two"))));
            cache.flush();
            assertEquals(List.of("one"), cache.get("context").getObjectValue());
            Thread.currentThread().setContextClassLoader(null);
            assertEquals(List.of("two"), cache.get("fallback").getObjectValue());
        } finally {
            Thread.currentThread().setContextClassLoader(original);
            cache.dispose();
        }
    }
}
