/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_paranamer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.module.paranamer.shaded.DefaultParanamer;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

public class DefaultParanamerTest {
    @Test
    void readsParameterNamesFromParanamerDataField() throws Exception {
        DefaultParanamer paranamer = new DefaultParanamer();
        Method method = ParameterDataFixture.class.getDeclaredMethod("repeat", String.class, int.class);

        String[] parameterNames = paranamer.lookupParameterNames(method, true);

        assertThat(parameterNames).containsExactly("message", "repeatCount");
    }

    public static final class ParameterDataFixture {
        public static final String __PARANAMER_DATA = "repeat java.lang.String,int message,repeatCount\n";

        public String repeat(String message, int repeatCount) {
            StringBuilder result = new StringBuilder();
            for (int index = 0; index < repeatCount; index++) {
                result.append(message);
            }
            return result.toString();
        }
    }
}
