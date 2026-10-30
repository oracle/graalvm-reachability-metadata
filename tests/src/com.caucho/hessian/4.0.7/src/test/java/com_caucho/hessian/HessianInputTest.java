/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.hessian.io.HessianInput;
import com.caucho.hessian.io.HessianOutput;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class HessianInputTest {
    @Test
    void propagatesRemoteExceptionWithWireMessageAndDetail() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        HessianOutput output = new HessianOutput(bytes);
        output.writeFault("ServiceException", "remote validation", new ValidationException("local validation", 73));
        output.close();
        HessianInput input = new HessianInput(new ByteArrayInputStream(bytes.toByteArray()));
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
