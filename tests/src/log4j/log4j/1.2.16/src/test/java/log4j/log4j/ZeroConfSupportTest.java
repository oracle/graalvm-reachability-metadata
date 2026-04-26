/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package log4j.log4j;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

import org.apache.log4j.net.ZeroConfSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ZeroConfSupportTest {
    @Test
    void advertisesAndUnadvertisesServicesWithVersion1JmDNS() {
        JmDNS jmDNS = new JmDNS();
        setZeroConfState(JmDNS.class, ServiceInfo.class, jmDNS);

        Map<String, String> properties = new HashMap<>();
        properties.put("scope", "integration-test");

        ZeroConfSupport zeroConfSupport = new ZeroConfSupport("_log4j._tcp.local.", 4567, "metadata-forge", properties);
        zeroConfSupport.advertise();

        assertThat(jmDNS.getRegisteredServices()).singleElement().satisfies(service -> {
            assertThat(service.zone()).isEqualTo("_log4j._tcp.local.");
            assertThat(service.name()).isEqualTo("metadata-forge");
            assertThat(service.port()).isEqualTo(4567);
            assertThat(service.weight()).isZero();
            assertThat(service.priority()).isZero();
            assertThat(service.properties()).containsEntry("scope", "integration-test");
        });

        zeroConfSupport.unadvertise();

        assertThat(jmDNS.getRegisteredServices()).isEmpty();
    }

    @Test
    void advertisesAndUnadvertisesServicesWithVersion3JmDNS() {
        setStaticField("jmDNSClass", JmDNSVersion3.class);
        setStaticField("serviceInfoClass", ServiceInfoVersion3.class);
        JmDNSVersion3 jmDNS = invokePrivateStaticMethod("createJmDNSVersion3", JmDNSVersion3.class);
        setStaticField("jmDNS", jmDNS);

        Map<String, String> properties = new HashMap<>();
        properties.put("mode", "version3");

        ZeroConfSupport zeroConfSupport = new ZeroConfSupport("_log4j._tcp.local.", 9876, "version3-service", properties);
        zeroConfSupport.advertise();

        assertThat(jmDNS.getRegisteredServices()).singleElement().satisfies(service -> {
            assertThat(service.zone()).isEqualTo("_log4j._tcp.local.");
            assertThat(service.name()).isEqualTo("version3-service");
            assertThat(service.port()).isEqualTo(9876);
            assertThat(service.weight()).isZero();
            assertThat(service.priority()).isZero();
            assertThat(service.properties()).containsEntry("mode", "version3");
        });

        zeroConfSupport.unadvertise();

        assertThat(jmDNS.getRegisteredServices()).isEmpty();
    }

    private static void setZeroConfState(Class<?> jmDNSClass, Class<?> serviceInfoClass, Object jmDNSInstance) {
        setStaticField("jmDNSClass", jmDNSClass);
        setStaticField("serviceInfoClass", serviceInfoClass);
        setStaticField("jmDNS", jmDNSInstance);
    }

    private static void setStaticField(String name, Object value) {
        try {
            Field field = ZeroConfSupport.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(null, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static <T> T invokePrivateStaticMethod(String name, Class<T> type) {
        try {
            Method method = ZeroConfSupport.class.getDeclaredMethod(name);
            method.setAccessible(true);
            Object value = method.invoke(null);
            return type.cast(value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    public static final class JmDNSVersion3 {
        private final List<ServiceInfoVersion3> registeredServices = new java.util.ArrayList<>();

        public static JmDNSVersion3 create() {
            return new JmDNSVersion3();
        }

        public void registerService(ServiceInfoVersion3 serviceInfo) {
            registeredServices.add(serviceInfo);
        }

        public void unregisterService(ServiceInfoVersion3 serviceInfo) {
            registeredServices.remove(serviceInfo);
        }

        public List<ServiceInfoVersion3> getRegisteredServices() {
            return registeredServices;
        }
    }

    public static final class ServiceInfoVersion3 {
        private final String zone;
        private final String name;
        private final int port;
        private final int weight;
        private final int priority;
        private final Map<?, ?> properties;

        private ServiceInfoVersion3(String zone, String name, int port, int weight, int priority, Map<?, ?> properties) {
            this.zone = zone;
            this.name = name;
            this.port = port;
            this.weight = weight;
            this.priority = priority;
            this.properties = properties;
        }

        public static ServiceInfoVersion3 create(String zone, String name, int port, int weight, int priority, Map<?, ?> properties) {
            return new ServiceInfoVersion3(zone, name, port, weight, priority, properties);
        }

        public String zone() {
            return zone;
        }

        public String name() {
            return name;
        }

        public int port() {
            return port;
        }

        public int weight() {
            return weight;
        }

        public int priority() {
            return priority;
        }

        public Map<?, ?> properties() {
            return properties;
        }
    }
}
