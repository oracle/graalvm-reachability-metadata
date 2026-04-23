/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.parsers.DocumentBuilderFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import static org.assertj.core.api.Assertions.assertThat;

public class JacksonOptionalHandlerFactoryTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void optionalHandlerFactoryInstantiatesXmlAndDomHandlers() throws Exception {
        XMLGregorianCalendar calendar = DatatypeFactory.newInstance().newXMLGregorianCalendar(2023, 11, 30, 20, 50, 40, 200, 0);
        String calendarJson = mapper.writeValueAsString(calendar);
        assertThat(calendarJson).isNotBlank();

        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        String documentJson = mapper.writeValueAsString(document);
        assertThat(documentJson).isNotBlank();
    }
}
