/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_ws.rt_fi;

import com.sun.xml.ws.api.pipe.Codec;
import com.sun.xml.ws.encoding.fastinfoset.FastInfosetCodec;
import com.sun.xml.ws.encoding.fastinfoset.FastInfosetMIMETypes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class FastInfosetCodecTest {
    @Test
    void copiesCodecsWhilePreservingTheirEncodingMode() {
        assertIndependentCopy(FastInfosetCodec.create(), FastInfosetMIMETypes.INFOSET);
        assertIndependentCopy(FastInfosetCodec.create(true), FastInfosetMIMETypes.STATEFUL_INFOSET);
    }

    private static void assertIndependentCopy(FastInfosetCodec original, String expectedMimeType) {
        Codec copy = original.copy();

        assertInstanceOf(FastInfosetCodec.class, copy);
        assertNotSame(original, copy, "A copied codec must have independent parser and serializer state");
        assertEquals(expectedMimeType, original.getMimeType());
        assertEquals(expectedMimeType, copy.getMimeType());
    }
}
