/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_commons;

import static org.assertj.core.api.Assertions.assertThat;

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

public class ClassGeneratingPropertyAccessorFactoryTest {

    @Test
    void mappingContextCreatesPersistentPropertyAccessorForMutableEntity() {
        TestMappingContext context = new TestMappingContext();
        BasicPersistentEntity<?, TestPersistentProperty> entity = context
                .getRequiredPersistentEntity(MutablePerson.class);
        MutablePerson person = new MutablePerson("Ada");
        TestPersistentProperty nameProperty = entity.getRequiredPersistentProperty("name");

        PersistentPropertyAccessor<MutablePerson> accessor = entity.getPropertyAccessor(person);

        assertThat(accessor.getProperty(nameProperty)).isEqualTo("Ada");

        accessor.setProperty(nameProperty, "Grace");

        assertThat(accessor.getBean()).isSameAs(person);
        assertThat(person.getName()).isEqualTo("Grace");
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

    public static final class MutablePerson {

        private String name;

        MutablePerson(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
