/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_ws.rt_fi;

import com.sun.xml.ws.encoding.fastinfoset.FastInfosetStreamReaderFactory;
import java.io.ByteArrayInputStream;
import javax.xml.stream.XMLStreamReader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

public class FastInfosetStreamReaderFactoryTest {
    @Test
    void reusesRecycledReadersFromItsThreadLocalPool() throws Exception {
        FastInfosetStreamReaderFactory factory = FastInfosetStreamReaderFactory.getInstance();

        XMLStreamReader firstReader = factory.doCreate(
                null, new ByteArrayInputStream(new byte[0]), false);
        XMLStreamReader secondReader = factory.doCreate(
                null, new ByteArrayInputStream(new byte[0]), false);
        assertNotSame(firstReader, secondReader, "An empty pool must create an independent reader");

        firstReader.close();
        factory.doRecycle(firstReader);
        XMLStreamReader recycledReader = factory.doCreate(
                null, new ByteArrayInputStream(new byte[0]), false);

        assertSame(firstReader, recycledReader, "The factory must reuse its thread-local reader");
        recycledReader.close();
        secondReader.close();
    }
}
