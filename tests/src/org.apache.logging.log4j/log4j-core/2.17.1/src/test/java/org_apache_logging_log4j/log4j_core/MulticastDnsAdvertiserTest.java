/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.jmdns.ServiceInfo;

import org.apache.logging.log4j.core.net.MulticastDnsAdvertiser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class MulticastDnsAdvertiserTest {
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
}
