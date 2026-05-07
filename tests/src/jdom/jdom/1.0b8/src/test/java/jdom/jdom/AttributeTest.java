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

import org.jdom.Attribute;
import org.jdom.Namespace;
import org.junit.jupiter.api.Test;

public class AttributeTest {
    @Test
    void serializesAttributeWithNamespace() throws Exception {
        Namespace namespace = Namespace.getNamespace("book", "urn:jdom:book");
        Attribute attribute = new Attribute("edition", "first", Attribute.ID_ATTRIBUTE, namespace);

        Attribute restored = deserialize(serialize(attribute));

        assertThat(restored).isNotSameAs(attribute);
        assertThat(restored.getName()).isEqualTo("edition");
        assertThat(restored.getValue()).isEqualTo("first");
        assertThat(restored.getAttributeType()).isEqualTo(Attribute.ID_ATTRIBUTE);
        assertThat(restored.getQualifiedName()).isEqualTo("book:edition");
        assertThat(restored.getNamespace()).isSameAs(namespace);
        assertThat(restored.getNamespacePrefix()).isEqualTo("book");
        assertThat(restored.getNamespaceURI()).isEqualTo("urn:jdom:book");
        assertThat(restored.getParent()).isNull();
    }

    private static byte[] serialize(Attribute attribute) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream)) {
            objectOutputStream.writeObject(attribute);
        }
        return byteArrayOutputStream.toByteArray();
    }

    private static Attribute deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (Attribute) objectInputStream.readObject();
        }
    }
}
