/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty_websocket.jetty_websocket_jetty_api;

import org.eclipse.jetty.websocket.api.Frame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FrameInnerTypeTest {
    @Test
    void decodesAndClassifiesWireOpcodes() {
        Frame.Type text = Frame.Type.from((byte) 0x01);
        Frame.Type binary = Frame.Type.from((byte) 0x02);
        Frame.Type continuation = Frame.Type.from((byte) 0x00);
        assertEquals(Frame.Type.TEXT, text);
        assertEquals(Frame.Type.BINARY, binary);
        assertTrue(text.isData());
        assertTrue(binary.isData());
        assertFalse(text.isControl());
        assertFalse(text.isContinuation());
        assertTrue(continuation.isContinuation());
        assertFalse(continuation.isData());
        assertFalse(continuation.isControl());

        for (byte opcode : new byte[]{0x08, 0x09, 0x0A}) {
            Frame.Type control = Frame.Type.from(opcode);
            assertEquals(opcode, control.getOpCode());
            assertTrue(control.isControl());
            assertFalse(control.isData());
            assertFalse(control.isContinuation());
        }
    }

    @Test
    void rejectsReservedOpcodes() {
        assertThrows(IllegalArgumentException.class, () -> Frame.Type.from((byte) 0x03));
        assertThrows(IllegalArgumentException.class, () -> Frame.Type.from((byte) 0x0F));
    }
}
