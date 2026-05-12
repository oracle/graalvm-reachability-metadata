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
import org.springframework.data.mapping.Parameter;
import org.springframework.data.mapping.PersistentProperty;
import org.springframework.data.mapping.model.BasicPersistentEntity;
import org.springframework.data.mapping.model.EntityInstantiator;
import org.springframework.data.mapping.model.EntityInstantiators;
import org.springframework.data.mapping.model.ParameterValueProvider;
import org.springframework.data.util.ClassTypeInformation;
import org.springframework.lang.Nullable;

public class ClassGeneratingEntityInstantiatorTest {

    @Test
    void createsEntityThroughDefaultClassGeneratingInstantiator() {
        BasicPersistentEntity<GeneratedEntity, ?> entity = new BasicPersistentEntity<>(
                ClassTypeInformation.from(GeneratedEntity.class));
        EntityInstantiator instantiator = new EntityInstantiators().getInstantiatorFor(entity);

        try {
            GeneratedEntity generatedEntity = createInstance(instantiator, entity);

            assertThat(generatedEntity.getName()).isEqualTo("generated-by-instantiator");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static <T, P extends PersistentProperty<P>> T createInstance(EntityInstantiator instantiator,
            BasicPersistentEntity<T, P> entity) {
        return instantiator.createInstance(entity, new NullParameterValueProvider<>());
    }

    private static final class NullParameterValueProvider<P extends PersistentProperty<P>>
            implements ParameterValueProvider<P> {

        @Override
        @Nullable
        public <T> T getParameterValue(Parameter<T, P> parameter) {
            return null;
        }
    }

    public static final class GeneratedEntity {

        private final String name;

        public GeneratedEntity() {
            this.name = "generated-by-instantiator";
        }

        public String getName() {
            return name;
        }
    }
}
