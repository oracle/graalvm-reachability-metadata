/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_commons;

import static org.assertj.core.api.Assertions.assertThat;

import kotlin.Pair;

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

public class KotlinClassGeneratingEntityInstantiatorInnerDefaultingKotlinConstructorResolverTest {

    @Test
    void createsKotlinEntityThroughClassGeneratingInstantiator() {
        BasicPersistentEntity<Pair, ?> entity = new BasicPersistentEntity<>(ClassTypeInformation.from(Pair.class));
        EntityInstantiator instantiator = new EntityInstantiators().getInstantiatorFor(entity);

        try {
            Pair<String, String> pair = createInstance(instantiator, entity);

            assertThat(pair.getFirst()).isEqualTo("left");
            assertThat(pair.getSecond()).isEqualTo("right");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static <T, P extends PersistentProperty<P>> T createInstance(EntityInstantiator instantiator,
            BasicPersistentEntity<T, P> entity) {
        return instantiator.createInstance(entity, new KotlinPairParameterValueProvider<>());
    }

    private static final class KotlinPairParameterValueProvider<P extends PersistentProperty<P>>
            implements ParameterValueProvider<P> {

        @Override
        @Nullable
        @SuppressWarnings("unchecked")
        public <T> T getParameterValue(Parameter<T, P> parameter) {
            if ("first".equals(parameter.getName())) {
                return (T) "left";
            }
            if ("second".equals(parameter.getName())) {
                return (T) "right";
            }
            throw new IllegalArgumentException("Unexpected Kotlin Pair constructor parameter: " + parameter.getName());
        }
    }
}
