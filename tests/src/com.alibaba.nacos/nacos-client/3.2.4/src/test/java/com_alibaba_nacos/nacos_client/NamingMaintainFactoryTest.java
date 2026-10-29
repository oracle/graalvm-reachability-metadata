/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.naming.NamingMaintainFactory;
import com.alibaba.nacos.api.naming.NamingMaintainService;
import com.alibaba.nacos.api.naming.pojo.Service;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.junit.jupiter.api.Test;

public class NamingMaintainFactoryTest {
    @Test
    void queriesServiceDefinitionsUsingEitherFactoryOverload() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/nacos/v1/ns/service", exchange -> {
            byte[] body = """
                    {"name":"jobs","groupName":"metadata","protectThreshold":0.5,"metadata":{"owner":"test"}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        try {
            String address = "127.0.0.1:" + server.getAddress().getPort();
            Properties properties = TestSupport.clientProperties();
            properties.setProperty(PropertyKeyConst.SERVER_ADDR, address);
            NamingMaintainService configured = NamingMaintainFactory.createMaintainService(properties);
            try {
                assertService(configured.queryService("jobs", "metadata"));
            } finally {
                configured.shutDown();
            }
            NamingMaintainService addressed = NamingMaintainFactory.createMaintainService(address);
            try {
                assertService(addressed.queryService("jobs", "metadata"));
            } finally {
                addressed.shutDown();
            }
        } finally {
            server.stop(0);
        }
    }

    private static void assertService(Service service) {
        assertEquals("jobs", service.getName());
        assertEquals("metadata", service.getGroupName());
        assertEquals(0.5f, service.getProtectThreshold());
        assertEquals("test", service.getMetadata().get("owner"));
    }
}
