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
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class ReflectionsTest {
    @Test
    void createsObjectsAndResolvesClassesThroughTheUtilityApi() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();

        Named named = Reflections.createInstance(
                DefaultNamed.class.getName(), Named.class, classLoader);
        Object marker = Reflections.createInstance(DefaultMarker.class.getName(), classLoader);
        Prefix prefix = (Prefix) Reflections.createInstance(
                Prefix.class.getName(),
                classLoader,
                new Object[] {"pulsar"},
                new Class<?>[] {String.class});

        assertThat(named.name()).isEqualTo("default");
        assertThat(marker).isInstanceOf(DefaultMarker.class);
        assertThat(prefix.value()).isEqualTo("pulsar");
        assertThat(Reflections.classExists(DefaultNamed.class.getName())).isTrue();
        assertThat(Reflections.classImplementsIface(DefaultNamed.class.getName(), Named.class))
                .isTrue();
        assertThat(Reflections.loadClass("java.lang.String", classLoader))
                .isEqualTo(String.class);
        assertThat(Reflections.loadClass("Ljava.lang.String;", classLoader))
                .isEqualTo(String.class);
        assertThat(Reflections.loadClass("[[Ljava.lang.String;", classLoader))
                .isEqualTo(String[][].class);

        List<Field> fields = Reflections.getAllFields(ChildFields.class);
        assertThat(fields).extracting(Field::getName).contains("parent", "child");
    }

    public interface Named {
        String name();
    }

    public static final class DefaultNamed implements Named {
        public DefaultNamed() { }

        @Override
        public String name() {
            return "default";
        }
    }

    public static final class DefaultMarker {
        public DefaultMarker() { }
    }

    public static final class Prefix {
        private final String value;

        public Prefix(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    public static class ParentFields {
        private String parent;
    }

    public static final class ChildFields extends ParentFields {
        private String child;
    }
}
