/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jdom.jdom;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

import org.jdom.Attribute;
import org.jdom.Element;
import org.jdom.Namespace;
import org.junit.jupiter.api.Test;

public class ElementTest {
    @Test
    void serializesElementNamespaceAndAdditionalNamespaceDeclarations() throws Exception {
        Namespace elementNamespace = Namespace.getNamespace("book", "urn:jdom:book");
        Namespace metadataNamespace = Namespace.getNamespace("meta", "urn:jdom:metadata");
        Element element = new Element("catalog", elementNamespace);
        element.addNamespaceDeclaration(metadataNamespace);
        element.setAttribute(new Attribute("id", "catalog-1"));
        element.addContent(new Element("title").setText("Native Image"));

        Element restored = deserialize(serialize(element));

        assertThat(restored.getName()).isEqualTo("catalog");
        assertThat(restored.getNamespacePrefix()).isEqualTo("book");
        assertThat(restored.getNamespaceURI()).isEqualTo("urn:jdom:book");
        assertThat(restored.getAttributeValue("id")).isEqualTo("catalog-1");
        assertThat(restored.getChildText("title")).isEqualTo("Native Image");

        List additionalNamespaces = restored.getAdditionalNamespaces();
        assertThat(additionalNamespaces).hasSize(1);
        Namespace restoredMetadataNamespace = (Namespace) additionalNamespaces.get(0);
        assertThat(restoredMetadataNamespace.getPrefix()).isEqualTo("meta");
        assertThat(restoredMetadataNamespace.getURI()).isEqualTo("urn:jdom:metadata");
    }

    private static byte[] serialize(Element element) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream)) {
            objectOutputStream.writeObject(element);
        }
        return byteArrayOutputStream.toByteArray();
    }

    private static Element deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (Element) objectInputStream.readObject();
        }
    }
}
