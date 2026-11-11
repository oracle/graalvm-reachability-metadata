/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.hessian.io.Hessian2Input;
import com.caucho.hessian.io.Hessian2Output;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(120)
public class Hessian2InputTest {
    @Test
    void decodesServiceFaultWithTypedDetail() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Hessian2Output output = new Hessian2Output(bytes);
        output.writeFault("ServiceException", "snapshot unavailable",
                new FaultSupport.SnapshotException("snapshot unavailable", 17));
        output.flush();
        Hessian2Input input = new Hessian2Input(new ByteArrayInputStream(bytes.toByteArray()));
        try {
            assertEquals('H', input.read());
            assertEquals(2, input.read());
            assertEquals(0, input.read());
            FaultSupport.SnapshotException exception = assertThrows(FaultSupport.SnapshotException.class,
                    () -> input.readReply(String.class));
            assertEquals("snapshot unavailable", exception.getMessage());
            assertEquals(17, exception.getTerm());
        } finally {
            input.close();
            output.close();
        }
    }
}
