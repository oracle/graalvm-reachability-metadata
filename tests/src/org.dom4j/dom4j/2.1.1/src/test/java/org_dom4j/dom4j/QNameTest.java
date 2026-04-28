/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_dom4j.dom4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.dom4j.Namespace;
import org.dom4j.QName;
import org.junit.jupiter.api.Test;

public class QNameTest {
    @Test
    void cachedQualifiedNameRetainsNamespaceAfterSerialization() throws Exception {
        QName original = QName.get("chapter", "bk", "urn:books");

        QName copy = serializeAndDeserialize(original);

        assertThat(copy).isNotSameAs(original);
        assertThat(copy).isEqualTo(original);
        assertThat(copy.getName()).isEqualTo("chapter");
        assertThat(copy.getQualifiedName()).isEqualTo("bk:chapter");
        assertThat(copy.getNamespacePrefix()).isEqualTo("bk");
        assertThat(copy.getNamespaceURI()).isEqualTo("urn:books");
        assertThat(copy.getNamespace()).isEqualTo(Namespace.get("bk", "urn:books"));
    }

    @Test
    void noNamespaceQualifiedNameRetainsEmptyNamespaceAfterSerialization() throws Exception {
        QName original = QName.get("appendix");

        QName copy = serializeAndDeserialize(original);

        assertThat(copy).isEqualTo(original);
        assertThat(copy.getName()).isEqualTo("appendix");
        assertThat(copy.getQualifiedName()).isEqualTo("appendix");
        assertThat(copy.getNamespacePrefix()).isEmpty();
        assertThat(copy.getNamespaceURI()).isEmpty();
        assertThat(copy.getNamespace()).isEqualTo(Namespace.NO_NAMESPACE);
    }

    private static QName serializeAndDeserialize(QName qName) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(qName);
        }

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (QName) input.readObject();
        }
    }
}
