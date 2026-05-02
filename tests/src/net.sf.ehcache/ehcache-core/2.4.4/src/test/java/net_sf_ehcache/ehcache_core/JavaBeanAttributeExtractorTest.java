/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache_core;

import static org.assertj.core.api.Assertions.assertThat;

import net.sf.ehcache.Element;
import net.sf.ehcache.search.attribute.JavaBeanAttributeExtractor;

import org.junit.jupiter.api.Test;

public class JavaBeanAttributeExtractorTest {
    @Test
    void extractsGetterBackedAttributeFromElementKey() {
        Element key = new Element("bean-key", "bean-value");
        Element element = new Element(key, null);
        JavaBeanAttributeExtractor extractor = new JavaBeanAttributeExtractor("objectKey");

        Object attribute = extractor.attributeFor(element, "objectKey");

        assertThat(attribute).isEqualTo("bean-key");
    }

    @Test
    void extractsBooleanAttributeFromElementValue() {
        Element value = new Element("eternal-key", "eternal-value", Boolean.TRUE, null, null);
        Element element = new Element(null, value);
        JavaBeanAttributeExtractor extractor = new JavaBeanAttributeExtractor("eternal");

        Object attribute = extractor.attributeFor(element, "eternal");

        assertThat(attribute).isEqualTo(Boolean.TRUE);
    }
}
