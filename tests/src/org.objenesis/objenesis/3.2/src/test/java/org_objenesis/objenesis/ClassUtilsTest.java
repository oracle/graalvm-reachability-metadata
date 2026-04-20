/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_objenesis.objenesis;

import java.util.concurrent.atomic.AtomicInteger;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.objenesis.instantiator.util.ClassUtils;

public class ClassUtilsTest {

    @Test
    void loadsAnExistingClassFromTheProvidedClassLoader() {
        InitializationTracker.count.set(0);

        Class<ExistingTarget> loadedClass = ClassUtils.getExistingClass(
            ClassUtilsTest.class.getClassLoader(), ExistingTarget.class.getName());

        Assertions.assertThat(loadedClass).isEqualTo(ExistingTarget.class);
        Assertions.assertThat(InitializationTracker.count).hasValue(1);
    }

    @Test
    void createsANewInstanceUsingTheTargetNoArgConstructor() {
        ConstructorTarget.constructorCalls.set(0);

        ConstructorTarget instance = ClassUtils.newInstance(ConstructorTarget.class);

        Assertions.assertThat(instance).isNotNull();
        Assertions.assertThat(instance.message).isEqualTo("constructed");
        Assertions.assertThat(ConstructorTarget.constructorCalls).hasValue(1);
    }

    public static final class ExistingTarget {
        static {
            InitializationTracker.count.incrementAndGet();
        }
    }

    public static final class InitializationTracker {
        static final AtomicInteger count = new AtomicInteger();
    }

    public static final class ConstructorTarget {
        static final AtomicInteger constructorCalls = new AtomicInteger();

        final String message;

        public ConstructorTarget() {
            constructorCalls.incrementAndGet();
            this.message = "constructed";
        }
    }
}
