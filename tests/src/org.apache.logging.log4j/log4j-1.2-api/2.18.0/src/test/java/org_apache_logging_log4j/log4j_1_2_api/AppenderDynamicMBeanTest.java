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

import org.apache.log4j.jmx.AppenderDynamicMBean;
import org.apache.log4j.varia.NullAppender;
import org.junit.jupiter.api.Test;

public class AppenderDynamicMBeanTest {

    @Test
    void exposesAndUpdatesAppenderBeanProperties() throws Exception {
        NullAppender appender = new NullAppender();
        appender.setName("initial-appender");

        AppenderDynamicMBean mbean = new AppenderDynamicMBean(appender);

        MBeanInfo mbeanInfo = mbean.getMBeanInfo();
        assertThat(Arrays.stream(mbeanInfo.getAttributes())
            .map(MBeanAttributeInfo::getName))
            .contains("name");

        assertThat(mbean.getAttribute("name")).isEqualTo("initial-appender");

        mbean.setAttribute(new Attribute("name", "updated-appender"));

        assertThat(appender.getName()).isEqualTo("updated-appender");
        assertThat(mbean.getAttribute("name")).isEqualTo("updated-appender");
    }
}
