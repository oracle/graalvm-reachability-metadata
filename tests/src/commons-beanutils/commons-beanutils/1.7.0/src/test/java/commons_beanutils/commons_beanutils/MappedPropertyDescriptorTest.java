/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_beanutils.commons_beanutils;

import org.apache.commons.beanutils.MappedPropertyDescriptor;
import org.apache.commons.beanutils.PropertyUtilsBean;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MappedPropertyDescriptorTest {
    @Test
    void discoversMappedAccessorsAndUsesThemToReadAndWriteValues() throws Exception {
        MappedPropertyDescriptor descriptor = new MappedPropertyDescriptor("label", SampleBean.class);
        assertEquals(String.class, descriptor.getMappedPropertyType());
        assertEquals("getLabel", descriptor.getMappedReadMethod().getName());
        assertEquals("setLabel", descriptor.getMappedWriteMethod().getName());

        SampleBean bean = new SampleBean();
        PropertyUtilsBean utils = new PropertyUtilsBean();
        utils.setMappedProperty(bean, "label", "color", "blue");
        assertEquals("blue", utils.getMappedProperty(bean, "label", "color"));
        assertEquals("blue", bean.getLabel("color"));
    }
}
