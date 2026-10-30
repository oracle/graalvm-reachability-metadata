/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_beanutils.commons_beanutils;

import org.apache.commons.beanutils.ConvertingWrapDynaBean;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ConvertingWrapDynaBeanTest {
    @Test
    void convertsStringInputsToTheWrappedBeansPropertyTypes() {
        SampleBean bean = new SampleBean();
        ConvertingWrapDynaBean wrapper = new ConvertingWrapDynaBean(bean);

        wrapper.set("count", "42");
        wrapper.set("amount", "123.75");
        wrapper.set("tags", new String[]{"primary", "secondary", "extra"});

        assertEquals(42, bean.getCount());
        assertEquals(123.75, bean.getAmount());
        assertEquals(42, wrapper.get("count"));
        assertEquals(123.75, wrapper.get("amount"));
        assertArrayEquals(new String[]{"primary", "secondary", "extra"}, bean.getTags());
    }
}
