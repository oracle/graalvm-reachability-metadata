/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tools_jackson_dataformat.jackson_dataformat_xml;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Test;

import tools.jackson.dataformat.xml.XmlFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class XmlFactoryTest {
    @Test
    void javaSerializationRestoresXmlFactories() throws Exception {
        XmlFactory original = XmlFactory.builder()
                .nameForTextElement("text")
                .build();

        XmlFactory restored = serializeAndDeserialize(original);

        assertThat(restored).isNotSameAs(original);
        assertThat(restored.getFormatName()).isEqualTo(XmlFactory.FORMAT_NAME_XML);
        assertThat(restored.getXMLTextElementName()).isEqualTo("text");
        assertThat(restored.getXMLInputFactory()).isNotNull();
        assertThat(restored.getXMLOutputFactory()).isNotNull();
        assertThat(restored.getXMLInputFactory().getClass())
                .isEqualTo(original.getXMLInputFactory().getClass());
        assertThat(restored.getXMLOutputFactory().getClass())
                .isEqualTo(original.getXMLOutputFactory().getClass());
    }

    private XmlFactory serializeAndDeserialize(XmlFactory factory) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(factory);
        }

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (XmlFactory) input.readObject();
        }
    }
}
