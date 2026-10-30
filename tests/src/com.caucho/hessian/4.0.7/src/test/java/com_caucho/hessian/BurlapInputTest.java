/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.burlap.io.BurlapInput;
import com.caucho.burlap.io.BurlapOutput;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BurlapInputTest {
    @Test
    void propagatesRemoteExceptionWithWireMessageAndDetail() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        BurlapOutput output = new BurlapOutput(bytes);
        output.startReply();
        output.writeFault("ServiceException", "remote validation", new ValidationException("local validation", 73));
        output.completeReply();
        output.close();
        BurlapInput input = new BurlapInput(new ByteArrayInputStream(bytes.toByteArray()));
        try {
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
