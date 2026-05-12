/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_commons;

import static org.assertj.core.api.Assertions.assertThat;

import kotlin.Pair;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.springframework.data.mapping.Association;
import org.springframework.data.mapping.PersistentEntity;
import org.springframework.data.mapping.PersistentProperty;
import org.springframework.data.mapping.PersistentPropertyAccessor;
import org.springframework.data.mapping.model.BasicPersistentEntity;
import org.springframework.data.util.ClassTypeInformation;
import org.springframework.data.util.TypeInformation;
import org.springframework.lang.Nullable;

public class KotlinCopyMethodTest {

    @Test
    void usesKotlinCopyMethodToSetImmutableDataClassProperty() {
        BasicPersistentEntity<Pair, TestPersistentProperty> entity = new BasicPersistentEntity<>(
                ClassTypeInformation.from(Pair.class));
        TestPersistentProperty firstProperty = new TestPersistentProperty(entity, "first", Object.class);
        Pair<String, String> source = new Pair<>("before", "second");

        PersistentPropertyAccessor<Pair<String, String>> accessor = entity.getPropertyAccessor(source);
        accessor.setProperty(firstProperty, "after");

        assertThat(accessor.getBean()).isNotSameAs(source);
        assertThat(accessor.getBean().getFirst()).isEqualTo("after");
        assertThat(accessor.getBean().getSecond()).isEqualTo("second");
    }

    private static final class TestPersistentProperty implements PersistentProperty<TestPersistentProperty> {

        private final PersistentEntity<?, TestPersistentProperty> owner;
        private final String name;
        private final TypeInformation<?> typeInformation;

        private TestPersistentProperty(PersistentEntity<?, TestPersistentProperty> owner, String name, Class<?> type) {
            this.owner = owner;
            this.name = name;
            this.typeInformation = ClassTypeInformation.from(type);
        }

        @Override
        public PersistentEntity<?, TestPersistentProperty> getOwner() {
            return owner;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Class<?> getType() {
            return typeInformation.getType();
        }

        @Override
        public TypeInformation<?> getTypeInformation() {
            return typeInformation;
        }

        @Override
        public Iterable<? extends TypeInformation<?>> getPersistentEntityTypes() {
            return Collections.emptyList();
        }

        @Override
        public Iterable<? extends TypeInformation<?>> getPersistentEntityTypeInformation() {
            return Collections.emptyList();
        }

        @Override
        @Nullable
        public Method getGetter() {
            return null;
        }

        @Override
        @Nullable
        public Method getSetter() {
            return null;
        }

        @Override
        @Nullable
        public Method getWither() {
            return null;
        }

        @Override
        @Nullable
        public Field getField() {
            return null;
        }

        @Override
        @Nullable
        public String getSpelExpression() {
            return null;
        }

        @Override
        @Nullable
        public Association<TestPersistentProperty> getAssociation() {
            return null;
        }

        @Override
        public boolean isEntity() {
            return false;
        }

        @Override
        public boolean isIdProperty() {
            return false;
        }

        @Override
        public boolean isVersionProperty() {
            return false;
        }

        @Override
        public boolean isCollectionLike() {
            return false;
        }

        @Override
        public boolean isMap() {
            return false;
        }

        @Override
        public boolean isArray() {
            return false;
        }

        @Override
        public boolean isTransient() {
            return false;
        }

        @Override
        public boolean isWritable() {
            return true;
        }

        @Override
        public boolean isImmutable() {
            return true;
        }

        @Override
        public boolean isAssociation() {
            return false;
        }

        @Override
        @Nullable
        public Class<?> getComponentType() {
            return null;
        }

        @Override
        public Class<?> getRawType() {
            return getType();
        }

        @Override
        @Nullable
        public Class<?> getMapValueType() {
            return null;
        }

        @Override
        public Class<?> getActualType() {
            return getType();
        }

        @Override
        @Nullable
        public <A extends Annotation> A findAnnotation(Class<A> annotationType) {
            return null;
        }

        @Override
        @Nullable
        public <A extends Annotation> A findPropertyOrOwnerAnnotation(Class<A> annotationType) {
            return null;
        }

        @Override
        public boolean isAnnotationPresent(Class<? extends Annotation> annotationType) {
            return false;
        }

        @Override
        public boolean usePropertyAccess() {
            return false;
        }

        @Override
        @Nullable
        public Class<?> getAssociationTargetType() {
            return null;
        }

        @Override
        @Nullable
        public TypeInformation<?> getAssociationTargetTypeInformation() {
            return null;
        }
    }
}
