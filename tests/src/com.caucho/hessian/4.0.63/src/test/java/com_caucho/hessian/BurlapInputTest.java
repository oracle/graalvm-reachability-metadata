/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.burlap.io.BurlapInput;
import com.caucho.burlap.io.BurlapOutput;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(120)
public class BurlapInputTest {
    @Test
    void decodesServiceFaultWithTypedDetail() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        BurlapOutput output = new BurlapOutput(bytes);
        output.startReply();
        output.writeFault("ServiceException", "snapshot unavailable",
                new FaultSupport.SnapshotException("snapshot unavailable", 17));
        output.completeReply();
        output.flush();
        BurlapInput input = new BurlapInput(new ByteArrayInputStream(bytes.toByteArray()));
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

    @Test
    void usesFaultEnvelopeMessageForTypedException() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        BurlapOutput output = new BurlapOutput(bytes);
        output.startReply();
        output.writeFault("ServiceException", "snapshot unavailable",
                new FaultSupport.SnapshotException("internal detail", 23));
        output.completeReply();
        output.flush();
        BurlapInput input = new BurlapInput(new ByteArrayInputStream(bytes.toByteArray()));
        try {
            FaultSupport.SnapshotException exception = assertThrows(FaultSupport.SnapshotException.class,
                    () -> input.readReply(String.class));
            assertEquals("snapshot unavailable", exception.getMessage());
            assertEquals(23, exception.getTerm());
        } finally {
            input.close();
            output.close();
        }
    }
}
