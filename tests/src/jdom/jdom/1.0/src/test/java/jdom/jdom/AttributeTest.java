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

import org.jdom.Attribute;
import org.jdom.Namespace;
import org.junit.jupiter.api.Test;

public class AttributeTest {
    @Test
    void serializationPreservesAttributeFieldsAndNamespaceIdentity() throws Exception {
        Namespace namespace = Namespace.getNamespace("sample", "urn:jdom-attribute-test:sample");
        Attribute attribute = new Attribute("enabled", "true", Attribute.ID_TYPE, namespace);

        Attribute deserialized = serializeAndDeserialize(attribute);

        assertThat(deserialized).isNotSameAs(attribute);
        assertThat(deserialized.getName()).isEqualTo("enabled");
        assertThat(deserialized.getValue()).isEqualTo("true");
        assertThat(deserialized.getAttributeType()).isEqualTo(Attribute.ID_TYPE);
        assertThat(deserialized.getNamespace()).isSameAs(namespace);
        assertThat(deserialized.getNamespacePrefix()).isEqualTo("sample");
        assertThat(deserialized.getNamespaceURI()).isEqualTo("urn:jdom-attribute-test:sample");
        assertThat(deserialized.getQualifiedName()).isEqualTo("sample:enabled");
        assertThat(deserialized.getBooleanValue()).isTrue();
    }

    private static Attribute serializeAndDeserialize(Attribute attribute) throws Exception {
        byte[] serialized;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(attribute);
            output.flush();
            serialized = bytes.toByteArray();
        }

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return (Attribute) input.readObject();
        }
    }
}
