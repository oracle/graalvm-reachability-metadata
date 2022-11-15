/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_logging.jboss_logging;

import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class Jboss_loggingTest {

    private static final String PROVIDER = "org.jboss.logging.provider";

    @Test
    void slf4j(CapturedOutput output) throws Exception {
        String oldValue = System.setProperty(PROVIDER, "slf4j");
        try {
            Logger logger = Logger.getLogger(Jboss_loggingTest.class);

            logger.tracef("Trace: %s", "1");
            logger.debugf("Debug: %s", "2");
            logger.infof("Info: %s", "3");
            logger.warnf("Warn: %s", "4");
            logger.errorf("Error: %s", "5");
            logger.fatalf("Fatal: %s", "6");
            logger.error("Boom", new RuntimeException("boom"));

            assertThat(output.getAll())
                    .contains("DEBUG org.jboss.logging - Logging Provider: org.jboss.logging.Slf4jLoggerProvider found via system property")
                    .contains("DEBUG org_jboss_logging.jboss_logging.Jboss_loggingTest - Debug: 2")
                    .contains("INFO org_jboss_logging.jboss_logging.Jboss_loggingTest - Info: 3")
                    .contains("WARN org_jboss_logging.jboss_logging.Jboss_loggingTest - Warn: 4")
                    .contains("ERROR org_jboss_logging.jboss_logging.Jboss_loggingTest - Error: 5")
                    .contains("ERROR org_jboss_logging.jboss_logging.Jboss_loggingTest - Fatal: 6")
                    .contains("ERROR org_jboss_logging.jboss_logging.Jboss_loggingTest - Boom")
                    .contains("java.lang.RuntimeException: boom")
                    .contains("at org_jboss_logging.jboss_logging.Jboss_loggingTest.slf4j");
        } finally {
            if (oldValue == null) {
                System.clearProperty(PROVIDER);
            } else {
                System.setProperty(PROVIDER, oldValue);
            }
        }
    }
}
