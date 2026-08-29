/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.internal.util.ReflectHelper;
import org.hibernate.type.StandardBasicTypes;
import org.hibernate.type.Type;
import org.hibernate.type.spi.TypeConfiguration;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectHelperTest {

    @Test
    public void resolvesClassesConstructorsAndBeanMethods() throws Exception {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        Class<?> first;
        Class<?> second;
        try {
            Thread.currentThread().setContextClassLoader(null);
            first = ReflectHelper.classForName(SampleValue.class.getName(), ReflectHelperTest.class);
            second = ReflectHelper.classForName(SampleValue.class.getName());
        }
        finally {
            Thread.currentThread().setContextClassLoader(original);
        }

        Constructor<SampleValue> declared = ReflectHelper.getConstructor(
                SampleValue.class,
                String.class
        );
        Type stringType = new TypeConfiguration().getBasicTypeRegistry()
                .resolve(StandardBasicTypes.STRING);
        Constructor<?> publicConstructor = ReflectHelper.getConstructor(
                SampleValue.class,
                new Type[]{stringType}
        );
        Method activeGetter = ReflectHelper.findGetterMethod(SampleValue.class, "active");
        Method readyGetter = ReflectHelper.findGetterMethod(SampleValue.class, "ready");
        Method publicMethod = ReflectHelper.getMethod(SampleValue.class, "getName");

        assertThat(first).isSameAs(SampleValue.class);
        assertThat(second).isSameAs(SampleValue.class);
        assertThat(declared).isNotNull();
        assertThat(publicConstructor).isNotNull();
        assertThat(activeGetter.getName()).isEqualTo("getActive");
        assertThat(readyGetter.getName()).isEqualTo("isReady");
        assertThat(publicMethod).isNotNull();
    }

    public static class SampleValue {
        private final String name;

        public SampleValue(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public boolean getActive() {
            return true;
        }

        public boolean isReady() {
            return true;
        }
    }
}
