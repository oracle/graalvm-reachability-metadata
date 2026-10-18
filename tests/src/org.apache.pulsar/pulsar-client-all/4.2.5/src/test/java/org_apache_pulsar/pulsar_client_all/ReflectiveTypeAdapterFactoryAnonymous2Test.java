/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.google.gson.Gson;
import org.junit.jupiter.api.Test;

public class ReflectiveTypeAdapterFactoryAnonymous2Test {

    @Test
    void readsPojoFieldsWhileWritingJson() {
        final Message message = new Message("orders", 7);

        final String json = new Gson().toJson(message);

        assertThat(json).contains("\"topic\":\"orders\"");
        assertThat(json).contains("\"sequence\":7");
    }

    public static final class Message {
        private final String topic;
        private final int sequence;

        public Message(String topic, int sequence) {
            this.topic = topic;
            this.sequence = sequence;
        }
    }
}
