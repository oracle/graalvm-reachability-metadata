/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dom4j.dom4j;

import static org.assertj.core.api.Assertions.assertThat;

import org.dom4j.Attribute;
import org.dom4j.DocumentFactory;
import org.dom4j.Element;
import org.dom4j.QName;
import org.dom4j.util.NonLazyDocumentFactory;
import org.dom4j.util.NonLazyElement;
import org.dom4j.util.UserDataAttribute;
import org.dom4j.util.UserDataDocumentFactory;
import org.dom4j.util.UserDataElement;
import org.junit.jupiter.api.Test;

public class SimpleSingletonTest {
    @Test
    void nonLazyDocumentFactorySingletonCreatesNonLazyElements() {
        DocumentFactory firstFactory = NonLazyDocumentFactory.getInstance();
        DocumentFactory secondFactory = NonLazyDocumentFactory.getInstance();

        Element root = firstFactory.createElement("root");
        root.addElement("child").addText("value");

        assertThat(secondFactory).isSameAs(firstFactory);
        assertThat(root).isInstanceOf(NonLazyElement.class);
        assertThat(root.element("child").getText()).isEqualTo("value");
    }

    @Test
    void userDataDocumentFactoryCreatesUserDataNodes() {
        DocumentFactory factory = UserDataDocumentFactory.getInstance();
        Element element = factory.createElement("entry");
        Attribute attribute = factory.createAttribute(element, QName.get("id"), "42");

        ((UserDataElement) element).setData("element-data");
        ((UserDataAttribute) attribute).setData("attribute-data");

        assertThat(element).isInstanceOf(UserDataElement.class);
        assertThat(attribute).isInstanceOf(UserDataAttribute.class);
        assertThat(((UserDataElement) element).getData()).isEqualTo("element-data");
        assertThat(((UserDataAttribute) attribute).getData()).isEqualTo("attribute-data");
    }
}
