/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import org.junit.jupiter.api.Test;

public class Nacos_clientTest {
    @Test
    void usesTheNacosFacadeToPublishTypedConfiguration() throws Exception {
        try (ConfigRpcServer server = new ConfigRpcServer()) {
            ConfigService service = NacosFactory.createConfigService(
                    server.properties().getProperty(PropertyKeyConst.SERVER_ADDR));
            try {
                assertTrue(service.publishConfig("settings", "metadata", "enabled: true", "yaml"));
                assertEquals("enabled: true", service.getConfig("settings", "metadata", 30000));
            } finally {
                service.shutDown();
            }
        }
    }
}
