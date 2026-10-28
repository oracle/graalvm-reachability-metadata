/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.google.common.reflect.Invokable;
import org.junit.jupiter.api.Test;

public class InvokableInnerConstructorInvokableTest {
    @Test
    void invokesRepresentedConstructor() throws Exception {
        Invokable<Message, Message> constructor = Invokable.from(Message.class.getConstructor(String.class));

        Message message = constructor.invoke(null, "created");

        assertThat(message.value).isEqualTo("created");
        assertThat(constructor.getReturnType().getRawType()).isEqualTo(Message.class);
    }

    public static class Message {
        private final String value;

        public Message(String value) {
            this.value = value;
        }
    }
}
