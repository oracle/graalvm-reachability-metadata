/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.ibm.websphere.management;

import java.lang.management.ManagementFactory;

import javax.management.MBeanServer;

public final class AdminServiceFactory {

    private static final TestMBeanFactory MBEAN_FACTORY = new TestMBeanFactory();

    private AdminServiceFactory() {
    }

    public static TestMBeanFactory getMBeanFactory() {
        return MBEAN_FACTORY;
    }

    public static final class TestMBeanFactory {

        public MBeanServer getMBeanServer() {
            return ManagementFactory.getPlatformMBeanServer();
        }
    }
}
