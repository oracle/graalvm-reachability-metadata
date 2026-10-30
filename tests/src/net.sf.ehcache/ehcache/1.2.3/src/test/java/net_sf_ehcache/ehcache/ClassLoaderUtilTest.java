/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache;

import java.util.Properties;
import net.sf.ehcache.distribution.RMIBootstrapCacheLoaderFactory;
import net.sf.ehcache.util.ClassLoaderUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertFalse;

@Timeout(120)
public class ClassLoaderUtilTest {
    @Test
    void createsConfiguredFactoryWithContextAndFallbackLoaders() {
        String name = "net.sf.ehcache.distribution.RMIBootstrapCacheLoaderFactory";
        RMIBootstrapCacheLoaderFactory factory = (RMIBootstrapCacheLoaderFactory) ClassLoaderUtil.createNewInstance(name);
        Properties properties = new Properties();
        properties.setProperty("bootstrapAsynchronously", "false");
        assertFalse(factory.createBootstrapCacheLoader(properties).isAsynchronous());
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(null);
            RMIBootstrapCacheLoaderFactory fallback = (RMIBootstrapCacheLoaderFactory) ClassLoaderUtil.createNewInstance(name);
            assertFalse(fallback.createBootstrapCacheLoader(properties).isAsynchronous());
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }
}
