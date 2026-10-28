/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.List;

import org.apache.pulsar.common.util.Reflections;
import org.junit.jupiter.api.Test;

public class ReflectionsTest {
    @Test
    void createsConfiguredImplementationsByClassName() {
        Greeter typed = Reflections.createInstance(
                DefaultGreeter.class.getName(), Greeter.class, getClass().getClassLoader());
        Object untyped = Reflections.createInstance(
                UntypedValue.class.getName(), getClass().getClassLoader());
        Object configured = Reflections.createInstance(
                ConfiguredValue.class.getName(),
                getClass().getClassLoader(),
                new Object[] {"configured"},
                new Class<?>[] {String.class});

        assertThat(typed.greet("Pulsar")).isEqualTo("hello Pulsar");
        assertThat(untyped).isInstanceOf(UntypedValue.class);
        assertThat(((ConfiguredValue) configured).value).isEqualTo("configured");
    }

    @Test
    void inspectsClassesAndResolvesDescriptorAndArrayNames() throws Exception {
        ClassLoader loader = getClass().getClassLoader();

        assertThat(Reflections.classExists(DefaultGreeter.class.getName())).isTrue();
        assertThat(Reflections.classImplementsIface(DefaultGreeter.class.getName(), Greeter.class)).isTrue();
        assertThat(Reflections.loadClass("Ljava.lang.String;", loader)).isEqualTo(String.class);
        assertThat(Reflections.loadClass("[[java.lang.String", loader)).isEqualTo(String[][].class);

        List<Field> fields = Reflections.getAllFields(ChildFields.class);
        assertThat(fields).extracting(Field::getName).contains("child", "parent");
    }

    public interface Greeter {
        String greet(String name);
    }

    public static class DefaultGreeter implements Greeter {
        public DefaultGreeter() {
        }

        @Override
        public String greet(String name) {
            return "hello " + name;
        }
    }

    public static class UntypedValue {
        public UntypedValue() {
        }
    }

    public static class ConfiguredValue {
        private final String value;

        public ConfiguredValue(String value) {
            this.value = value;
        }
    }

    public static class ParentFields {
        private String parent;
    }

    public static class ChildFields extends ParentFields {
        private String child;
    }
}
