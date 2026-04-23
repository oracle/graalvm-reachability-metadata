/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_slf4j.jcl_over_slf4j.support;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;

public final class TestLocationAwareLoggerFactory implements ILoggerFactory {

    private final Map<String, TestLocationAwareLogger> loggers = new ConcurrentHashMap<>();

    @Override
    public Logger getLogger(String name) {
        return this.loggers.computeIfAbsent(name, TestLocationAwareLogger::new);
    }

    public TestLocationAwareLogger getRecordedLogger(String name) {
        return this.loggers.computeIfAbsent(name, TestLocationAwareLogger::new);
    }
}
