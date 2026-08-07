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
import org.springframework.data.mapping.PersistentEntity;
import org.springframework.data.mapping.PersistentPropertyAccessor;
import org.springframework.data.mapping.context.AbstractMappingContext;
import org.springframework.data.mapping.model.AnnotationBasedPersistentProperty;
import org.springframework.data.mapping.model.BasicPersistentEntity;
import org.springframework.data.mapping.model.Property;
import org.springframework.data.mapping.model.SimpleTypeHolder;
import org.springframework.data.util.TypeInformation;

public class KotlinCopyMethodTest {

    @Test
    void accessorUpdatesImmutableKotlinDataClassPropertyWithCopyMethod() {
        TestMappingContext context = new TestMappingContext();
        BasicPersistentEntity<?, TestPersistentProperty> entity = context.getRequiredPersistentEntity(Pair.class);
        TestPersistentProperty firstProperty = entity.getRequiredPersistentProperty("first");
        Pair<String, Integer> source = new Pair<>("Ada", 1);

        PersistentPropertyAccessor<Pair<String, Integer>> accessor = entity.getPropertyAccessor(source);

        accessor.setProperty(firstProperty, "Grace");

        assertThat(accessor.getBean()).isEqualTo(new Pair<>("Grace", 1));
        assertThat(source).isEqualTo(new Pair<>("Ada", 1));
    }

    private static final class TestMappingContext
            extends AbstractMappingContext<BasicPersistentEntity<?, TestPersistentProperty>, TestPersistentProperty> {

        @Override
        protected <T> BasicPersistentEntity<T, TestPersistentProperty> createPersistentEntity(
                TypeInformation<T> typeInformation) {

            return new BasicPersistentEntity<>(typeInformation);
        }

        @Override
        protected TestPersistentProperty createPersistentProperty(Property property,
                BasicPersistentEntity<?, TestPersistentProperty> owner, SimpleTypeHolder simpleTypeHolder) {

            return new TestPersistentProperty(property, owner, simpleTypeHolder);
        }
    }

    private static final class TestPersistentProperty
            extends AnnotationBasedPersistentProperty<TestPersistentProperty> {

        TestPersistentProperty(Property property, PersistentEntity<?, TestPersistentProperty> owner,
                SimpleTypeHolder simpleTypeHolder) {

            super(property, owner, simpleTypeHolder);
        }

        @Override
        protected Association<TestPersistentProperty> createAssociation() {
            return new Association<>(this, null);
        }
    }
}
