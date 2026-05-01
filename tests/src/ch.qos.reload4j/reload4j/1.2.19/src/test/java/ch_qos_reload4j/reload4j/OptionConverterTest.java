/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.log4j.Level;
import org.apache.log4j.helpers.OptionConverter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class OptionConverterTest {
    private static final String CUSTOM_LEVEL_NAME = "CUSTOM_OPTION_LEVEL";

    @AfterEach
    void resetCustomLevelProvider() {
        CustomLevelProvider.requestedLevelName = null;
        CustomLevelProvider.requestedDefaultLevel = null;
    }

    @Test
    void convertsCustomLevelUsingConfiguredProvider() {
        Level defaultLevel = Level.DEBUG;

        Level convertedLevel = OptionConverter.toLevel(
                CUSTOM_LEVEL_NAME + "#" + CustomLevelProvider.class.getName(), defaultLevel);

        assertThat(convertedLevel).isSameAs(Level.WARN);
        assertThat(CustomLevelProvider.requestedLevelName).isEqualTo(CUSTOM_LEVEL_NAME);
        assertThat(CustomLevelProvider.requestedDefaultLevel).isSameAs(defaultLevel);
    }

    public static final class CustomLevelProvider {
        static String requestedLevelName;
        static Level requestedDefaultLevel;

        private CustomLevelProvider() {
        }

        public static Level toLevel(String name, Level defaultLevel) {
            requestedLevelName = name;
            requestedDefaultLevel = defaultLevel;
            return Level.WARN;
        }
    }
}
