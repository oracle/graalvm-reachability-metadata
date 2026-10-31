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

public class InvokableInnerConstructorInvokableTest {
    @Test
    void exercisesPublicApi() throws Exception {
        Invokable<?, Message> constructor = Invokable.from(Message.class.getConstructor(String.class));
        Message message = constructor.invoke(null, "hello");
        assertEquals("hello", message.text);
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
