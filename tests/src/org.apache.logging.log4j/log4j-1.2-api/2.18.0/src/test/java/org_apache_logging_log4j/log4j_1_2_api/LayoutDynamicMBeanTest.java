/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_1_2_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import javax.management.Attribute;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;

import org.apache.log4j.PatternLayout;
import org.apache.log4j.jmx.LayoutDynamicMBean;
import org.junit.jupiter.api.Test;

public class LayoutDynamicMBeanTest {

    @Test
    void exposesAndUpdatesSupportedLayoutProperties() throws Exception {
        PatternLayout layout = new PatternLayout("%m%n");

        LayoutDynamicMBean mbean = new LayoutDynamicMBean(layout);
        MBeanInfo mbeanInfo = mbean.getMBeanInfo();

        assertThat(Arrays.stream(mbeanInfo.getAttributes())
                .map(MBeanAttributeInfo::getName))
                .contains("conversionPattern", "contentType", "header", "footer");

        mbean.setAttribute(new Attribute("conversionPattern", "%p - %m%n"));

        assertThat(layout.getConversionPattern()).isEqualTo("%p - %m%n");
        assertThat(mbean.getAttribute("conversionPattern")).isEqualTo("%p - %m%n");
    }
}
