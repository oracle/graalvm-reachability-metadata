/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.druid;

import com.alibaba.druid.stat.JdbcSqlStatValue;
import com.alibaba.druid.support.monitor.dao.MonitorDaoJdbcImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class MonitorDaoJdbcImplTest {
    @Test
    void createsMonitoredValueFromBeanDescription() {
        MonitorDaoJdbcImpl dao = new MonitorDaoJdbcImpl();
        MonitorDaoJdbcImpl.BeanInfo beanInfo = new MonitorDaoJdbcImpl.BeanInfo(JdbcSqlStatValue.class);
        assertInstanceOf(JdbcSqlStatValue.class, dao.createInstance(beanInfo));
    }
}
