/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jdom.jdom;

import org.jdom.Element;
import org.jdom.Namespace;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class ElementTest {
    @Test
    void serializationPreservesElementAndAdditionalNamespaces() throws Exception {
        Namespace elementNamespace = Namespace.getNamespace("doc", "urn:document");
        Namespace metadataNamespace = Namespace.getNamespace("meta", "urn:metadata");
        Namespace auditNamespace = Namespace.getNamespace("audit", "urn:audit");
        Element original = new Element("root", elementNamespace);
        original.addNamespaceDeclaration(metadataNamespace);
        original.addNamespaceDeclaration(auditNamespace);
        original.setAttribute("id", "root-1");
        original.addContent(new Element("child", elementNamespace).setText("payload"));

        Element restored = serializeAndDeserialize(original);

        assertThat(restored.getQualifiedName()).isEqualTo("doc:root");
        assertThat(restored.getNamespace()).isSameAs(elementNamespace);
        assertThat(restored.getNamespace("meta")).isSameAs(metadataNamespace);
        assertThat(restored.getNamespace("audit")).isSameAs(auditNamespace);
        assertThat(restored.getAdditionalNamespaces()).containsExactly(metadataNamespace, auditNamespace);
        assertThat(restored.getAttributeValue("id")).isEqualTo("root-1");
        assertThat(restored.getChild("child", elementNamespace).getText()).isEqualTo("payload");
    }

    private Element serializeAndDeserialize(Element element) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(element);
        }

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (Element) input.readObject();
        }
    }
}
