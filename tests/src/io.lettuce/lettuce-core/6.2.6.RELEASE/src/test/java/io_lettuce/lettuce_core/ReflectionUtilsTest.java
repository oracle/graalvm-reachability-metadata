/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_lettuce.lettuce_core;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.dynamic.support.ReflectionUtils;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class ReflectionUtilsTest {

    @Test
    void findsMethodsOnClassesAndInterfacesAndInvokesThem() {
        ReflectiveSubject subject = new ReflectiveSubject("lettuce");

        Method concreteMethod = ReflectionUtils.findMethod(ReflectiveSubject.class, "echo", String.class);
        Method defaultMethod = ReflectionUtils.findMethod(ReflectiveSubject.class, "defaultLabel");
        Method interfaceMethod = ReflectionUtils.findMethod(ReflectiveOperations.class, "operationName");

        assertThat(concreteMethod).isNotNull();
        assertThat(defaultMethod).isNotNull();
        assertThat(interfaceMethod).isNotNull();
        assertThat(interfaceMethod.getDeclaringClass()).isEqualTo(ReflectiveOperations.class);
        assertThat(ReflectionUtils.invokeMethod(concreteMethod, subject, "core")).isEqualTo("lettuce-core");
        assertThat(ReflectionUtils.invokeMethod(defaultMethod, subject)).isEqualTo("default-lettuce");
    }

    @Test
    void visitsDeclaredFieldsAndReadsTheirValues() {
        ReflectiveSubject subject = new ReflectiveSubject("lettuce");
        Map<String, Object> values = new LinkedHashMap<>();

        ReflectionUtils.doWithFields(ReflectiveSubject.class,
                field -> values.put(field.getName(), ReflectionUtils.getField(field, subject)),
                field -> String.class.equals(field.getType()));

        assertThat(values).containsEntry("label", "lettuce").containsEntry("parentLabel", "parent");
    }

    public interface ReflectiveOperations {

        String operationName();

        default String defaultLabel() {
            return "default-" + operationName();
        }
    }

    public static class ReflectiveParent {
        public final String parentLabel = "parent";
    }

    public static class ReflectiveSubject extends ReflectiveParent implements ReflectiveOperations {
        public final String label;

        ReflectiveSubject(String label) {
            this.label = label;
        }

        public String echo(String suffix) {
            return label + "-" + suffix;
        }

        @Override
        public String operationName() {
            return label;
        }
    }
}
