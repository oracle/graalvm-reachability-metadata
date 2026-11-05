/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_avro;

import org.apache.avro.Protocol;
import org.apache.avro.Schema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/// Exercises Avro's protocol definition subsystem to load a service contract for an application client.
public class ProtocolCatalogTest {
    @Test
    @Timeout(60)
    public void loadsOrderServiceContract() {
        Protocol protocol = Protocol.parse("""
                {
                  "protocol": "OrderService",
                  "namespace": "com.acme.orders",
                  "doc": "Operations for an order service.",
                  "types": [
                    {
                      "type": "record",
                      "name": "OrderRequest",
                      "fields": [
                        {"name": "orderId", "type": "string"},
                        {"name": "includeItems", "type": "boolean", "default": true}
                      ]
                    },
                    {
                      "type": "record",
                      "name": "OrderSummary",
                      "fields": [
                        {"name": "orderId", "type": "string"},
                        {"name": "status", "type": "string"}
                      ]
                    }
                  ],
                  "messages": {
                    "getOrder": {
                      "doc": "Loads one order summary.",
                      "request": [
                        {"name": "request", "type": "OrderRequest"}
                      ],
                      "response": "OrderSummary",
                      "errors": []
                    }
                  }
                }
                """);

        Protocol.Message getOrder = protocol.getMessages().get("getOrder");
        Schema request = protocol.getType("OrderRequest");
        Schema response = protocol.getType("OrderSummary");

        assertEquals("OrderService", protocol.getName());
        assertEquals("com.acme.orders", protocol.getNamespace());
        assertEquals("Operations for an order service.", protocol.getDoc());
        assertEquals(2, protocol.getTypes().size());
        assertNotNull(getOrder);
        assertEquals("Loads one order summary.", getOrder.getDoc());
        assertFalse(getOrder.isOneWay());
        assertEquals("request", getOrder.getRequest().getField("request").name());
        assertEquals("OrderRequest", getOrder.getRequest().getField("request").schema().getName());
        assertEquals("OrderSummary", getOrder.getResponse().getName());
        assertEquals(Schema.Type.UNION, getOrder.getErrors().getType());
        assertEquals(1, getOrder.getErrors().getTypes().size());
        assertEquals("boolean", request.getField("includeItems").schema().getType().getName());
        assertEquals("string", response.getField("status").schema().getType().getName());
    }
}
