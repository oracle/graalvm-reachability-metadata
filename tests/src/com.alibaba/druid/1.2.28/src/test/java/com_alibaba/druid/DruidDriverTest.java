/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.druid;

import com.alibaba.druid.proxy.DruidDriver;
import java.lang.management.ManagementFactory;
import java.sql.Driver;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DruidDriverTest {
    @Test
    void createsWrappedDriverAndPublishesMBean() throws Exception {
        Driver driver = DruidDriver.createDriver("org.h2.Driver");
        assertEquals("org.h2.Driver", driver.getClass().getName());

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        MBeanInfo info = server.getMBeanInfo(new ObjectName("com.alibaba.druid:type=DruidDriver"));
        assertTrue(info.getAttributes().length > 0);
    }
}
