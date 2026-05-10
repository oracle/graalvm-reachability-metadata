/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_1_2_api;

import static org.assertj.core.api.Assertions.assertThat;

import javax.management.Attribute;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;

import org.apache.log4j.PatternLayout;
import org.apache.log4j.jmx.LayoutDynamicMBean;
import org.junit.jupiter.api.Test;

public class LayoutDynamicMBeanTest {

    @Test
    void exposesAndUpdatesLayoutBeanPropertiesThroughDynamicMBean() throws Exception {
        PatternLayout layout = new PatternLayout("%m%n");
        LayoutDynamicMBean mBean = new LayoutDynamicMBean(layout);

        MBeanInfo mBeanInfo = mBean.getMBeanInfo();
        assertThat(mBeanInfo.getConstructors()).hasSize(1);
        assertThat(attributeInfo(mBeanInfo, "conversionPattern"))
            .returns("java.lang.String", MBeanAttributeInfo::getType)
            .returns(true, MBeanAttributeInfo::isReadable)
            .returns(true, MBeanAttributeInfo::isWritable);
        assertThat(mBean.getAttribute("conversionPattern")).isEqualTo("%m%n");

        mBean.setAttribute(new Attribute("conversionPattern", "%p - %m%n"));

        assertThat(mBean.getAttribute("conversionPattern")).isEqualTo("%p - %m%n");
        assertThat(layout.getConversionPattern()).isEqualTo("%p - %m%n");
    }

    private static MBeanAttributeInfo attributeInfo(MBeanInfo mBeanInfo, String name) {
        for (MBeanAttributeInfo attribute : mBeanInfo.getAttributes()) {
            if (attribute.getName().equals(name)) {
                return attribute;
            }
        }
        throw new AssertionError("Missing MBean attribute: " + name);
    }
}
