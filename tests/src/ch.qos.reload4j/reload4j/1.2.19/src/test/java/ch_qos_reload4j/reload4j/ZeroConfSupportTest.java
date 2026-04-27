/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import javax.jmdns.JmDNS;

import org.apache.log4j.net.ZeroConfSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

public class ZeroConfSupportTest {
    private static final String SERVICE_TYPE = "_reload4j-test._tcp.local.";

    @AfterAll
    public static void closeSharedJmDns() throws IOException {
        Object jmDns = ZeroConfSupport.getJMDNSInstance();
        if (jmDns instanceof JmDNS) {
            ((JmDNS) jmDns).close();
        }
    }

    @Test
    public void shouldAdvertiseAndUnadvertiseServiceWithJmDns() {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("library", "reload4j");
        properties.put("scenario", "zero-conf-support");

        ZeroConfSupport support = new ZeroConfSupport(SERVICE_TYPE, 12345, uniqueServiceName(), properties);

        assertThat(ZeroConfSupport.getJMDNSInstance()).isInstanceOf(JmDNS.class);
        support.advertise();
        support.unadvertise();
    }

    private static String uniqueServiceName() {
        return "reload4j-" + UUID.randomUUID();
    }
}
