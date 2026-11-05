/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.ObjectMapper;
import javax.xml.datatype.XMLGregorianCalendar;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OptionalHandlerFactoryTest {
    @Test
    void mapsApplicationData() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        XMLGregorianCalendar result = mapper.readValue("\"2025-03-04T12:30:00Z\"", XMLGregorianCalendar.class);
        assertEquals(2025, result.getYear());
        assertEquals(3, result.getMonth());
        assertEquals(4, result.getDay());
        XMLGregorianCalendar restored = mapper.readValue(mapper.writeValueAsString(result), XMLGregorianCalendar.class);
        assertEquals(result.toGregorianCalendar().getTimeInMillis(), restored.toGregorianCalendar().getTimeInMillis());
    }
}
