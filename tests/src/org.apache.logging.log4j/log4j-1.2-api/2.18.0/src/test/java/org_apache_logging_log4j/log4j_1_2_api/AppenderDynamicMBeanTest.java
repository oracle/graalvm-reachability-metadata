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

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.jmx.AppenderDynamicMBean;
import org.junit.jupiter.api.Test;

public class AppenderDynamicMBeanTest {

    @Test
    void exposesAndUpdatesAppenderBeanPropertiesThroughDynamicMBean() throws Exception {
        ConsoleAppender appender = new ConsoleAppender();
        appender.setName("console-appender");
        AppenderDynamicMBean mBean = new AppenderDynamicMBean(appender);

        MBeanInfo mBeanInfo = mBean.getMBeanInfo();
        assertThat(mBeanInfo.getConstructors()).hasSize(1);
        assertThat(attributeNames(mBeanInfo)).contains("target", "follow");
        assertThat(mBean.getAttribute("target")).isEqualTo(ConsoleAppender.SYSTEM_OUT);
        assertThat(mBean.getAttribute("follow")).isEqualTo(false);

        mBean.setAttribute(new Attribute("target", ConsoleAppender.SYSTEM_ERR));
        mBean.setAttribute(new Attribute("follow", true));

        assertThat(mBean.getAttribute("target")).isEqualTo(ConsoleAppender.SYSTEM_ERR);
        assertThat(mBean.getAttribute("follow")).isEqualTo(true);
        assertThat(appender.getTarget()).isEqualTo(ConsoleAppender.SYSTEM_ERR);
        assertThat(appender.getFollow()).isTrue();
    }

    private static String[] attributeNames(MBeanInfo mBeanInfo) {
        MBeanAttributeInfo[] attributes = mBeanInfo.getAttributes();
        String[] names = new String[attributes.length];
        for (int i = 0; i < attributes.length; i++) {
            names[i] = attributes[i].getName();
        }
        return names;
    }
}
