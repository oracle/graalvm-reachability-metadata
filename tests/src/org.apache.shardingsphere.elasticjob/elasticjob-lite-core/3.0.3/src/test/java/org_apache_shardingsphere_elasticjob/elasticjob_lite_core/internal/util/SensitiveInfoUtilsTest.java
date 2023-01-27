/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_shardingsphere_elasticjob.elasticjob_lite_core.internal.util;

import org.apache.shardingsphere.elasticjob.lite.internal.util.SensitiveInfoUtils;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public final class SensitiveInfoUtilsTest {
    @Test
    public void assertFilterContentWithoutIp() {
        List<String> actual = Arrays.asList("/simpleElasticDemoJob/servers", "/simpleElasticDemoJob/leader");
        MatcherAssert.assertThat(SensitiveInfoUtils.filterSensitiveIps(actual), is(actual));
    }

    @Test
    public void assertFilterContentWithSensitiveIp() {
        List<String> actual = Arrays.asList("/simpleElasticDemoJob/servers/127.0.0.1", "/simpleElasticDemoJob/servers/192.168.0.1/hostName | 192.168.0.1",
                "/simpleElasticDemoJob/servers/192.168.0.11", "/simpleElasticDemoJob/servers/192.168.0.111");
        List<String> expected = Arrays.asList("/simpleElasticDemoJob/servers/ip1", "/simpleElasticDemoJob/servers/ip2/hostName | ip2",
                "/simpleElasticDemoJob/servers/ip3", "/simpleElasticDemoJob/servers/ip4");
        assertThat(SensitiveInfoUtils.filterSensitiveIps(actual), is(expected));
    }
}
