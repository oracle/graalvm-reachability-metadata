/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_beanutils.commons_beanutils;

import org.apache.commons.beanutils.PropertyUtilsBean;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PropertyUtilsBeanTest {
    @Test
    void accessesSimpleNestedIndexedAndMappedProperties() throws Exception {
        PropertyUtilsBean utils = new PropertyUtilsBean();
        SampleBean bean = new SampleBean();
        SampleBean child = new SampleBean();
        bean.setChild(child);

        utils.setProperty(bean, "name", "parent");
        utils.setProperty(bean, "child.name", "nested");
        utils.setProperty(bean, "tags[1]", "updated");
        utils.setProperty(bean, "label(language)", "Java");

        assertEquals("parent", utils.getProperty(bean, "name"));
        assertEquals("nested", utils.getProperty(bean, "child.name"));
        assertEquals("updated", utils.getProperty(bean, "tags[1]"));
        assertEquals("Java", utils.getProperty(bean, "label(language)"));
        assertEquals("Java", bean.getLabel("language"));
        assertTrue(utils.isReadable(bean, "name"));
        assertTrue(utils.isWriteable(bean, "name"));
    }
}
