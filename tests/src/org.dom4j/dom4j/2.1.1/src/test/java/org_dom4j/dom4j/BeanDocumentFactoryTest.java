/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_dom4j.dom4j;

import static org.assertj.core.api.Assertions.assertThat;

import org.dom4j.DocumentFactory;
import org.dom4j.Element;
import org.dom4j.QName;
import org.dom4j.bean.BeanDocumentFactory;
import org.dom4j.bean.BeanElement;
import org.junit.jupiter.api.Test;
import org.xml.sax.helpers.AttributesImpl;

public class BeanDocumentFactoryTest {
    @Test
    void createsBeanElementWithConfiguredBeanClass() {
        BeanDocumentFactory factory = (BeanDocumentFactory) BeanDocumentFactory
                .getInstance();
        AttributesImpl attributes = new AttributesImpl();
        attributes.addAttribute("", "class", "class", "CDATA",
                DocumentFactory.class.getName());
        QName qName = factory.createQName("configuration");

        Element element = factory.createElement(qName, attributes);

        assertThat(element).isInstanceOf(BeanElement.class);
        assertThat(((BeanElement) element).getData()).isInstanceOf(DocumentFactory.class);
    }
}
