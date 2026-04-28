/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_rabbitmq.amqp_client;

import com.rabbitmq.tools.json.JSONWriter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JSONWriterTest {
    @Test
    void writeLimitedSerializesBeanGetterAndDeclaredPublicField() {
        JSONWriter writer = new JSONWriter();
        PublishDescription description = new PublishDescription("orders.created", 2);

        String json = writer.write(description);

        assertThat(json).isEqualTo("{\"routingKey\":\"orders.created\",\"deliveryMode\":2}");
    }

    public static final class PublishDescription {
        public final int deliveryMode;
        private final String routingKey;

        public PublishDescription(String routingKey, int deliveryMode) {
            this.routingKey = routingKey;
            this.deliveryMode = deliveryMode;
        }

        public String getRoutingKey() {
            return routingKey;
        }
    }
}
