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

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

import org.apache.log4j.net.ZeroConfSupport;
import org.junit.jupiter.api.Test;

public class ZeroConfSupportTest {
    @Test
    void advertisesAndUnadvertisesServiceThroughJmDnsVersion3Api() {
        JmDNS jmDNS = (JmDNS) ZeroConfSupport.getJMDNSInstance();
        jmDNS.clear();

        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("endpoint", "integration-test");
        properties.put("transport", "tcp");

        ZeroConfSupport zeroConfSupport = new ZeroConfSupport("_log4j._tcp.local.", 4560, "reload4j-test", properties);

        zeroConfSupport.advertise();

        assertThat(jmDNS.getRegisteredServices()).hasSize(1);
        ServiceInfo serviceInfo = jmDNS.getRegisteredServices().get(0);
        assertThat(serviceInfo.getType()).isEqualTo("_log4j._tcp.local.");
        assertThat(serviceInfo.getName()).isEqualTo("reload4j-test");
        assertThat(serviceInfo.getPort()).isEqualTo(4560);
        assertThat(serviceInfo.getWeight()).isZero();
        assertThat(serviceInfo.getPriority()).isZero();
        assertThat(serviceInfo.getProperties()).containsExactlyEntriesOf(properties);

        zeroConfSupport.unadvertise();

        assertThat(jmDNS.getRegisteredServices()).isEmpty();
        assertThat(jmDNS.getUnregisteredServices()).containsExactly(serviceInfo);
    }
}
