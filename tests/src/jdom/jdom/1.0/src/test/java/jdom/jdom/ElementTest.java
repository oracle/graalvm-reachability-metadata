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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

import org.jdom.Element;
import org.jdom.Namespace;
import org.junit.jupiter.api.Test;

public class ElementTest {
    @Test
    void serializationPreservesElementAndAdditionalNamespaces() throws Exception {
        Namespace elementNamespace = Namespace.getNamespace("sample", "urn:jdom-element-test:sample");
        Namespace additionalNamespace = Namespace.getNamespace("extra", "urn:jdom-element-test:extra");
        Element element = new Element("root", elementNamespace);
        element.addNamespaceDeclaration(additionalNamespace);

        Element deserialized = serializeAndDeserialize(element);

        assertThat(deserialized).isNotSameAs(element);
        assertThat(deserialized.getName()).isEqualTo("root");
        assertThat(deserialized.getNamespace()).isSameAs(elementNamespace);
        assertThat(deserialized.getNamespace("extra")).isSameAs(additionalNamespace);
        @SuppressWarnings("unchecked")
        List<Namespace> additionalNamespaces = deserialized.getAdditionalNamespaces();
        assertThat(additionalNamespaces).containsExactly(additionalNamespace);
    }

    private static Element serializeAndDeserialize(Element element) throws Exception {
        byte[] serialized;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(element);
            output.flush();
            serialized = bytes.toByteArray();
        }

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return (Element) input.readObject();
        }
    }
}
