/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import org.junit.jupiter.api.Test;

public class NamingFactoryTest {
    @Test
    void createsServicesThatValidateRegistrationInputs() throws Exception {
        try (ConfigRpcServer server = new ConfigRpcServer()) {
            NamingService configured = NamingFactory.createNamingService(server.properties());
            try {
                NacosException failure = assertThrows(NacosException.class, () -> configured.registerInstance("jobs", null));
                assertEquals(NacosException.INVALID_PARAM, failure.getErrCode());
            } finally {
                configured.shutDown();
            }
            NamingService addressed = NamingFactory.createNamingService(server.properties().getProperty(PropertyKeyConst.SERVER_ADDR));
            try {
                NacosException failure = assertThrows(NacosException.class, () -> addressed.registerInstance("jobs", null));
                assertEquals(NacosException.INVALID_PARAM, failure.getErrCode());
            } finally {
                addressed.shutDown();
            }
        }
    }
}
