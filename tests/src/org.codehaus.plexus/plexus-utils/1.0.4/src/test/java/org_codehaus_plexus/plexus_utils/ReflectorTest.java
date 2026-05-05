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
    @Test
    void createsObjectsUsingMatchingPublicConstructors() throws Exception {
        Reflector reflector = new Reflector();

        Object instance = reflector.newInstance(ReflectorFixture.class, new Object[] {"created"});

        assertThat(instance).isInstanceOf(ReflectorFixture.class);
        assertThat(((ReflectorFixture) instance).value).isEqualTo("created");
    }

    @Test
    void readsPublicStaticAndInstanceFields() throws Exception {
        Reflector reflector = new Reflector();
        ReflectorFixture fixture = new ReflectorFixture("field-value");

        assertThat(reflector.getStaticField(ReflectorFixture.class, "STATIC_VALUE")).isEqualTo("static-value");
        assertThat(reflector.getField(fixture, "value")).isEqualTo("field-value");
    }

    @Test
    void invokesPublicInstanceStaticAndSingletonMethods() throws Exception {
        Reflector reflector = new Reflector();
        ReflectorFixture fixture = new ReflectorFixture("call");

        assertThat(reflector.invoke(fixture, "append", new Object[] {"-suffix"})).isEqualTo("call-suffix");
        assertThat(reflector.invokeStatic(ReflectorFixture.class, "join", new Object[] {"left", "right"}))
                .isEqualTo("left:right");

        Object singleton = reflector.getSingleton(ReflectorFixture.class, new Object[] {"singleton"});

        assertThat(singleton).isInstanceOf(ReflectorFixture.class);
        assertThat(((ReflectorFixture) singleton).value).isEqualTo("singleton");
    }

    @Test
    void objectPropertyCannotReachAccessorInvocationInVersion104() {
        Reflector reflector = new Reflector();
        ReflectorFixture fixture = new ReflectorFixture("property-value");

        // `getObjectProperty` finds `getValue`, but then looks for `value` on `Class.class`
        // before invoking the accessor. `Class` exposes no public fields, so version 1.0.4
        // always fails at that field lookup before the final `Method.invoke` call site.
        assertThatThrownBy(() -> reflector.getObjectProperty(fixture, "value"))
                .isInstanceOf(ReflectorException.class)
                .hasCauseInstanceOf(NoSuchFieldException.class);
    }

    public static class ReflectorFixture {
        public static final String STATIC_VALUE = "static-value";

        public String value;

        public ReflectorFixture() {
            this("default");
        }

        public ReflectorFixture(String value) {
            this.value = value;
        }

        public static ReflectorFixture getInstance(String value) {
            return new ReflectorFixture(value);
        }

        public static String join(String left, String right) {
            return left + ":" + right;
        }

        public String append(String suffix) {
            return value + suffix;
        }

        public String getValue() {
            return value;
        }
    }
}
