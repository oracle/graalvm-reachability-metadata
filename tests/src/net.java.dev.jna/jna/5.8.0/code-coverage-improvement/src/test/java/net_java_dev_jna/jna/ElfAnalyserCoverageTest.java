/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ElfAnalyserCoverageTest {
    @Test
    void parameterTypesRoundTripThroughEnumApi() throws Exception {
        Class<?> parameterType = Class.forName(
                "com.sun.jna.ELFAnalyser$ArmAeabiAttributesTag$ParameterType");
        Method values = parameterType.getMethod("values");
        Method valueOf = parameterType.getMethod("valueOf", String.class);
        values.setAccessible(true);
        valueOf.setAccessible(true);

        Object[] types = (Object[]) values.invoke(null);
        assertThat(Arrays.stream(types).map(Object::toString))
                .containsExactly("UINT32", "NTBS", "ULEB128");
        for (Object type : types) {
            assertThat(valueOf.invoke(null, ((Enum<?>) type).name())).isSameAs(type);
        }
    }
}
