/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jdom.jdom;

import org.jdom.Element;
import org.jdom.Namespace;
import org.jdom.filter.ElementFilter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class ElementFilterTest {
    @Test
    void serializationPreservesNameAndNamespaceMatching() throws Exception {
        Namespace namespace = Namespace.getNamespace("catalog", "urn:catalog");
        ElementFilter original = new ElementFilter("book", namespace);

        ElementFilter restored = serializeAndDeserialize(original);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.hashCode()).isEqualTo(original.hashCode());
        Namespace otherNamespace = Namespace.getNamespace("other", "urn:other-catalog");
        assertThat(restored.matches(new Element("book", namespace))).isTrue();
        assertThat(restored.matches(new Element("chapter", namespace))).isFalse();
        assertThat(restored.matches(new Element("book", otherNamespace))).isFalse();
    }

    private ElementFilter serializeAndDeserialize(ElementFilter filter) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(filter);
        }

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (ElementFilter) input.readObject();
        }
    }
}
