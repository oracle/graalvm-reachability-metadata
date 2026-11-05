/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_beanutils.commons_beanutils;

import org.apache.commons.beanutils.WrapDynaBean;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class WrapDynaBeanTest {
    @Test
    void updatesAnExistingBeanThroughSimpleIndexedAndMappedProperties() {
        SampleBean bean = new SampleBean();
        WrapDynaBean wrapper = new WrapDynaBean(bean);

        wrapper.set("name", "Ada");
        wrapper.set("count", 12);
        wrapper.set("tags", 1, "secondary");
        wrapper.set("label", "language", "Java");

        assertSame(bean, wrapper.getInstance());
        assertEquals("Ada", wrapper.get("name"));
        assertEquals(12, wrapper.get("count"));
        assertEquals("secondary", wrapper.get("tags", 1));
        assertEquals("Java", wrapper.get("label", "language"));
        assertEquals("Ada", bean.getName());
        assertEquals(12, bean.getCount());
        assertEquals("secondary", bean.getTags()[1]);
        assertEquals("Java", bean.getLabel("language"));

        bean.setName("Grace");
        assertEquals("Grace", wrapper.get("name"));
    }
}
