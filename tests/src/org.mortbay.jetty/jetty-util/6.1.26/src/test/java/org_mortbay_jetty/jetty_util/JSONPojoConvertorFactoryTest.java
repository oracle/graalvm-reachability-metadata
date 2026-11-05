/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mortbay_jetty.jetty_util;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mortbay.util.ajax.JSON;
import org.mortbay.util.ajax.JSONPojoConvertorFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class JSONPojoConvertorFactoryTest {
    @Test
    public void createsAndCachesConvertorsForUnregisteredBeans() {
        JSON json = new JSON();
        json.addConvertor(Object.class, new JSONPojoConvertorFactory(json));
        Endpoint endpoint = new Endpoint();
        endpoint.setHost("localhost");
        endpoint.setPort(8080);
        String document = json.toJSON(endpoint);
        JSON.Convertor cached = json.getConvertorFor(Endpoint.class.getName());
        assertNotNull(cached);
        Endpoint restored = (Endpoint) json.fromJSON(document);
        assertEquals("localhost", restored.getHost());
        assertEquals(8080, restored.getPort());
        assertEquals(document, json.toJSON(restored));
        assertSame(cached, json.getConvertorFor(Endpoint.class.getName()));
    }

    @Test
    public void buildsConvertorWhenDecodingAnUnknownBean() {
        JSON json = new JSON();
        JSONPojoConvertorFactory factory = new JSONPojoConvertorFactory(json);
        Endpoint endpoint = (Endpoint) factory.fromJSON(Map.of(
                "class", Endpoint.class.getName(), "host", "example.test", "port", 8443L));
        assertEquals("example.test", endpoint.getHost());
        assertEquals(8443, endpoint.getPort());
        assertNotNull(json.getConvertorFor(Endpoint.class.getName()));
    }

    public static class Endpoint {
        private String host;
        private int port;

        public Endpoint() {
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }
    }
}
