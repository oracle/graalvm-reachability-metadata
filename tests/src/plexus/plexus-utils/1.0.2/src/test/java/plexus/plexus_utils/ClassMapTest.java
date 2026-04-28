/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package plexus.plexus_utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.codehaus.plexus.util.introspection.ClassMap;
import org.junit.jupiter.api.Test;

public class ClassMapTest {
    @Test
    void findsPublicInterfaceMethodForPackagePrivateImplementation() throws Exception {
        ClassMap classMap = new ClassMap(ClassMapGreetingRunnable.class);

        Method method = classMap.findMethod("run", new Object[0]);

        assertThat(method).isNotNull();
        assertThat(method.getName()).isEqualTo("run");
        assertThat(method.getParameterTypes()).isEmpty();
        assertThat(Modifier.isPublic(method.getDeclaringClass().getModifiers())).isTrue();
    }

    @Test
    void resolvesPackagePrivateMethodToPublicInterfaceMethod() throws Exception {
        Method packagePrivateClassMethod = ClassMapGreetingRunnable.class.getMethod("run");

        Method publicMethod = ClassMap.getPublicMethod(packagePrivateClassMethod);

        assertThat(Modifier.isPublic(packagePrivateClassMethod.getDeclaringClass().getModifiers())).isFalse();
        assertThat(publicMethod).isNotNull();
        assertThat(publicMethod.getDeclaringClass()).isEqualTo(Runnable.class);
        assertThat(publicMethod.getName()).isEqualTo("run");
        assertThat(publicMethod.getParameterTypes()).isEmpty();
    }
}

class ClassMapGreetingRunnable implements Runnable {
    @Override
    public void run() {
    }
}
