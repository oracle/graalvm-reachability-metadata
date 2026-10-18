/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.druid;

import com.alibaba.druid.mock.MockDriver;
import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.util.Properties;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MockDriverTest {
    @Test
    void opensMockConnectionAndPublishesMBean() throws Exception {
        try (Connection connection = MockDriver.instance.connect("jdbc:mock:test", new Properties())) {
            assertNotNull(connection);
        }

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        MBeanInfo info = server.getMBeanInfo(new ObjectName("com.alibaba.druid:type=MockDriver"));
        assertTrue(info.getAttributes().length > 0);
    }
}
