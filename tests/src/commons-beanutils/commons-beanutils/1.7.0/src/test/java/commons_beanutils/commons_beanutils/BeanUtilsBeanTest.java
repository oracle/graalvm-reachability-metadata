/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_beanutils.commons_beanutils;

import org.apache.commons.beanutils.BeanUtilsBean;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

public class BeanUtilsBeanTest {
    @Test
    void populatesConvertsAndShallowClonesAJavaBean() throws Exception {
        BeanUtilsBean utils = new BeanUtilsBean();
        SampleBean source = new SampleBean();
        SampleBean child = new SampleBean();
        source.setChild(child);
        utils.populate(source, Map.of("name", "Ada", "count", "12", "amount", "24.5"));

        SampleBean copy = (SampleBean) utils.cloneBean(source);

        assertNotSame(source, copy);
        assertEquals("Ada", copy.getName());
        assertEquals(12, copy.getCount());
        assertEquals(24.5, copy.getAmount());
        assertSame(child, copy.getChild());
        assertSame(source.getTags(), copy.getTags());
        assertEquals("12", utils.getProperty(copy, "count"));
        copy.setName("Grace");
        assertEquals("Ada", source.getName());
    }
}
