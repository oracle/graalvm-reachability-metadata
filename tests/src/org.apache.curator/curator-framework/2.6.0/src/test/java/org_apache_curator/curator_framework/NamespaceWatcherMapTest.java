/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_framework;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.imps.CuratorFrameworkImpl;
import org.apache.curator.retry.RetryOneTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class NamespaceWatcherMapTest {
    @Test
    void drainReadsGuavaDrainThreshold() throws Exception {
        try (CuratorFramework client = CuratorFrameworkFactory.newClient("127.0.0.1:1", new RetryOneTime(1))) {
            Field namespaceWatcherMapField = CuratorFrameworkImpl.class.getDeclaredField("namespaceWatcherMap");
            namespaceWatcherMapField.setAccessible(true);
            Object namespaceWatcherMap = namespaceWatcherMapField.get(client);
            assertNotNull(namespaceWatcherMap);

            Method drainMethod = namespaceWatcherMap.getClass().getDeclaredMethod("drain");
            drainMethod.setAccessible(true);
            drainMethod.invoke(namespaceWatcherMap);
        }
    }
}
