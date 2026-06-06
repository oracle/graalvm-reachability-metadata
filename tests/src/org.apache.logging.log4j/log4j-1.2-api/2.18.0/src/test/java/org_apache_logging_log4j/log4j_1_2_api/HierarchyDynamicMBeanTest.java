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
import org.apache.log4j.LogManager;
import org.apache.log4j.jmx.HierarchyDynamicMBean;
import org.apache.log4j.spi.LoggerRepository;
import org.junit.jupiter.api.Test;

public class HierarchyDynamicMBeanTest {

    @Test
    void exposesConstructorsOperationsAndThresholdAttribute() throws Exception {
        LoggerRepository repository = LogManager.getLoggerRepository();
        Level previousThreshold = repository.getThreshold();

        try {
            HierarchyDynamicMBean mbean = new HierarchyDynamicMBean();
            MBeanInfo mbeanInfo = mbean.getMBeanInfo();

            assertThat(Arrays.stream(mbeanInfo.getConstructors())
                    .map(MBeanConstructorInfo::getName))
                    .contains(HierarchyDynamicMBean.class.getName());
            assertThat(Arrays.stream(mbeanInfo.getAttributes())
                    .map(MBeanAttributeInfo::getName))
                    .contains("threshold");
            assertThat(Arrays.stream(mbeanInfo.getOperations())
                    .map(MBeanOperationInfo::getName))
                    .contains("addLoggerMBean");

            mbean.setAttribute(new Attribute("threshold", "WARN"));

            assertThat(mbean.getAttribute("threshold")).isEqualTo(Level.WARN);
            assertThat(repository.getThreshold()).isEqualTo(Level.WARN);
        } finally {
            repository.setThreshold(previousThreshold);
        }
    }
}
