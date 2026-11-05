/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.google.common.reflect.Invokable;
import java.util.Locale;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class InvokableInnerMethodInvokableTest {
    @Test
    void exercisesPublicApi() throws Exception {
        Invokable<?, Object> method = Invokable.from(Message.class.getMethod("uppercase"));
        @SuppressWarnings("unchecked")
        Invokable<Message, Object> callable = (Invokable<Message, Object>) method;
        assertEquals("HELLO", callable.invoke(new Message("hello")));
    }

    public static class Message {
        private final String text;

        public Message(String text) {
            this.text = text;
        }

        public String uppercase() {
            return text.toUpperCase(Locale.ROOT);
        }
    }
}
