/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity_dep;

import org.apache.velocity.util.introspection.ClassMap;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassMapTest {
    @Test
    void resolvesPublicInterfaceMethodForNonPublicImplementation() throws Exception {
        Method implementationMethod = ClassMapPublicMethodSubject.class.getMethod("get");

        Method publicMethod = ClassMap.getPublicMethod(implementationMethod);

        assertThat(publicMethod).isNotNull();
        assertThat(publicMethod.getDeclaringClass()).isEqualTo(Supplier.class);
        assertThat(publicMethod.getName()).isEqualTo("get");
        assertThat(publicMethod.getParameterTypes()).isEmpty();
    }
}

final class ClassMapPublicMethodSubject implements Supplier<String> {
    @Override
    public String get() {
        return "public method";
    }
}
