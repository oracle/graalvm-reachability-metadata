/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import org.apache.pulsar.shade.org.apache.commons.lang3.ClassUtils;
import org.junit.jupiter.api.Test;

public class ClassUtilsTest {

    @Test
    void resolvesNamesArraysAndPublicInterfaceMethods() throws Exception {
        final List<Class<?>> classes = ClassUtils.convertClassNamesToClasses(
                List.of(String.class.getName(), Long.class.getName()));
        final Class<?> arrayType = ClassUtils.getClass(getClass().getClassLoader(), "java.lang.String[][]", false);
        final Method method = ClassUtils.getPublicMethod(HiddenNameProvider.class, "name");

        assertThat(classes).containsExactly(String.class, Long.class);
        assertThat(arrayType).isEqualTo(String[][].class);
        assertThat(method.getDeclaringClass()).isEqualTo(NameProvider.class);
        assertThat(method.getName()).isEqualTo("name");
    }

    public interface NameProvider {
        String name();
    }

    private static final class HiddenNameProvider implements NameProvider {
        @Override
        public String name() {
            return "orders";
        }
    }
}
