/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dom4j.dom4j;

import static org.assertj.core.api.Assertions.assertThat;

import org.dom4j.QName;
import org.dom4j.bean.BeanDocumentFactory;
import org.dom4j.bean.BeanElement;
import org.dom4j.tree.NamespaceStack;
import org.junit.jupiter.api.Test;
import org.xml.sax.helpers.AttributesImpl;

public class BeanElementTest {
    @Test
    void saxClassAttributeInitializesBackingBean() {
        BeanElement element = new BeanElement(QName.get("configuredBean"));
        AttributesImpl attributes = new AttributesImpl();
        attributes.addAttribute("", "class", "class", "CDATA", BeanDocumentFactory.class.getName());

        element.setAttributes(attributes, new NamespaceStack(), false);

        assertThat(element.getData()).isInstanceOf(BeanDocumentFactory.class);
    }
}
