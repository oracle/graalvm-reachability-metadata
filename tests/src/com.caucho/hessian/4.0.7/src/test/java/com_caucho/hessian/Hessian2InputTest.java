/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.hessian.io.Hessian2Input;
import com.caucho.hessian.io.Hessian2Output;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class Hessian2InputTest {
    @Test
    void propagatesRemoteExceptionWithWireMessageAndDetail() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Hessian2Output output = new Hessian2Output(bytes);
        output.writeFault("ServiceException", "remote validation", new ValidationException("local validation", 73));
        output.close();
        Hessian2Input input = new Hessian2Input(new ByteArrayInputStream(bytes.toByteArray()));
        try {
            assertEquals('H', input.read());
            assertEquals(2, input.read());
            assertEquals(0, input.read());
            ValidationException failure = assertThrows(ValidationException.class, () -> input.readReply(Object.class));
            assertEquals("remote validation", failure.getMessage());
            assertEquals(73, failure.code);
        } finally {
            input.close();
        }
    }

    public static class ValidationException extends RuntimeException {
        private int code;

        public ValidationException(String message, int code) {
            super(message);
            this.code = code;
        }
    }
}
