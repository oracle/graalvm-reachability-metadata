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
import org.dom4j.util.IndexedDocumentFactory;
import org.dom4j.util.IndexedElement;
import org.junit.jupiter.api.Test;

public class PerThreadSingletonTest {
    @Test
    void indexedDocumentFactorySingletonCreatesIndexedElements() {
        DocumentFactory firstFactory = IndexedDocumentFactory.getInstance();
        DocumentFactory secondFactory = IndexedDocumentFactory.getInstance();

        Element root = firstFactory.createElement("root");
        root.addAttribute("id", "sample");
        root.addElement("child").addAttribute("name", "first");

        assertThat(secondFactory).isSameAs(firstFactory);
        assertThat(root).isInstanceOf(IndexedElement.class);
        assertThat(root.attribute(0).getValue()).isEqualTo("sample");
        assertThat(root.element("child").attribute(0).getValue()).isEqualTo("first");
    }
}
