/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_azure.azure_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.core.util.serializer.JacksonAdapter;
import com.azure.core.util.serializer.SerializerEncoding;
import com.azure.json.JsonReader;
import com.azure.json.JsonSerializable;
import com.azure.json.JsonToken;
import com.azure.json.JsonWriter;
import com.azure.xml.XmlReader;
import com.azure.xml.XmlSerializable;
import com.azure.xml.XmlToken;
import com.azure.xml.XmlWriter;
import java.io.IOException;
import javax.xml.stream.XMLStreamException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
public class ReflectionSerializableTest {
    @Test
    void adapterRoundTripsJsonSerializableModel() throws Exception {
        JacksonAdapter adapter = new JacksonAdapter();

        String json = adapter.serialize(new JsonWidget("azure"), SerializerEncoding.JSON);
        JsonWidget decoded = adapter.deserialize(json, JsonWidget.class, SerializerEncoding.JSON);

        assertThat(json).isEqualTo("{\"name\":\"azure\"}");
        assertThat(decoded.name).isEqualTo("azure");
    }

    @Test
    void adapterRoundTripsXmlSerializableModel() throws Exception {
        JacksonAdapter adapter = new JacksonAdapter();

        String xml = adapter.serialize(new XmlWidget("azure"), SerializerEncoding.XML);
        XmlWidget decoded = adapter.deserialize(xml, XmlWidget.class, SerializerEncoding.XML);

        assertThat(xml).contains("<widget>", "<name>azure</name>");
        assertThat(decoded.name).isEqualTo("azure");
    }

    public static final class JsonWidget implements JsonSerializable<JsonWidget> {
        private final String name;

        public JsonWidget(String name) {
            this.name = name;
        }

        public static JsonWidget fromJson(JsonReader reader) throws IOException {
            return reader.readObject(objectReader -> {
                String name = null;
                while (objectReader.nextToken() != JsonToken.END_OBJECT) {
                    String fieldName = objectReader.getFieldName();
                    objectReader.nextToken();
                    if ("name".equals(fieldName)) {
                        name = objectReader.getString();
                    } else {
                        objectReader.skipChildren();
                    }
                }
                return new JsonWidget(name);
            });
        }

        @Override
        public JsonWriter toJson(JsonWriter writer) throws IOException {
            return writer.writeStartObject().writeStringField("name", name).writeEndObject();
        }
    }

    public static final class XmlWidget implements XmlSerializable<XmlWidget> {
        private final String name;

        public XmlWidget(String name) {
            this.name = name;
        }

        public static XmlWidget fromXml(XmlReader reader) throws XMLStreamException {
            String[] name = new String[1];
            if (reader.currentToken() == XmlToken.START_DOCUMENT) {
                reader.nextElement();
            }
            while (reader.nextElement() != XmlToken.END_ELEMENT) {
                reader.processNextElement((namespace, localName, element) -> {
                    if ("name".equals(localName)) {
                        name[0] = element.getStringElement();
                    } else {
                        element.skipElement();
                    }
                });
            }
            return new XmlWidget(name[0]);
        }

        public static XmlWidget fromXml(XmlReader reader, String rootElementName) throws XMLStreamException {
            return fromXml(reader);
        }

        @Override
        public XmlWriter toXml(XmlWriter writer, String rootElementName) throws XMLStreamException {
            String elementName = rootElementName == null ? "widget" : rootElementName;
            return writer.writeStartElement(elementName)
                    .writeStringElement("name", name)
                    .writeEndElement();
        }

        @Override
        public XmlWriter toXml(XmlWriter writer) throws XMLStreamException {
            return toXml(writer, "widget");
        }
    }
}
