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

import org.jdom.Element;
import org.jdom.Namespace;
import org.jdom.filter.ElementFilter;
import org.junit.jupiter.api.Test;

public class ElementFilterTest {
    @Test
    void serializationPreservesNameAndNamespaceMatching() throws Exception {
        Namespace namespace = Namespace.getNamespace("sample", "urn:jdom-element-filter-test:sample");
        ElementFilter filter = new ElementFilter("entry", namespace);

        ElementFilter deserialized = serializeAndDeserialize(filter);

        assertThat(deserialized).isNotSameAs(filter);
        assertThat(deserialized).isEqualTo(filter);
        assertThat(deserialized.hashCode()).isEqualTo(filter.hashCode());
        assertThat(deserialized.matches(new Element("entry", namespace))).isTrue();
        assertThat(deserialized.matches(new Element("entry", Namespace.getNamespace("other", "urn:jdom-element-filter-test:other"))))
                .isFalse();
        assertThat(deserialized.matches(new Element("other", namespace))).isFalse();
    }

    private static ElementFilter serializeAndDeserialize(ElementFilter filter) throws Exception {
        byte[] serialized;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(filter);
            output.flush();
            serialized = bytes.toByteArray();
        }

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return (ElementFilter) input.readObject();
        }
    }
}
