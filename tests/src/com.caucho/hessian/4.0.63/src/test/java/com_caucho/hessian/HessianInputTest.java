/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.hessian.io.HessianInput;
import com.caucho.hessian.io.HessianOutput;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(120)
public class HessianInputTest {
    @Test
    void decodesServiceFaultWithTypedDetail() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        HessianOutput output = new HessianOutput(bytes);
        output.writeFault("ServiceException", "snapshot unavailable",
                new FaultSupport.SnapshotException("snapshot unavailable", 17));
        output.flush();
        HessianInput input = new HessianInput(new ByteArrayInputStream(bytes.toByteArray()));
        try {
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
