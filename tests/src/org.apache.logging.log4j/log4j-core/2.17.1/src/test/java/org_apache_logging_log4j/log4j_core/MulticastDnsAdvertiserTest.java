/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.UUID;

import javax.jmdns.ServiceInfo;

import org.apache.logging.log4j.core.net.MulticastDnsAdvertiser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class MulticastDnsAdvertiserTest {
    private static final String JMDNS_FIELD = "jmDNS";
    private static final String JMDNS_CLASS_FIELD = "jmDNSClass";
    private static final String SERVICE_INFO_CLASS_FIELD = "serviceInfoClass";

    @Test
    @Timeout(20)
    void advertisesAndUnadvertisesServiceWithJmDns() {
        final MulticastDnsAdvertiser advertiser = new MulticastDnsAdvertiser();
        final Map<String, String> properties = new HashMap<>();
        properties.put("name", "log4j-core-test-" + UUID.randomUUID());
        properties.put("protocol", "tcp");
        properties.put("port", "4555");
        properties.put("description", "Log4j multicast DNS advertiser integration test");

        final Object serviceInfo = advertiser.advertise(properties);

        try {
            assertThat(serviceInfo).isInstanceOf(ServiceInfo.class);
        } finally {
            if (serviceInfo != null) {
                advertiser.unadvertise(serviceInfo);
            }
        }
    }

    @Test
    @Timeout(20)
    void advertisesAndUnadvertisesServiceWithLegacyJmDnsApi() throws Exception {
        final Object previousJmDns = getStaticField(JMDNS_FIELD);
        final Object previousJmDnsClass = getStaticField(JMDNS_CLASS_FIELD);
        final Object previousServiceInfoClass = getStaticField(SERVICE_INFO_CLASS_FIELD);
        try {
            setStaticField(JMDNS_CLASS_FIELD, LegacyJmDns.class);
            setStaticField(SERVICE_INFO_CLASS_FIELD, LegacyServiceInfo.class);
            final LegacyJmDns legacyJmDns = (LegacyJmDns) invokeStatic("createJmDnsVersion1");
            setStaticField(JMDNS_FIELD, legacyJmDns);

            final MulticastDnsAdvertiser advertiser = new MulticastDnsAdvertiser();
            final Map<String, String> properties = new HashMap<>();
            properties.put("name", "log4j-core-legacy-test-" + UUID.randomUUID());
            properties.put("protocol", "tcp");
            properties.put("port", "4556");
            properties.put("description", "Log4j legacy multicast DNS advertiser integration test");

            final Object serviceInfo = advertiser.advertise(properties);

            assertThat(serviceInfo).isInstanceOf(LegacyServiceInfo.class);
            assertThat(legacyJmDns.registeredServiceInfo).isSameAs(serviceInfo);

            advertiser.unadvertise(serviceInfo);

            assertThat(legacyJmDns.unregisteredServiceInfo).isSameAs(serviceInfo);
        } finally {
            setStaticField(JMDNS_FIELD, previousJmDns);
            setStaticField(JMDNS_CLASS_FIELD, previousJmDnsClass);
            setStaticField(SERVICE_INFO_CLASS_FIELD, previousServiceInfoClass);
        }
    }

    private static Object getStaticField(final String fieldName) throws Exception {
        final Field field = MulticastDnsAdvertiser.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(null);
    }

    private static void setStaticField(final String fieldName, final Object value) throws Exception {
        final Field field = MulticastDnsAdvertiser.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static Object invokeStatic(final String methodName) throws Exception {
        final Method method = MulticastDnsAdvertiser.class.getDeclaredMethod(methodName);
        method.setAccessible(true);
        try {
            return method.invoke(null);
        } catch (InvocationTargetException exception) {
            final Throwable cause = exception.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw exception;
        }
    }

    public static final class LegacyJmDns {
        private LegacyServiceInfo registeredServiceInfo;
        private LegacyServiceInfo unregisteredServiceInfo;

        public LegacyJmDns() {
        }

        public void registerService(final LegacyServiceInfo serviceInfo) {
            registeredServiceInfo = serviceInfo;
        }

        public void unregisterService(final LegacyServiceInfo serviceInfo) {
            unregisteredServiceInfo = serviceInfo;
        }
    }

    public static final class LegacyServiceInfo {
        private final String zone;
        private final String name;
        private final int port;
        private final Hashtable<String, String> properties;

        public LegacyServiceInfo(final String zone, final String name, final int port, final int weight,
                final int priority, final Hashtable<String, String> properties) {
            this.zone = zone;
            this.name = name;
            this.port = port;
            this.properties = properties;
        }

        public String getZone() {
            return zone;
        }

        public String getName() {
            return name;
        }

        public int getPort() {
            return port;
        }

        public Hashtable<String, String> getProperties() {
            return properties;
        }
    }
}
