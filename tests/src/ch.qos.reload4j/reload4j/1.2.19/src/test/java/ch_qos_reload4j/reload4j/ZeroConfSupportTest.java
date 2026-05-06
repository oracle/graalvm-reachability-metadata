/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

import org.apache.log4j.net.ZeroConfSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ZeroConfSupportTest {
    @Test
    void constructsAdvertisesAndUnadvertisesUsingJmDns3Api() {
        Map properties = new HashMap();
        properties.put("application", "reload4j-test");

        ZeroConfSupport zeroConfSupport = new ZeroConfSupport("_log4j._tcp.local.", 4560, "reload4j", properties);
        Object jmDNSInstance = ZeroConfSupport.getJMDNSInstance();

        assertThat(jmDNSInstance).isInstanceOf(JmDNS.class);
        JmDNS jmDNS = (JmDNS) jmDNSInstance;

        zeroConfSupport.advertise();

        ServiceInfo registeredService = jmDNS.getRegisteredService();
        assertThat(registeredService).isNotNull();
        assertThat(registeredService.getType()).isEqualTo("_log4j._tcp.local.");
        assertThat(registeredService.getName()).isEqualTo("reload4j");
        assertThat(registeredService.getPort()).isEqualTo(4560);
        assertThat(registeredService.getProperties()).containsEntry("application", "reload4j-test");

        zeroConfSupport.unadvertise();

        assertThat(jmDNS.getRegisteredService()).isNull();
    }

    @Test
    void constructsAdvertisesAndUnadvertisesUsingLegacyJmDns1Api() throws Exception {
        Field jmDNSClassField = getZeroConfSupportField("jmDNSClass");
        Field serviceInfoClassField = getZeroConfSupportField("serviceInfoClass");
        Field jmDNSField = getZeroConfSupportField("jmDNS");
        Object originalJmDNSClass = jmDNSClassField.get(null);
        Object originalServiceInfoClass = serviceInfoClassField.get(null);
        Object originalJmDNS = jmDNSField.get(null);

        try {
            jmDNSClassField.set(null, LegacyJmDNS.class);
            serviceInfoClassField.set(null, LegacyServiceInfo.class);
            Method createJmDNSVersion1Method = ZeroConfSupport.class.getDeclaredMethod("createJmDNSVersion1");
            createJmDNSVersion1Method.setAccessible(true);
            Object legacyJmDNS = createJmDNSVersion1Method.invoke(null);
            jmDNSField.set(null, legacyJmDNS);

            Map properties = new HashMap();
            properties.put("application", "legacy-reload4j-test");

            ZeroConfSupport zeroConfSupport = new ZeroConfSupport(
                    "_log4j._tcp.local.",
                    4561,
                    "legacy-reload4j",
                    properties);

            assertThat(legacyJmDNS).isInstanceOf(LegacyJmDNS.class);
            LegacyJmDNS jmDNS = (LegacyJmDNS) legacyJmDNS;

            zeroConfSupport.advertise();

            assertThat(jmDNS.getRegisteredService()).isInstanceOf(LegacyServiceInfo.class);
            LegacyServiceInfo registeredService = (LegacyServiceInfo) jmDNS.getRegisteredService();
            assertThat(registeredService.getZone()).isEqualTo("_log4j._tcp.local.");
            assertThat(registeredService.getName()).isEqualTo("legacy-reload4j");
            assertThat(registeredService.getPort()).isEqualTo(4561);
            assertThat(registeredService.getProperties()).containsEntry("application", "legacy-reload4j-test");

            zeroConfSupport.unadvertise();

            assertThat(jmDNS.getRegisteredService()).isNull();
        } finally {
            jmDNSClassField.set(null, originalJmDNSClass);
            serviceInfoClassField.set(null, originalServiceInfoClass);
            jmDNSField.set(null, originalJmDNS);
        }
    }

    private static Field getZeroConfSupportField(String fieldName) throws NoSuchFieldException {
        Field field = ZeroConfSupport.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field;
    }

    public static final class LegacyJmDNS {
        private Object registeredService;

        public LegacyJmDNS() {
        }

        public void registerService(LegacyServiceInfo serviceInfo) {
            registeredService = serviceInfo;
        }

        public void unregisterService(LegacyServiceInfo serviceInfo) {
            if (registeredService == serviceInfo) {
                registeredService = null;
            }
        }

        public Object getRegisteredService() {
            return registeredService;
        }
    }

    public static final class LegacyServiceInfo {
        private final String zone;
        private final String name;
        private final int port;
        private final Hashtable properties;

        public LegacyServiceInfo(
                String zone,
                String name,
                int port,
                int weight,
                int priority,
                Hashtable properties) {
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

        public Hashtable getProperties() {
            return properties;
        }
    }
}
