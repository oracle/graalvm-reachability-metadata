/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import java.sql.Blob;
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
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.DateDeserializers;
import com.fasterxml.jackson.databind.ext.CoreXMLDeserializers;
import com.fasterxml.jackson.databind.ext.CoreXMLSerializers;
import com.fasterxml.jackson.databind.ext.DOMSerializer;
import com.fasterxml.jackson.databind.ext.SqlBlobSerializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.SqlDateSerializer;
import com.fasterxml.jackson.databind.ser.std.SqlTimeSerializer;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import static org.assertj.core.api.Assertions.assertThat;

public class JacksonOptionalSerializersTest {

    private static final ObjectMapper MAPPER = mapperWithOptionalHandlers();

    @Test
    void sqlDateDeserializer() throws JsonProcessingException {
        Date result = MAPPER.readValue("0", Date.class);
        assertThat(result).isEqualTo(new Date(0));
    }

    @Test
    void sqlTimestampDeserializer() throws JsonProcessingException {
        Timestamp result = MAPPER.readValue("0", Timestamp.class);
        assertThat(result).isEqualTo(new Timestamp(0));
    }

    @Test
    void sqlDateSerializer() throws JsonProcessingException {
        String result = MAPPER.writeValueAsString(new Date(0));
        assertThat(result).isEqualTo("0");
    }

    @Test
    void sqlTimeSerializer() throws JsonProcessingException {
        String result = MAPPER.writeValueAsString(Time.valueOf("00:00:00"));
        assertThat(result).isEqualTo("\"00:00:00\"");
    }

    @Test
    void sqlBlobSerializer() throws JsonProcessingException, SQLException {
        String result = MAPPER.writeValueAsString(new SerialBlob(new byte[0]));
        assertThat(result).isEqualTo("\"\"");
    }

    @Test
    void nodeSerializer() throws JsonProcessingException, ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.newDocument();
        Node node = document.createElement("p");
        String result = MAPPER.writeValueAsString(node);
        assertThat(result).isEqualTo("\"<p/>\"");
    }

    @Test
    void documentSerializer() throws JsonProcessingException, ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.newDocument();
        String result = MAPPER.writeValueAsString(document);
        assertThat(result).isEqualTo("\"\"");
    }

    @Test
    void xmlGregorianCalendarSerializer() throws Exception {
        XMLGregorianCalendar calendar = DatatypeFactory.newInstance().newXMLGregorianCalendar(2023,
                11, 30, 20, 50, 40, 200, 0);
        String result = MAPPER.writeValueAsString(calendar);
        assertThat(result).isEqualTo("1701377440200");
    }

    @Test
    void xmlGregorianCalendarDeserializer() throws Exception {
        XMLGregorianCalendar calendar = MAPPER.readValue("1701377440200", XMLGregorianCalendar.class);
        assertThat(calendar.getYear()).isEqualTo(2023);
        assertThat(calendar.getMonth()).isEqualTo(11);
        assertThat(calendar.getDay()).isEqualTo(30);
        assertThat(calendar.getMinute()).isEqualTo(50);
        assertThat(calendar.getSecond()).isEqualTo(40);
        assertThat(calendar.getMillisecond()).isEqualTo(200);
        assertThat(calendar.getTimezone()).isEqualTo(0);
    }

    private static ObjectMapper mapperWithOptionalHandlers() {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Date.class, new DateDeserializers.SqlDateDeserializer());
        module.addDeserializer(Timestamp.class, new DateDeserializers.TimestampDeserializer());
        module.addSerializer(Date.class, new SqlDateSerializer());
        module.addSerializer(Time.class, new SqlTimeSerializer());
        module.addSerializer(Blob.class, new SqlBlobSerializer());
        module.addSerializer(Node.class, new DOMSerializer());
        module.addSerializer(XMLGregorianCalendar.class, new CoreXMLSerializers.XMLGregorianCalendarSerializer());
        module.addDeserializer(XMLGregorianCalendar.class, createXmlGregorianCalendarDeserializer());
        return new ObjectMapper().registerModule(module);
    }

    @SuppressWarnings("unchecked")
    private static JsonDeserializer<XMLGregorianCalendar> createXmlGregorianCalendarDeserializer() {
        ObjectMapper mapper = new ObjectMapper();
        JavaType type = mapper.constructType(XMLGregorianCalendar.class);
        BeanDescription beanDescription = mapper.getDeserializationConfig().introspect(type);
        return (JsonDeserializer<XMLGregorianCalendar>) new CoreXMLDeserializers()
                .findBeanDeserializer(type, mapper.getDeserializationConfig(), beanDescription);
    }
}
