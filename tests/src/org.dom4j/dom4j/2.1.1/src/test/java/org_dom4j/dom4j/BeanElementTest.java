/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_dom4j.dom4j;

import static org.assertj.core.api.Assertions.assertThat;

import org.dom4j.DocumentFactory;
import org.dom4j.bean.BeanElement;
import org.dom4j.tree.NamespaceStack;
import org.junit.jupiter.api.Test;
import org.xml.sax.helpers.AttributesImpl;

public class BeanElementTest {
    @Test
    void setAttributesCreatesBeanFromClassAttribute() {
        BeanElement element = new BeanElement(
                DocumentFactory.getInstance().createQName("configuration"));
        AttributesImpl attributes = new AttributesImpl();
        attributes.addAttribute("", "class", "class", "CDATA",
                DocumentFactory.class.getName());

        element.setAttributes(attributes, new NamespaceStack(), true);

        assertThat(element.getData()).isInstanceOf(DocumentFactory.class);
    }
}
