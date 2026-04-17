/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_core;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.core.util.EnvUtil;
import org.junit.jupiter.api.Test;

public class EnvUtilTest {

    @Test
    void reportsJaninoAvailabilityFromTheLogbackClassLoader() {
        ClassLoader classLoader = EnvUtil.class.getClassLoader();
        boolean janinoClassPresent = classLoader.getResource("org/codehaus/janino/ScriptEvaluator.class") != null;

        assertThat(EnvUtil.isJaninoAvailable()).isEqualTo(janinoClassPresent);
    }
}
