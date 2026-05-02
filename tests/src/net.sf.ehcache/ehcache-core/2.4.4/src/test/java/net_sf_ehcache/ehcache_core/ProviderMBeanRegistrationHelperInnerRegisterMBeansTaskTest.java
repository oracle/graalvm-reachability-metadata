/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.hibernate.management.impl.ProviderMBeanRegistrationHelper;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;
import org.hibernate.impl.SessionFactoryImpl;
import org.junit.jupiter.api.Test;

public class ProviderMBeanRegistrationHelperInnerRegisterMBeansTaskTest {
    private static final String CACHE_MANAGER_NAME_PREFIX = "register-mbeans-task-coverage-";
    private static final String HIBERNATE_STATISTICS_SUPPORTED_ATTRIBUTE = "HibernateStatisticsSupported";
    private static final long REGISTRATION_TIMEOUT_MILLIS = 5_000L;
    private static final long POLL_INTERVAL_MILLIS = 50L;

    @Test
    void locatesUnnamedSessionFactoryByMatchingProperties() throws Exception {
        String previousTcActive = System.getProperty("tc.active");
        String cacheManagerName = CACHE_MANAGER_NAME_PREFIX + System.nanoTime();
        ProviderMBeanRegistrationHelper helper = new ProviderMBeanRegistrationHelper();
        CacheManager cacheManager = null;
        SessionFactoryImpl sessionFactory = null;
        try {
            cacheManager = newCacheManager(cacheManagerName);
            sessionFactory = newSessionFactory();
            Properties registrationProperties = sessionFactory.getProperties();
            ObjectName objectName = new ObjectName(
                    "net.sf.ehcache.hibernate:type=EhcacheHibernateStats,name=" + cacheManagerName);
            MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();

            System.setProperty("tc.active", "true");

            helper.registerMBean(cacheManager, registrationProperties);

            assertThat(awaitHibernateStatisticsSupport(mBeanServer, objectName)).isTrue();
        } finally {
            helper.unregisterMBean();
            closeSessionFactory(sessionFactory);
            shutdownCacheManager(cacheManager);
            restoreTcActive(previousTcActive);
        }
    }

    private static CacheManager newCacheManager(String cacheManagerName) {
        String configuration = """
                <ehcache name=\"%s\"
                         updateCheck=\"false\"
                         monitoring=\"off\">
                    <diskStore path=\"java.io.tmpdir/register-mbeans-task-test\"/>
                    <defaultCache maxElementsInMemory=\"10\"
                                  eternal=\"false\"
                                  overflowToDisk=\"false\"
                                  timeToIdleSeconds=\"120\"
                                  timeToLiveSeconds=\"120\"/>
                </ehcache>
                """.formatted(cacheManagerName);
        return new CacheManager(new ByteArrayInputStream(configuration.getBytes(StandardCharsets.UTF_8)));
    }

    private static SessionFactoryImpl newSessionFactory() {
        Properties properties = new Properties();
        properties.setProperty(Environment.DIALECT, "org.hibernate.dialect.HSQLDialect");
        properties.setProperty(Environment.USE_SECOND_LEVEL_CACHE, "false");
        properties.setProperty(Environment.USE_QUERY_CACHE, "false");
        SessionFactory sessionFactory = new Configuration()
                .setProperties(properties)
                .buildSessionFactory();
        return (SessionFactoryImpl) sessionFactory;
    }

    private static boolean awaitHibernateStatisticsSupport(
            MBeanServer mBeanServer, ObjectName objectName) throws Exception {
        long deadline = System.currentTimeMillis() + REGISTRATION_TIMEOUT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            if (mBeanServer.isRegistered(objectName)) {
                Object attributeValue = mBeanServer.getAttribute(objectName, HIBERNATE_STATISTICS_SUPPORTED_ATTRIBUTE);
                if (Boolean.TRUE.equals(attributeValue)) {
                    return true;
                }
            }
            Thread.sleep(POLL_INTERVAL_MILLIS);
        }
        return false;
    }

    private static void closeSessionFactory(SessionFactory sessionFactory) {
        if (sessionFactory != null && !sessionFactory.isClosed()) {
            sessionFactory.close();
        }
    }

    private static void shutdownCacheManager(CacheManager cacheManager) {
        if (cacheManager != null) {
            cacheManager.shutdown();
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
