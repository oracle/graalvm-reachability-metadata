/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.management.ManagementFactory;
import java.util.Properties;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.config.Configuration;
import net.sf.ehcache.hibernate.management.impl.ProviderMBeanRegistrationHelper;

import org.junit.jupiter.api.Test;

public class ProviderMBeanRegistrationHelperTest {
    private static final String CACHE_MANAGER_NAME_PREFIX = "provider-mbean-registration-helper-coverage-";
    private static final long REGISTRATION_TIMEOUT_MILLIS = 5_000L;
    private static final long POLL_INTERVAL_MILLIS = 50L;
    private static final long POST_REGISTRATION_SETTLE_MILLIS = 600L;

    @Test
    void registersHibernateMBeanAndScansUnnamedSessionFactories() throws Exception {
        String previousTcActive = System.getProperty("tc.active");
        String cacheManagerName = CACHE_MANAGER_NAME_PREFIX + System.nanoTime();
        CacheManager cacheManager = newCacheManager(cacheManagerName);
        ProviderMBeanRegistrationHelper helper = new ProviderMBeanRegistrationHelper();
        ObjectName objectName = new ObjectName("net.sf.ehcache.hibernate:type=EhcacheHibernateStats,name=" + cacheManagerName);
        MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
        try {
            System.setProperty("tc.active", "true");

            helper.registerMBean(cacheManager, new Properties());

            awaitMBeanRegistration(mBeanServer, objectName);
            Thread.sleep(POST_REGISTRATION_SETTLE_MILLIS);
            assertThat(mBeanServer.isRegistered(objectName)).isTrue();
        } finally {
            helper.unregisterMBean();
            cacheManager.shutdown();
            restoreTcActive(previousTcActive);
        }
    }

    private static CacheManager newCacheManager(String cacheManagerName) {
        Configuration configuration = new Configuration()
                .name(cacheManagerName)
                .updateCheck(false);
        return new CacheManager(configuration);
    }

    private static void awaitMBeanRegistration(MBeanServer mBeanServer, ObjectName objectName) throws InterruptedException {
        long deadline = System.currentTimeMillis() + REGISTRATION_TIMEOUT_MILLIS;
        while (!mBeanServer.isRegistered(objectName) && System.currentTimeMillis() < deadline) {
            Thread.sleep(POLL_INTERVAL_MILLIS);
        }
    }

    private static void restoreTcActive(String previousTcActive) {
        if (previousTcActive == null) {
            System.clearProperty("tc.active");
            return;
        }
        System.setProperty("tc.active", previousTcActive);
    }
}
