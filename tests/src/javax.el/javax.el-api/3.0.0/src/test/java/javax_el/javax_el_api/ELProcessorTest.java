/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_el.javax_el_api;

import java.lang.reflect.Method;

import javax.el.ELProcessor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ELProcessorTest {

    @Test
    void definesFunctionFromDeclaredMethodName() throws ClassNotFoundException, NoSuchMethodException {
        ELProcessor processor = new ELProcessor();

        processor.defineFunction("lib", "joinByName", FunctionLibrary.class.getName(), "join");

        Method mappedMethod = processor.getELManager().getELContext().getFunctionMapper()
                .resolveFunction("lib", "joinByName");
        assertThat(mappedMethod).isNotNull();
        assertThat(mappedMethod.getDeclaringClass()).isEqualTo(FunctionLibrary.class);
        assertThat(mappedMethod.getName()).isEqualTo("join");
    }

    @Test
    void definesFunctionFromSignatureWithReferenceAndArrayTypes() throws ClassNotFoundException, NoSuchMethodException {
        ELProcessor processor = new ELProcessor();

        processor.defineFunction(
                "lib",
                "flattenArrays",
                FunctionLibrary.class.getName(),
                "java.lang.String flatten(java.lang.String[],java.lang.Integer[][])");

        Method mappedMethod = processor.getELManager().getELContext().getFunctionMapper()
                .resolveFunction("lib", "flattenArrays");
        assertThat(mappedMethod).isNotNull();
        assertThat(mappedMethod.getDeclaringClass()).isEqualTo(FunctionLibrary.class);
        assertThat(mappedMethod.getName()).isEqualTo("flatten");
        assertThat(mappedMethod.getParameterTypes()).containsExactly(String[].class, Integer[][].class);
    }

    public static final class FunctionLibrary {
        public static String join() {
            return "joined";
        }

        public static String flatten(String[] values, Integer[][] numbers) {
            return values.length + ":" + numbers.length;
        }

        public static String flatten(String value) {
            return value;
        }
    }
}
