/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dom4j.dom4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;

import org.dom4j.tree.NamespaceCache;
import org.junit.jupiter.api.Test;

public class ConcurrentReaderHashMapTest {
    @Test
    void serializesUriCacheEntries() throws Exception {
        ExposedNamespaceCache namespaceCache = new ExposedNamespaceCache();
        Map uriCache = namespaceCache.getExposedURICache("urn:dom4j-cache-test");
        uriCache.clear();
        uriCache.put("first-prefix", "first-uri");
        uriCache.put("second-prefix", "second-uri");

        Map restored = roundTrip(uriCache);

        assertThat(restored).isNotSameAs(uriCache);
        assertThat(restored).containsEntry("first-prefix", "first-uri");
        assertThat(restored).containsEntry("second-prefix", "second-uri");
        assertThat(restored).hasSize(2);
    }

    private static Map roundTrip(Map cache) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(cache);
        }

        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (Map) in.readObject();
        }
    }

    private static class ExposedNamespaceCache extends NamespaceCache {
        Map getExposedURICache(String uri) {
            return getURICache(uri);
        }
    }
}
