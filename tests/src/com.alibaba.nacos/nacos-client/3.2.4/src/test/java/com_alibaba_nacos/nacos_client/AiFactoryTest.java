/*
 * Copyright and related rights waived via CC0
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.alibaba.nacos.api.ai.AiFactory;
import com.alibaba.nacos.api.ai.AiService;
import com.alibaba.nacos.api.exception.NacosException;
import org.junit.jupiter.api.Test;

public class AiFactoryTest {
    @Test
    void createsAnAiServiceThatValidatesRequiredMcpNames() throws Exception {
        try (ConfigRpcServer server = new ConfigRpcServer()) {
            AiService service = AiFactory.createAiService(server.properties());
            try {
                NacosException failure = assertThrows(NacosException.class, () -> service.getMcpServer("", null));
                assertEquals(NacosException.INVALID_PARAM, failure.getErrCode());
            } finally {
                service.shutdown();
            }
        }
    }
}
