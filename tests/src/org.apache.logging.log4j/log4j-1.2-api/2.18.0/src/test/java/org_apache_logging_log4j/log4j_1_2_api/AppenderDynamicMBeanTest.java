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

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.jmx.AppenderDynamicMBean;
import org.junit.jupiter.api.Test;

public class AppenderDynamicMBeanTest {

    @Test
    void exposesAndUpdatesSupportedAppenderProperties() throws Exception {
        ConsoleAppender appender = new ConsoleAppender();
        appender.setName("managed-console");

        AppenderDynamicMBean mbean = new AppenderDynamicMBean(appender);
        MBeanInfo mbeanInfo = mbean.getMBeanInfo();

        assertThat(Arrays.stream(mbeanInfo.getAttributes())
            .map(MBeanAttributeInfo::getName))
            .contains("follow", "name", "target", "threshold");

        mbean.setAttribute(new Attribute("target", ConsoleAppender.SYSTEM_ERR));
        mbean.setAttribute(new Attribute("follow", true));
        mbean.setAttribute(new Attribute("threshold", "WARN"));

        assertThat(appender.getTarget()).isEqualTo(ConsoleAppender.SYSTEM_ERR);
        assertThat(appender.getFollow()).isTrue();
        assertThat(appender.getThreshold()).isEqualTo(Level.WARN);
        assertThat(mbean.getAttribute("target")).isEqualTo(ConsoleAppender.SYSTEM_ERR);
    }
}
