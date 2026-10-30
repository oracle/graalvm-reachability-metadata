/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_beanutils.commons_beanutils;

import org.apache.commons.beanutils.locale.LocaleBeanUtils;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LocaleBeanUtilsTest {
    @Test
    void usesStaticFacadeForFormattedBeanProperties() throws Exception {
        Locale previous = LocaleBeanUtils.getDefaultLocale();
        try {
            LocaleBeanUtils.setDefaultLocale(Locale.US);
            SampleBean bean = new SampleBean();
            LocaleBeanUtils.setProperty(bean, "count", "2,048", "#,##0");
            LocaleBeanUtils.setProperty(bean, "name", "Ada");

            assertEquals(2048, bean.getCount());
            assertEquals("2,048", LocaleBeanUtils.getSimpleProperty(bean, "count", "#,##0"));
            assertEquals("Ada", LocaleBeanUtils.getProperty(bean, "name"));
        } finally {
            LocaleBeanUtils.setDefaultLocale(previous);
        }
    }
}
