/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jersey_media.jersey_media_json_jackson;

import java.io.ByteArrayOutputStream;
import java.lang.annotation.Annotation;
import java.nio.charset.StandardCharsets;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class JsonMapperConfiguratorTest {
    @Test
    void defaultProviderWritesJaxbPropertyNamesAndExcludesTransientFields() throws Exception {
        JacksonJaxbJsonProvider provider = new JacksonJaxbJsonProvider();
        Message message = new Message();
        message.text = "hello";
        message.internalNote = "not for the wire";
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        provider.writeTo(message, Message.class, Message.class, new Annotation[0],
                MediaType.APPLICATION_JSON_TYPE, new MultivaluedHashMap<>(), output);

        assertEquals("""
                {"message_text":"hello"}""", output.toString(StandardCharsets.UTF_8));
    }

    @Test
    void defaultProviderMapperReadsJaxbPropertyNamesAndIgnoresTransientFields() throws Exception {
        JacksonJaxbJsonProvider provider = new JacksonJaxbJsonProvider();
        ObjectMapper mapper = provider.locateMapper(Message.class, MediaType.APPLICATION_JSON_TYPE);

        Message message = mapper.readValue("""
                {"message_text":"received","internalNote":"not for the application"}
                """, Message.class);

        assertEquals("received", message.text);
        assertNull(message.internalNote);
    }

    public static class Message {
        @XmlElement(name = "message_text")
        public String text;

        @XmlTransient
        public String internalNote;
    }
}
