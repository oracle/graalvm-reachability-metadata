/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat.jasper_runtime;

import org.apache.jasper.runtime.ProtectedFunctionMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

public class ProtectedFunctionMapperTest {
    @Test
    @SuppressWarnings("removal")
    void mapFunctionResolvesDeclaredMethodWithoutPackageProtection() {
        assertThat(System.getSecurityManager()).isNull();
        ProtectedFunctionMapper mapper = ProtectedFunctionMapper.getInstance();

        mapper.mapFunction("text:combine", FunctionLibrary.class, "combine",
                new Class[] {String.class, String.class});
        Method method = mapper.resolveFunction("text", "combine");

        assertThat(method).isNotNull();
        assertThat(method.getDeclaringClass()).isEqualTo(FunctionLibrary.class);
        assertThat(method.getName()).isEqualTo("combine");
        assertThat(method.getReturnType()).isEqualTo(String.class);
        assertThat(method.getParameterTypes()).containsExactly(String.class, String.class);
    }

    @Test
    @SuppressWarnings("removal")
    void getMapForFunctionResolvesSingleDeclaredMethodWithoutPackageProtection() {
        assertThat(System.getSecurityManager()).isNull();

        ProtectedFunctionMapper mapper = ProtectedFunctionMapper.getMapForFunction(
                "text:length", FunctionLibrary.class, "length", new Class[] {String.class});
        Method method = mapper.resolveFunction("text", "length");

        assertThat(method).isNotNull();
        assertThat(method.getDeclaringClass()).isEqualTo(FunctionLibrary.class);
        assertThat(method.getName()).isEqualTo("length");
        assertThat(method.getReturnType()).isEqualTo(int.class);
        assertThat(method.getParameterTypes()).containsExactly(String.class);
    }

    public static class FunctionLibrary {
        public static String combine(String first, String second) {
            return first + second;
        }

        public static int length(String value) {
            return value.length();
        }
    }
}
