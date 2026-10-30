/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.hessian.io.Hessian2Output;
import com.caucho.hessian.io.HessianSerializerInput;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Serializable;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HessianSerializerInputTest {
    @Test
    void readsApplicationObjectFromHessianStream() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Hessian2Output output = new Hessian2Output(bytes);
        output.writeObject(new Message("incoming"));
        output.close();
        HessianSerializerInput input = new HessianSerializerInput(new ByteArrayInputStream(bytes.toByteArray()));
        try {
            Message restored = (Message) input.readObject(Message.class);
            assertEquals("incoming", restored.text);
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
}
