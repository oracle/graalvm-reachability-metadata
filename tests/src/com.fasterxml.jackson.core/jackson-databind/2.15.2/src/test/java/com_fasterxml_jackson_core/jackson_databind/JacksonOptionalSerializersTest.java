/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import java.sql.Date;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;

import javax.sql.rowset.serial.SerialBlob;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import static org.assertj.core.api.Assertions.assertThat;

class JacksonOptionalSerializersTest {

    static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void sqlDateDeserializer() throws JsonProcessingException {
        Date result = mapper.readValue("0", Date.class);
        assertThat(result).isEqualTo(new Date(0));
    }

    @Test
    void sqlTimestampDeserializer() throws JsonProcessingException {
        Timestamp result = mapper.readValue("0", Timestamp.class);
        assertThat(result).isEqualTo(new Timestamp(0));
    }

    @Test
    void sqlDateSerializer() throws JsonProcessingException {
        String result = mapper.writeValueAsString(new Date(0));
        assertThat(result).isEqualTo("0");
    }

    @Test
    void sqlTimeSerializer() throws JsonProcessingException {
        String result = mapper.writeValueAsString(Time.valueOf("00:00:00"));
        assertThat(result).isEqualTo("\"00:00:00\"");
    }

    @Test
    void sqlBlobSerializer() throws JsonProcessingException, SQLException {
        String result = mapper.writeValueAsString(new SerialBlob(new byte[0]));
        assertThat(result).isEqualTo("\"\"");
    }

    @Test
    void nodeSerializer() throws JsonProcessingException, ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.newDocument();
        Node node = document.createElement("p");
        String result = mapper.writeValueAsString(node);
        assertThat(result).isEqualTo("\"<p/>\"");
    }

    @Test
    void documentSerializer() throws JsonProcessingException, ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.newDocument();
        String result = mapper.writeValueAsString(document);
        assertThat(result).isEqualTo("\"\"");
    }

    @Test
    void xmlGregorianCalendarSerializer() throws Exception {
        XMLGregorianCalendar calendar = DatatypeFactory.newInstance().newXMLGregorianCalendar(2023,
                11,30, 20, 50, 40, 200, 0);
        String result = mapper.writeValueAsString(calendar);
        assertThat(result).isEqualTo("1701377440200");
    }

    @Test
    void xmlGregorianCalendarDeserializer() throws Exception {
        XMLGregorianCalendar calendar = mapper.readValue("1701377440200", XMLGregorianCalendar.class);
        assertThat(calendar.getYear()).isEqualTo(2023);
        assertThat(calendar.getMonth()).isEqualTo(11);
        assertThat(calendar.getDay()).isEqualTo(30);
        assertThat(calendar.getMinute()).isEqualTo(50);
        assertThat(calendar.getSecond()).isEqualTo(40);
        assertThat(calendar.getMillisecond()).isEqualTo(200);
        assertThat(calendar.getTimezone()).isEqualTo(0);
    }
}
