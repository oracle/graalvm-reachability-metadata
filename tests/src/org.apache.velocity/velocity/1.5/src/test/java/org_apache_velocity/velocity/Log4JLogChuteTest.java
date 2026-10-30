/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity;

import java.io.StringWriter;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.WriterAppender;
import org.apache.velocity.runtime.RuntimeInstance;
import org.apache.velocity.runtime.log.Log4JLogChute;
import org.apache.velocity.runtime.log.LogChute;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class Log4JLogChuteTest {
    @Test
    void forwardsTraceAndWarningMessagesToConfiguredLogger() throws Exception {
        Logger logger = Logger.getLogger(Log4JLogChuteTest.class.getName());
        Level originalLevel = logger.getLevel();
        boolean originalAdditivity = logger.getAdditivity();
        StringWriter output = new StringWriter();
        WriterAppender appender = new WriterAppender(new PatternLayout("%p:%m%n"), output);
        Log4JLogChute chute = new Log4JLogChute();
        logger.setLevel(Level.TRACE);
        logger.setAdditivity(false);
        logger.addAppender(appender);
        try {
            RuntimeInstance runtime = new RuntimeInstance();
            runtime.setProperty(Log4JLogChute.RUNTIME_LOG_LOG4J_LOGGER, logger.getName());
            chute.init(runtime);
            assertTrue(chute.isLevelEnabled(LogChute.TRACE_ID));
            chute.log(LogChute.TRACE_ID, "template trace");
            chute.log(LogChute.WARN_ID, "template warning");
            assertTrue(output.toString().contains("TRACE:template trace"));
            assertTrue(output.toString().contains("WARN:template warning"));
        } finally {
            chute.shutdown();
            logger.removeAppender(appender);
            appender.close();
            logger.setLevel(originalLevel);
            logger.setAdditivity(originalAdditivity);
        }
    }
}
