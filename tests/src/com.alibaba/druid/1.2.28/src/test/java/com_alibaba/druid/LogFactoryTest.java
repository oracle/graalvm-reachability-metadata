/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.druid;

import com.alibaba.druid.support.logging.Jdk14LoggingImpl;
import com.alibaba.druid.support.logging.Log;
import com.alibaba.druid.support.logging.LogFactory;
import com.alibaba.druid.support.logging.SLF4JImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class LogFactoryTest {
    @Test
    void selectsAvailableLoggingImplementations() {
        Log defaultLog = LogFactory.getLog(LogFactoryTest.class);
        assertInstanceOf(SLF4JImpl.class, defaultLog);

        LogFactory.selectJavaLogging();
        assertInstanceOf(Jdk14LoggingImpl.class, LogFactory.getLog(LogFactoryTest.class));
    }
}
