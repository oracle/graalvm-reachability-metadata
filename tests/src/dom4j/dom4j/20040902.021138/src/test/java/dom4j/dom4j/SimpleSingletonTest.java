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
import org.dom4j.util.NonLazyDocumentFactory;
import org.dom4j.util.NonLazyElement;
import org.junit.jupiter.api.Test;

public class SimpleSingletonTest {
    @Test
    void returnsSharedNonLazyDocumentFactoryInstance() {
        DocumentFactory first = NonLazyDocumentFactory.getInstance();
        DocumentFactory second = NonLazyDocumentFactory.getInstance();

        assertThat(first).isInstanceOf(NonLazyDocumentFactory.class);
        assertThat(second).isSameAs(first);
    }

    @Test
    void createsNonLazyElementsWithAttributesAndChildren() {
        DocumentFactory factory = NonLazyDocumentFactory.getInstance();

        Element root = factory.createElement("root");
        root.addAttribute("id", "42");
        root.addElement("child").addText("value");

        assertThat(root).isInstanceOf(NonLazyElement.class);
        assertThat(root.attributeValue("id")).isEqualTo("42");
        assertThat(root.elementText("child")).isEqualTo("value");
    }
}
