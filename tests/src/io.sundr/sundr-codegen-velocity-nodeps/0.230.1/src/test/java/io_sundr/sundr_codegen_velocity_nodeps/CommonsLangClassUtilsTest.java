/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.deps.org.apache.commons.lang.ClassUtils;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

public class CommonsLangClassUtilsTest {

    @Test
    public void convertClassNamesToClassesLoadsKnownNamesAndPreservesMissingEntriesAsNull() {
        List<String> names = Arrays.asList(
                String.class.getName(),
                "not.available.ClassUtilsTarget",
                Integer.class.getName());

        List classes = ClassUtils.convertClassNamesToClasses(names);

        assertThat(classes).containsExactly(String.class, null, Integer.class);
    }

    @Test
    public void getClassLoadsPrimitiveAndReferenceTypesWithProvidedClassLoader() throws ClassNotFoundException {
        ClassLoader loader = CommonsLangClassUtilsTest.class.getClassLoader();

        Class primitiveClass = ClassUtils.getClass(loader, "int", false);
        Class referenceClass = ClassUtils.getClass(loader, String.class.getName(), false);

        assertThat(primitiveClass).isSameAs(int.class);
        assertThat(referenceClass).isSameAs(String.class);
    }

    @Test
    public void getPublicMethodResolvesMethodDeclaredByPublicInterface() throws NoSuchMethodException {
        Method method = ClassUtils.getPublicMethod(HiddenGreeter.class, "greet", new Class[0]);

        assertThat(method.getDeclaringClass()).isSameAs(PublicGreeter.class);
        assertThat(method.getName()).isEqualTo("greet");
    }

    public interface PublicGreeter {
        String greet();
    }

    private static class HiddenGreeter implements PublicGreeter {
        @Override
        public String greet() {
            return "hello";
        }
    }
}
