/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OptionalHandlerFactoryTest {
    @Test
    void loadsXmlHandlersForAnXmlGregorianCalendarRoundTrip() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        XMLGregorianCalendar input = DatatypeFactory.newInstance().newXMLGregorianCalendar("2024-03-14T12:30:00Z");

        String json = mapper.writeValueAsString(input);
        XMLGregorianCalendar output = mapper.readValue(json, XMLGregorianCalendar.class);

        assertThat(output).isEqualTo(input);
    }
}
