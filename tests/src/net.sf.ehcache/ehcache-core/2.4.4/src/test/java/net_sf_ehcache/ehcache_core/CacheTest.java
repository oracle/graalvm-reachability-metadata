/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;

import net.sf.ehcache.Cache;
import net.sf.ehcache.Element;
import net.sf.ehcache.Status;
import net.sf.ehcache.config.CacheConfiguration;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class CacheTest {
    private static final String OFF_HEAP_STORE_CLASS_NAME = "net.sf.ehcache.store.offheap.OffHeapStore";
    private static final String OFF_HEAP_STORE_BYTES = """
            yv66vgAAAEUAFgoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+AQADKClWBwAIAQAUbmV0L3NmL2VoY2Fj
            aGUvQ2FjaGUKAAoACwcADAwADQAOAQAybmV0L3NmL2VoY2FjaGUvc3RvcmUvY29tcG91bmQvaW1wbC9NZW1vcnlPbmx5U3RvcmUB
            AAZjcmVhdGUBAF4oTG5ldC9zZi9laGNhY2hlL0NhY2hlO0xqYXZhL2xhbmcvU3RyaW5nOylMbmV0L3NmL2VoY2FjaGUvc3RvcmUv
            Y29tcG91bmQvaW1wbC9NZW1vcnlPbmx5U3RvcmU7BwAQAQApbmV0L3NmL2VoY2FjaGUvc3RvcmUvb2ZmaGVhcC9PZmZIZWFwU3Rv
            cmUBAARDb2RlAQAPTGluZU51bWJlclRhYmxlAQBIKExuZXQvc2YvZWhjYWNoZS9FaGNhY2hlO0xqYXZhL2xhbmcvU3RyaW5nOylM
            bmV0L3NmL2VoY2FjaGUvc3RvcmUvU3RvcmU7AQAKU291cmNlRmlsZQEAEU9mZkhlYXBTdG9yZS5qYXZhADEADwACAAAAAAACAAIA
            BQAGAAEAEQAAACEAAQABAAAABSq3AAGxAAAAAQASAAAACgACAAAACQAEAAoACQANABMAAQARAAAAIQACAAIAAAAJKsAAByu4AAmw
            AAAAAQASAAAABgABAAAADQABABQAAAACABU=
            """;

    @Test
    void initialisesOffHeapStoreThroughConfiguredFactoryMethod() {
        ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
        Cache cache = newOffHeapCache();
        Thread.currentThread().setContextClassLoader(new OffHeapStoreClassLoader(previousClassLoader));
        try {
            cache.initialise();
            cache.put(new Element("offheap-key", "offheap-value"));

            assertThat(cache.get("offheap-key").getObjectValue()).isEqualTo("offheap-value");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        } finally {
            Thread.currentThread().setContextClassLoader(previousClassLoader);
            if (cache.getStatus() == Status.STATUS_ALIVE) {
                cache.dispose();
            }
        }
    }

    private static Cache newOffHeapCache() {
        CacheConfiguration configuration = new CacheConfiguration("offheap-reflection-coverage", 2)
                .overflowToOffHeap(true)
                .eternal(true);
        return new Cache(configuration);
    }

    private static final class OffHeapStoreClassLoader extends ClassLoader {
        private static final byte[] OFF_HEAP_STORE_CLASS_BYTES = Base64.getMimeDecoder().decode(OFF_HEAP_STORE_BYTES);

        private OffHeapStoreClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (OFF_HEAP_STORE_CLASS_NAME.equals(name)) {
                return defineClass(name, OFF_HEAP_STORE_CLASS_BYTES, 0, OFF_HEAP_STORE_CLASS_BYTES.length);
            }
            return super.findClass(name);
        }
    }
}
