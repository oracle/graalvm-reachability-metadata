/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package log4j.log4j;

import org.apache.log4j.Level;
import org.apache.log4j.helpers.OptionConverter;
import org.apache.log4j.spi.OptionHandler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OptionConverterTest {

    @Test
    void resolvesCustomLevelsThroughTheNamedFactoryMethod() {
        Level level = OptionConverter.toLevel("CUSTOM#" + CustomLevel.class.getName(), Level.INFO);

        assertThat(level).isSameAs(CustomLevel.CUSTOM);
    }

    @Test
    void instantiatesAssignableTypesByClassName() {
        Object instance = OptionConverter.instantiateByClassName(
                CustomOptionHandler.class.getName(),
                OptionHandler.class,
                null);

        assertThat(instance).isInstanceOf(CustomOptionHandler.class);
    }

    public static final class CustomLevel extends Level {
        private static final long serialVersionUID = 1L;
        private static final CustomLevel CUSTOM = new CustomLevel();

        private CustomLevel() {
            super(35000, "CUSTOM", 0);
        }

        public static Level toLevel(String value, Level defaultLevel) {
            if (CUSTOM.toString().equalsIgnoreCase(value)) {
                return CUSTOM;
            }
            return defaultLevel;
        }
    }

    public static final class CustomOptionHandler implements OptionHandler {
        @Override
        public void activateOptions() {
        }
    }
}
