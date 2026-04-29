/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import org.modelmapper.internal.objenesis.instantiator.util.ClassUtils;

public class ClassUtilsTest {
    @Test
    void findsExistingClassByNameWithProvidedClassLoader() {
        Class<?> existingClass = ClassUtils.getExistingClass(
            Thread.currentThread().getContextClassLoader(),
            ModelMapper.class.getName());

        assertThat(existingClass).isSameAs(ModelMapper.class);
    }

    @Test
    void returnsNullWhenClassCannotBeFoundByProvidedClassLoader() {
        Class<?> missingClass = ClassUtils.getExistingClass(
            Thread.currentThread().getContextClassLoader(),
            ModelMapper.class.getName() + "Missing");

        assertThat(missingClass).isNull();
    }

    @Test
    void createsInstanceWithPublicNoArgumentConstructor() {
        InstanceTarget.constructorCalls.set(0);

        InstanceTarget instance = ClassUtils.newInstance(InstanceTarget.class);

        assertThat(instance).isNotNull();
        assertThat(instance.message).isEqualTo("created by ClassUtils");
        assertThat(InstanceTarget.constructorCalls).hasValue(1);
    }

    public static final class InstanceTarget {
        static final AtomicInteger constructorCalls = new AtomicInteger();

        final String message;

        public InstanceTarget() {
            constructorCalls.incrementAndGet();
            this.message = "created by ClassUtils";
        }
    }
}
