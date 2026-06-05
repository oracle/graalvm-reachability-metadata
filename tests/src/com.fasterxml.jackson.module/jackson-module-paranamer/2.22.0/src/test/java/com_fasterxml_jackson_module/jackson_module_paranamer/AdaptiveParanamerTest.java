/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_paranamer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.module.paranamer.shaded.AdaptiveParanamer;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

public class AdaptiveParanamerTest {
    @Test
    void defaultConstructorReadsParameterNamesFromAvailableStrategies() throws Exception {
        AdaptiveParanamer paranamer = new AdaptiveParanamer();
        Method method = MethodParameterFixture.class.getDeclaredMethod("format", String.class, int.class);

        String[] parameterNames = paranamer.lookupParameterNames(method, false);

        assertThat(parameterNames).containsExactly("message", "repeatCount");
    }

    private static final class MethodParameterFixture {
        String format(String message, int repeatCount) {
            StringBuilder result = new StringBuilder();
            for (int index = 0; index < repeatCount; index++) {
                result.append(message);
            }
            return result.toString();
        }
    }
}
