/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.slf4j.impl;

import org.slf4j.ILoggerFactory;
import org.slf4j.spi.LoggerFactoryBinder;
import org_slf4j.jcl_over_slf4j.support.TestLocationAwareLoggerFactory;

public final class StaticLoggerBinder implements LoggerFactoryBinder {

    public static final StaticLoggerBinder SINGLETON = new StaticLoggerBinder();

    private final TestLocationAwareLoggerFactory loggerFactory = new TestLocationAwareLoggerFactory();

    private StaticLoggerBinder() {
    }

    public TestLocationAwareLoggerFactory getTestLoggerFactory() {
        return this.loggerFactory;
    }

    @Override
    public ILoggerFactory getLoggerFactory() {
        return this.loggerFactory;
    }

    @Override
    public String getLoggerFactoryClassStr() {
        return TestLocationAwareLoggerFactory.class.getName();
    }
}
