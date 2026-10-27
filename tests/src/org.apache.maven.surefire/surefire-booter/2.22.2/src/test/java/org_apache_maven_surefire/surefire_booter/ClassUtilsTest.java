/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_surefire.surefire_booter;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import org.apache.maven.surefire.shade.org.apache.commons.lang3.ClassUtils;
import org.junit.jupiter.api.Test;

public class ClassUtilsTest {

    @Test
    public void convertClassNamesToClassesLoadsKnownNamesAndKeepsMissingNamesAsNull() {
        List<Class<?>> classes = ClassUtils.convertClassNamesToClasses(
                Arrays.asList(String.class.getName(), "example.missing.Type"));

        assertThat(classes).containsExactly(String.class, null);
    }

    @Test
    public void getClassLoadsCanonicalClassNameThroughContextClassLoader() throws Exception {
        Class<?> loadedClass = ClassUtils.getClass(String.class.getName(), false);

        assertThat(loadedClass).isEqualTo(String.class);
    }

    @Test
    public void getPublicMethodReturnsMethodDeclaredOnPublicClass() throws Exception {
        Method method = ClassUtils.getPublicMethod(PublicGreeter.class, "greet", String.class);

        assertThat(method.getDeclaringClass()).isEqualTo(PublicGreeter.class);
        assertThat(method.getName()).isEqualTo("greet");
    }

    @Test
    public void getPublicMethodFindsPublicInterfaceMethod() throws Exception {
        Method method = ClassUtils.getPublicMethod(
                PackagePrivateContractImplementation.class, "contractValue");

        assertThat(method.getDeclaringClass()).isEqualTo(PublicContract.class);
        assertThat(method.getName()).isEqualTo("contractValue");
    }

    public interface PublicContract {
        String contractValue();
    }

    static class PackagePrivateContractImplementation implements PublicContract {
        public String contractValue() {
            return "implemented";
        }
    }

    public static class PublicGreeter {
        public String greet(String name) {
            return "Hello " + name;
        }
    }
}
