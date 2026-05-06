/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j;

import org.apache.log4j.Level;
import org.apache.log4j.helpers.OptionConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OptionConverterTest {
    @BeforeEach
    void resetRecordingState() {
        CustomLevel.lastRequestedLevel = null;
        RecordingRunnable.instances = 0;
    }

    @Test
    void resolvesCustomLevelThroughClassName() {
        Level defaultLevel = Level.INFO;

        Level convertedLevel = OptionConverter.toLevel("VERBOSE#" + CustomLevel.class.getName(), defaultLevel);

        assertThat(convertedLevel).isSameAs(CustomLevel.VERBOSE);
        assertThat(CustomLevel.lastRequestedLevel).isEqualTo("VERBOSE");
    }

    @Test
    void instantiatesAssignableClassByName() {
        Object fallback = new Object();

        Object instance = OptionConverter.instantiateByClassName(
                RecordingRunnable.class.getName(),
                Runnable.class,
                fallback);

        assertThat(instance).isInstanceOf(RecordingRunnable.class);
        assertThat(instance).isNotSameAs(fallback);
        assertThat(RecordingRunnable.instances).isEqualTo(1);
    }

    public static final class CustomLevel extends Level {
        private static final Level VERBOSE = new CustomLevel(4500, "VERBOSE", 7);
        private static String lastRequestedLevel;

        private CustomLevel(int level, String levelStr, int syslogEquivalent) {
            super(level, levelStr, syslogEquivalent);
        }

        public static Level toLevel(String levelName, Level defaultLevel) {
            lastRequestedLevel = levelName;
            return VERBOSE;
        }
    }

    public static final class RecordingRunnable implements Runnable {
        private static int instances;

        public RecordingRunnable() {
            instances++;
        }

        @Override
        public void run() {
        }
    }
}
