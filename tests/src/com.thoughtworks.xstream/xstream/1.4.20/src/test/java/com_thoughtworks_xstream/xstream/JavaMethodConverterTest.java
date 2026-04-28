/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import com.thoughtworks.xstream.XStream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaMethodConverterTest {
    @Test
    void unmarshalsDeclaredMethodDescription() {
        XStream xstream = configuredXStream();
        Object restored = xstream.fromXML("""
                <method>
                  <class>%s</class>
                  <name>combine</name>
                  <parameter-types>
                    <class>java.lang.String</class>
                    <class>int</class>
                  </parameter-types>
                </method>
                """.formatted(InvocationTarget.class.getName()));

        assertThat(restored).isInstanceOf(Method.class);
        Method method = (Method)restored;
        assertThat(method.getDeclaringClass()).isEqualTo(InvocationTarget.class);
        assertThat(method.getName()).isEqualTo("combine");
        assertThat(method.getParameterTypes()).containsExactly(String.class, int.class);
    }

    @Test
    void unmarshalsDeclaredConstructorDescription() {
        XStream xstream = configuredXStream();
        Object restored = xstream.fromXML("""
                <constructor>
                  <class>%s</class>
                  <parameter-types>
                    <class>java.lang.String</class>
                    <class>int</class>
                  </parameter-types>
                </constructor>
                """.formatted(InvocationTarget.class.getName()));

        assertThat(restored).isInstanceOf(Constructor.class);
        Constructor<?> constructor = (Constructor<?>)restored;
        assertThat(constructor.getDeclaringClass()).isEqualTo(InvocationTarget.class);
        assertThat(constructor.getParameterTypes()).containsExactly(String.class, int.class);
    }

    private static XStream configuredXStream() {
        XStream xstream = new XStream();
        xstream.allowTypes(new Class[]{
            Constructor.class,
            int.class,
            InvocationTarget.class,
            Method.class,
            String.class
        });
        return xstream;
    }

    public static final class InvocationTarget {
        private final String prefix;
        private final int count;

        public InvocationTarget(String prefix, int count) {
            this.prefix = prefix;
            this.count = count;
        }

        public String combine(String suffix, int repetitions) {
            return prefix + suffix.repeat(count + repetitions);
        }
    }
}
