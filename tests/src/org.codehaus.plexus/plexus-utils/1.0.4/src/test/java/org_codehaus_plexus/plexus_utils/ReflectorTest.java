/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_utils;

import org.codehaus.plexus.util.reflection.Reflector;
import org.codehaus.plexus.util.reflection.ReflectorException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ReflectorTest {
    private final Reflector reflector = new Reflector();

    @Test
    void createsInstancesThroughMatchingPublicConstructors() throws Exception {
        Object instance = reflector.newInstance(ConstructedValue.class, new Object[] {"created"});

        assertThat(instance).isInstanceOf(ConstructedValue.class);
        assertThat(((ConstructedValue) instance).description).isEqualTo("created");
    }

    @Test
    void readsPublicInstanceFields() throws Exception {
        FieldTarget target = new FieldTarget("field-value");

        Object value = reflector.getField(target, "name");

        assertThat(value).isEqualTo("field-value");
    }

    @Test
    void readsPublicStaticFields() throws Exception {
        Object value = reflector.getStaticField(StaticFieldTarget.class, "MESSAGE");

        assertThat(value).isEqualTo("static-message");
    }

    @Test
    void invokesPublicInstanceMethods() throws Exception {
        InvocationTarget target = new InvocationTarget("prefix");

        Object result = reflector.invoke(target, "append", new Object[] {"suffix"});

        assertThat(result).isEqualTo("prefix:suffix");
    }

    @Test
    void invokesPublicStaticMethods() throws Exception {
        Object result = reflector.invokeStatic(StaticInvocationTarget.class, "decorate", new Object[] {"value"});

        assertThat(result).isEqualTo("[value]");
    }

    @Test
    void obtainsSingletonsFromPublicGetInstanceFactories() throws Exception {
        Object result = reflector.getSingleton(SingletonFactory.class, new Object[] {"singleton-value"});

        assertThat(result).isInstanceOf(SingletonFactory.class);
        assertThat(((SingletonFactory) result).value).isEqualTo("singleton-value");
    }

    @Test
    void reportsObjectPropertyAccessorFailuresFromLegacyClassFieldLookup() {
        assertThatThrownBy(() -> reflector.getObjectProperty(new BeanWithAccessor(), "value"))
            .isInstanceOf(ReflectorException.class)
            .hasCauseInstanceOf(NoSuchFieldException.class)
            .hasMessageContaining("value");
    }

    public static class ConstructedValue {
        public final String description;

        public ConstructedValue(String description) {
            this.description = description;
        }
    }

    public static class FieldTarget {
        public final String name;

        public FieldTarget(String name) {
            this.name = name;
        }
    }

    public static class StaticFieldTarget {
        public static final String MESSAGE = "static-message";
    }

    public static class InvocationTarget {
        private final String prefix;

        public InvocationTarget(String prefix) {
            this.prefix = prefix;
        }

        public String append(String suffix) {
            return prefix + ":" + suffix;
        }
    }

    public static class StaticInvocationTarget {
        public static String decorate(String value) {
            return "[" + value + "]";
        }
    }

    public static class SingletonFactory {
        public final String value;

        private SingletonFactory(String value) {
            this.value = value;
        }

        public static SingletonFactory getInstance(String value) {
            return new SingletonFactory(value);
        }
    }

    public static class BeanWithAccessor {
        public String getValue() {
            return "accessor-value";
        }
    }
}
