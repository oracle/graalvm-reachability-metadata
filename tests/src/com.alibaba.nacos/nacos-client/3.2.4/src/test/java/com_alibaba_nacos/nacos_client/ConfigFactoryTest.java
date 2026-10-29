/*
 * Copyright and related rights waived via CC0
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.alibaba.nacos.api.config.ConfigFactory;
import com.alibaba.nacos.api.config.ConfigService;
import org.junit.jupiter.api.Test;

public class ConfigFactoryTest {
    @Test
    void publishesQueriesAndRemovesConfigurationThroughTheServiceApi() throws Exception {
        try (ConfigRpcServer server = new ConfigRpcServer()) {
            ConfigService service = ConfigFactory.createConfigService(server.properties());
            try {
                assertTrue(service.publishConfig("application", "metadata", "replicas=3"));
                assertEquals("replicas=3", server.content("application"));
                assertEquals("replicas=3", service.getConfig("application", "metadata", 30000));
                assertTrue(service.removeConfig("application", "metadata"));
                assertNull(server.content("application"));
                assertNull(service.getConfig("application", "metadata", 30000));
            } finally {
                service.shutDown();
            }
        }
    }
}
