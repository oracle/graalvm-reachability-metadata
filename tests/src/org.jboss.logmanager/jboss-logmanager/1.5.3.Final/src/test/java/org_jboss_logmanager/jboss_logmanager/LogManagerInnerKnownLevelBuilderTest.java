/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_logmanager.jboss_logmanager;

import org.jboss.logmanager.Level;
import org.jboss.logmanager.LogManager;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LogManagerInnerKnownLevelBuilderTest {

    @Test
    void constructorInstallsJbossLevelsInJulLevelRegistry() {
        new LogManager();

        assertParsedLevel("TRACE", Level.TRACE.intValue());
        assertParsedLevel("DEBUG", Level.DEBUG.intValue());
        assertParsedLevel("WARN", Level.WARN.intValue());
        assertParsedLevel("ERROR", Level.ERROR.intValue());
        assertParsedLevel("FATAL", Level.FATAL.intValue());
    }

    private static void assertParsedLevel(final String name, final int expectedValue) {
        assertThat(java.util.logging.Level.parse(name).intValue()).isEqualTo(expectedValue);
    }
}
