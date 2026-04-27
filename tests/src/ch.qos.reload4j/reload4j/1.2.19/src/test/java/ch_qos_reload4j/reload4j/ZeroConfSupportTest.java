/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

import org.apache.log4j.net.ZeroConfSupport;
import org.junit.jupiter.api.Test;

public class ZeroConfSupportTest {
    private static final String SERVICE_TYPE = "_reload4j-test._tcp.local.";

    @Test
    public void shouldAdvertiseAndUnadvertiseServiceWithJmDnsVersionThreeApi() {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("library", "reload4j");
        properties.put("scenario", "zero-conf-support");
        String serviceName = uniqueServiceName();

        ZeroConfSupport support = new ZeroConfSupport(SERVICE_TYPE, 12345, serviceName, properties);
        JmDNS jmDns = (JmDNS) ZeroConfSupport.getJMDNSInstance();

        support.advertise();
        assertThat(jmDns.getRegisteredServices()).hasSize(1);
        ServiceInfo serviceInfo = jmDns.getRegisteredServices().get(0);
        assertThat(serviceInfo.getType()).isEqualTo(SERVICE_TYPE);
        assertThat(serviceInfo.getName()).isEqualTo(serviceName);
        assertThat(serviceInfo.getPort()).isEqualTo(12345);
        assertThat(serviceInfo.getWeight()).isZero();
        assertThat(serviceInfo.getPriority()).isZero();
        assertThat(serviceInfo.getProperties()).containsEntry("library", "reload4j");
        assertThat(serviceInfo.getProperties()).containsEntry("scenario", "zero-conf-support");

        support.unadvertise();
        assertThat(jmDns.getRegisteredServices()).isEmpty();
    }

    private static String uniqueServiceName() {
        return "reload4j-" + UUID.randomUUID();
    }
}
