/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_shardingsphere_elasticjob.elasticjob_lite_core.internal.config;

import org.apache.shardingsphere.elasticjob.lite.internal.config.ConfigurationNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public final class ConfigurationNodeTest {
    private final ConfigurationNode configurationNode = new ConfigurationNode("test_job");

    @Test
    public void assertIsConfigPath() {
        Assertions.assertTrue(configurationNode.isConfigPath("/test_job/config"));
    }
}
