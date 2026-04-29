/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dom4j.dom4j;

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
    void cachedNamePreservesNamespaceDetails() {
        QName qName = QName.get("element", "sample", "urn:sample");

        assertThat(qName.getName()).isEqualTo("element");
        assertThat(qName.getNamespacePrefix()).isEqualTo("sample");
        assertThat(qName.getNamespaceURI()).isEqualTo("urn:sample");
        assertThat(qName.getQualifiedName()).isEqualTo("sample:element");
    }

    @Test
    void serializedNameRestoresTransientNamespace() throws Exception {
        QName original = new QName("entry", Namespace.get("feed", "urn:feed"));
        original.setDocumentFactory(null);

        QName restored = roundTrip(original);

        assertThat(restored).isNotSameAs(original);
        assertThat(restored.getName()).isEqualTo("entry");
        assertThat(restored.getNamespacePrefix()).isEqualTo("feed");
        assertThat(restored.getNamespaceURI()).isEqualTo("urn:feed");
        assertThat(restored.getQualifiedName()).isEqualTo("feed:entry");
        assertThat(restored).isEqualTo(original);
    }

    private static QName roundTrip(QName qName) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(qName);
        }

        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (QName) in.readObject();
        }
    }
}
