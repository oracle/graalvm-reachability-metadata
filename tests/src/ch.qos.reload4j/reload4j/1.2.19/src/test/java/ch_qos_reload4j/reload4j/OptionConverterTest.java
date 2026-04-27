/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.log4j.Layout;
import org.apache.log4j.Level;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.helpers.OptionConverter;
import org.junit.jupiter.api.Test;

public class OptionConverterTest {
    @Test
    void resolvesCustomLevelThroughConfiguredLevelClass() {
        Level defaultLevel = Level.ERROR;

        Level level = OptionConverter.toLevel("INFO#" + Level.class.getName(), defaultLevel);

        assertThat(level).isSameAs(Level.INFO);
    }

    @Test
    void instantiatesAssignableClassByName() {
        Object defaultValue = new Object();

        Object instance = OptionConverter.instantiateByClassName(PatternLayout.class.getName(), Layout.class, defaultValue);

        assertThat(instance).isInstanceOf(PatternLayout.class);
        assertThat(instance).isNotSameAs(defaultValue);
    }
}
