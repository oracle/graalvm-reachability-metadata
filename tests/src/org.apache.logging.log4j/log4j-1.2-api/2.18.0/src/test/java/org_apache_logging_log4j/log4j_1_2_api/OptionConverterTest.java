/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_1_2_api;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.log4j.Level;
import org.apache.log4j.helpers.OptionConverter;
import org.junit.jupiter.api.Test;

public class OptionConverterTest {

    private static final String CUSTOM_LEVEL_NAME = "AUDIT";

    @Test
    void convertsCustomLevelThroughConfiguredLevelClass() {
        Level defaultLevel = Level.DEBUG;

        Level convertedLevel = OptionConverter.toLevel(
                CUSTOM_LEVEL_NAME + "#" + OptionConverterTest.class.getName(),
                defaultLevel
        );

        assertThat(convertedLevel).isSameAs(Level.FATAL);
    }

    public static Level toLevel(String levelName, Level defaultLevel) {
        if (CUSTOM_LEVEL_NAME.equals(levelName)) {
            return Level.FATAL;
        }
        return defaultLevel;
    }
}
