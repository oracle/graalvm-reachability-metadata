/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import net.sf.ehcache.terracotta.TerracottaClient;
import org.junit.jupiter.api.Test;

public class TerracottaClientTest {
    @Test
    void delegatesTestModeToTerracottaClusteredInstanceHelper() throws Exception {
        Class<?> helperClass = Class.forName("net.sf.ehcache.terracotta.TerracottaClusteredInstanceHelper");
        Method getInstanceMethod = helperClass.getDeclaredMethod("getInstance");
        getInstanceMethod.setAccessible(true);
        Object originalHelper = getInstanceMethod.invoke(null);

        Method setTestModeMethod = TerracottaClient.class.getDeclaredMethod("setTestMode", helperClass);
        setTestModeMethod.setAccessible(true);

        try {
            setTestModeMethod.invoke(null, new Object[] {null});
            Object clearedHelper = getInstanceMethod.invoke(null);
            assertThat(clearedHelper).isNull();
        } finally {
            setTestModeMethod.invoke(null, originalHelper);
        }

        Object restoredHelper = getInstanceMethod.invoke(null);
        assertThat(restoredHelper).isSameAs(originalHelper);
    }
}
