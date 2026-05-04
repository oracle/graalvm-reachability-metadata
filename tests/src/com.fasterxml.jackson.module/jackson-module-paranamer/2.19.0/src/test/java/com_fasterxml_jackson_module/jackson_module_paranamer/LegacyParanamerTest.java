/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_paranamer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.module.paranamer.shaded.LegacyParanamer;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

public class LegacyParanamerTest {
    @Test
    void readsParameterNamesFromEmbeddedParanamerDataField() throws Exception {
        LegacyParanamer paranamer = new LegacyParanamer();
        Method method = MethodParameterFixture.class.getDeclaredMethod("combine", String.class, int.class);

        String[] parameterNames = paranamer.lookupParameterNames(method);

        assertThat(parameterNames).containsExactly("text", "repeatCount");
    }

    public static final class MethodParameterFixture {
        public static final String __PARANAMER_DATA = """
                v1.0
                combine java.lang.String,int text,repeatCount
                """;

        public String combine(String text, int repeatCount) {
            StringBuilder result = new StringBuilder();
            for (int index = 0; index < repeatCount; index++) {
                result.append(text);
            }
            return result.toString();
        }
    }
}
