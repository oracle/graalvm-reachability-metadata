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

import org.jdom.Element;
import org.jdom.Namespace;
import org.jdom.filter.ElementFilter;
import org.junit.jupiter.api.Test;

public class ElementFilterTest {
    @Test
    void serializesElementFilterWithNamespace() throws Exception {
        Namespace namespace = Namespace.getNamespace("book", "urn:jdom:book");
        ElementFilter filter = new ElementFilter("chapter", namespace);

        ElementFilter restored = deserialize(serialize(filter));

        assertThat(restored).isEqualTo(filter);
        assertThat(restored.matches(new Element("chapter", namespace))).isTrue();
        assertThat(restored.matches(new Element("chapter"))).isFalse();
        assertThat(restored.matches(new Element("appendix", namespace))).isFalse();
    }

    private static byte[] serialize(ElementFilter filter) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream)) {
            objectOutputStream.writeObject(filter);
        }
        return byteArrayOutputStream.toByteArray();
    }

    private static ElementFilter deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (ElementFilter) objectInputStream.readObject();
        }
    }
}
