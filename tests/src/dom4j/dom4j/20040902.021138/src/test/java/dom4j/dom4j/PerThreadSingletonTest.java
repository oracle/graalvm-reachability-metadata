/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dom4j.dom4j;

import static org.assertj.core.api.Assertions.assertThat;

import org.dom4j.DocumentFactory;
import org.dom4j.Element;
import org.dom4j.QName;
import org.dom4j.util.IndexedDocumentFactory;
import org.dom4j.util.IndexedElement;
import org.junit.jupiter.api.Test;

public class PerThreadSingletonTest {
    @Test
    void returnsSharedIndexedDocumentFactoryInstance() {
        DocumentFactory first = IndexedDocumentFactory.getInstance();
        DocumentFactory second = IndexedDocumentFactory.getInstance();

        assertThat(first).isInstanceOf(IndexedDocumentFactory.class);
        assertThat(second).isSameAs(first);
    }

    @Test
    void createsIndexedElementsThatResolveChildrenByName() {
        IndexedDocumentFactory factory = new IndexedDocumentFactory();
        QName rootName = factory.createQName("root");

        Element root = factory.createElement(rootName, 3);
        root.addElement("item").addText("first");
        root.addElement("item").addText("second");
        root.addElement("other").addText("ignored");

        assertThat(root).isInstanceOf(IndexedElement.class);
        assertThat(root.element("item").getText()).isEqualTo("first");
        assertThat(root.elements("item").size()).isEqualTo(2);
    }
}
