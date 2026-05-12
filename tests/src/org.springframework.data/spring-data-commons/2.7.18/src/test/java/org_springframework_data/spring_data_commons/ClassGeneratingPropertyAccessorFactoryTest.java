/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_commons;

import static org.assertj.core.api.Assertions.assertThat;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;
import org.springframework.data.mapping.PersistentPropertyAccessor;
import org.springframework.data.mapping.model.BasicPersistentEntity;
import org.springframework.data.mapping.model.ClassGeneratingPropertyAccessorFactory;
import org.springframework.data.util.ClassTypeInformation;

public class ClassGeneratingPropertyAccessorFactoryTest {
    private static final String NATIVE_IMAGE_RUNTIME = "runtime";

    @Test
    void createsGeneratedAccessorForEntityBean() {
        ClassGeneratingPropertyAccessorFactory factory = new ClassGeneratingPropertyAccessorFactory();
        BasicPersistentEntity<AccessorSample, ?> entity = new BasicPersistentEntity<>(
                ClassTypeInformation.from(AccessorSample.class));
        AccessorSample bean = new AccessorSample("spring-data");

        if (isNativeImageRuntime()) {
            throw new TestAbortedException(
                    "Native Image runtime cannot exercise ClassGeneratingPropertyAccessorFactory because it defines accessor classes dynamically"
            );
        }

        try {
            PersistentPropertyAccessor<AccessorSample> accessor = factory.getPropertyAccessor(entity, bean);

            assertThat(accessor.getBean()).isSameAs(bean);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static boolean isNativeImageRuntime() {
        return NATIVE_IMAGE_RUNTIME.equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }

    public static final class AccessorSample {

        private final String name;

        public AccessorSample(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
