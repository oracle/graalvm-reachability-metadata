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
import javax.management.MBeanConstructorInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.jmx.LoggerDynamicMBean;
import org.junit.jupiter.api.Test;

public class LoggerDynamicMBeanTest {

    @Test
    void exposesConstructorsOperationsAndLoggerAttributes() throws Exception {
        Logger logger = Logger.getLogger("org_apache_logging_log4j.log4j_1_2_api.managedLogger");
        Level previousLevel = logger.getLevel();

        try {
            LoggerDynamicMBean mbean = new LoggerDynamicMBean(logger);
            MBeanInfo mbeanInfo = mbean.getMBeanInfo();

            assertThat(Arrays.stream(mbeanInfo.getConstructors())
                    .map(MBeanConstructorInfo::getName))
                    .contains(LoggerDynamicMBean.class.getName());
            assertThat(Arrays.stream(mbeanInfo.getAttributes())
                    .map(MBeanAttributeInfo::getName))
                    .contains("name", "priority");
            assertThat(Arrays.stream(mbeanInfo.getOperations())
                    .map(MBeanOperationInfo::getName))
                    .contains("addAppender");

            mbean.setAttribute(new Attribute("priority", "ERROR"));

            assertThat(mbean.getAttribute("name")).isEqualTo(logger.getName());
            assertThat(mbean.getAttribute("priority")).isEqualTo("ERROR");
            assertThat(logger.getLevel()).isEqualTo(Level.ERROR);
        } finally {
            logger.setLevel(previousLevel);
        }
    }
}
