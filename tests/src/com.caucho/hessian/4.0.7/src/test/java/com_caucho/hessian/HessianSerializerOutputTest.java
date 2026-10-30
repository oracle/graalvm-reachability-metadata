/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.hessian.io.Hessian2Input;
import com.caucho.hessian.io.HessianSerializerOutput;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Serializable;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HessianSerializerOutputTest {
    @Test
    void writesApplicationFieldsThroughSerializerOutputApi() throws Exception {
        assertEquals("outgoing", ((Message) writeAndRead(new Message("outgoing"))).text);
    }

    @Test
    void honorsPublicWriteReplacement() throws Exception {
        assertEquals("replacement", writeAndRead(new Replacement()));
    }

    private Object writeAndRead(Object value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        HessianSerializerOutput output = new HessianSerializerOutput(bytes);
        output.writeObjectImpl(value);
        output.close();
        Hessian2Input input = new Hessian2Input(new ByteArrayInputStream(bytes.toByteArray()));
        try {
            return input.readObject();
        } finally {
            input.close();
        }
    }

    public static class Message implements Serializable {
        private String text;

        public Message(String text) {
            this.text = text;
        }
    }

    public static class Replacement {
        public Object writeReplace() {
            return "replacement";
        }
    }
}
