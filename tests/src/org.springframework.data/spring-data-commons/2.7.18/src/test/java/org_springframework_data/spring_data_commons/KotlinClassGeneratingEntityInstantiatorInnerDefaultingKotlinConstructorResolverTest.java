/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_commons;

import static org.assertj.core.api.Assertions.assertThat;

import kotlin.Pair;
import org.junit.jupiter.api.Test;
import org.springframework.data.mapping.Association;
import org.springframework.data.mapping.Parameter;
import org.springframework.data.mapping.PersistentEntity;
import org.springframework.data.mapping.model.AnnotationBasedPersistentProperty;
import org.springframework.data.mapping.model.BasicPersistentEntity;
import org.springframework.data.mapping.model.EntityInstantiators;
import org.springframework.data.mapping.model.ParameterValueProvider;
import org.springframework.data.mapping.model.Property;
import org.springframework.data.mapping.model.SimpleTypeHolder;
import org.springframework.data.util.ClassTypeInformation;

public class KotlinClassGeneratingEntityInstantiatorInnerDefaultingKotlinConstructorResolverTest {

    @Test
    @SuppressWarnings("rawtypes")
    void instantiatesKotlinEntityUsingItsConstructorParameters() {
        BasicPersistentEntity<Pair, TestProperty> entity = new BasicPersistentEntity<>(
                ClassTypeInformation.from(Pair.class));
        ParameterValueProvider<TestProperty> values = new ParameterValueProvider<>() {

            @Override
            @SuppressWarnings("unchecked")
            public <T> T getParameterValue(Parameter<T, TestProperty> parameter) {
                return (T) (parameter == entity.getInstanceCreatorMetadata().getParameters().get(0)
                        ? "Ada" : Integer.valueOf(7));
            }
        };

        Pair result = new EntityInstantiators().getInstantiatorFor(entity).createInstance(entity, values);

        assertThat(result.getFirst()).isEqualTo("Ada");
        assertThat(result.getSecond()).isEqualTo(7);
    }

    static class TestProperty extends AnnotationBasedPersistentProperty<TestProperty> {

        TestProperty(Property property, PersistentEntity<?, TestProperty> owner, SimpleTypeHolder types) {
            super(property, owner, types);
        }

        @Override
        protected Association<TestProperty> createAssociation() {
            return new Association<>(this, null);
        }
    }
}
