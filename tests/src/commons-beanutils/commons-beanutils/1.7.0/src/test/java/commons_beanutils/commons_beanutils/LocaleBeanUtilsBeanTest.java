/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_beanutils.commons_beanutils;

import org.apache.commons.beanutils.locale.LocaleBeanUtilsBean;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LocaleBeanUtilsBeanTest {
    @Test
    void parsesAndFormatsBeanValuesWithAnExplicitLocale() throws Exception {
        LocaleBeanUtilsBean utils = new LocaleBeanUtilsBean();
        utils.setDefaultLocale(Locale.US);
        SampleBean bean = new SampleBean();
        bean.setChild(new SampleBean());

        utils.setProperty(bean, "amount", "1,234.50", "#,##0.00");
        utils.setProperty(bean.getChild(), "count", "3,000", "#,##0");
        utils.setProperty(bean, "tags[0]", "primary");
        utils.setProperty(bean, "label(mode)", "fast");

        assertEquals(1234.5, bean.getAmount());
        assertEquals("1,234.50", utils.getSimpleProperty(bean, "amount", "#,##0.00"));
        assertEquals(3000, bean.getChild().getCount());
        assertEquals("3,000", utils.getNestedProperty(bean, "child.count", "#,##0"));
        assertEquals("primary", utils.getIndexedProperty(bean, "tags", 0));
        assertEquals("fast", utils.getMappedProperty(bean, "label", "mode"));
    }
}
