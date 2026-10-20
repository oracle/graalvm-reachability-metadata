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

public class InvokableInnerMethodInvokableTest {
    @Test
    @SuppressWarnings("unchecked")
    void invokesRepresentedInstanceMethod() throws Exception {
        Invokable<Message, Object> method = (Invokable<Message, Object>) (Invokable<?, ?>)
                Invokable.from(Message.class.getMethod("decorate", String.class));

        Object result = method.invoke(new Message("payload"), "received");

        assertThat(result).isEqualTo("received:payload");
        assertThat(method.getName()).isEqualTo("decorate");
    }

    public static class Message {
        private final String value;

        public Message(String value) {
            this.value = value;
        }

        public String decorate(String prefix) {
            return prefix + ":" + value;
        }
    }
}
