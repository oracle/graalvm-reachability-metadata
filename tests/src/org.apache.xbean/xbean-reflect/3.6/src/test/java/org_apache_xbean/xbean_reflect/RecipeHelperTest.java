/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_xbean.xbean_reflect;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;

import org.apache.xbean.recipe.DefaultExecutionContext;
import org.apache.xbean.recipe.ExecutionContext;
import org.apache.xbean.recipe.RecipeHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RecipeHelperTest {
    @Test
    void loadsClassWithTheCurrentExecutionContextClassLoader() throws ClassNotFoundException {
        ExecutionContext previousContext = ExecutionContext.setContext(new DefaultExecutionContext());
        try {
            Class<?> loadedClass = RecipeHelper.loadClass(LoadTarget.class.getName());

            assertThat(loadedClass).isSameAs(LoadTarget.class);
        } finally {
            ExecutionContext.setContext(previousContext);
        }
    }

    @Test
    void detectsPublicDefaultConstructors() {
        boolean hasDefaultConstructor = RecipeHelper.hasDefaultConstructor(PublicDefaultConstructorTarget.class);
        boolean missingDefaultConstructor = RecipeHelper.hasDefaultConstructor(ConstructorArgumentTarget.class);

        assertThat(hasDefaultConstructor).isTrue();
        assertThat(missingDefaultConstructor).isFalse();
    }

    @Test
    void convertsGenericArrayTypeToArrayClass() {
        Class<?> arrayClass = RecipeHelper.toClass(new GenericArrayOfStrings());

        assertThat(arrayClass).isSameAs(String[].class);
    }

    public static class LoadTarget {
        public LoadTarget() {
        }
    }

    public static class PublicDefaultConstructorTarget {
        public PublicDefaultConstructorTarget() {
        }
    }

    public static class ConstructorArgumentTarget {
        public ConstructorArgumentTarget(String value) {
        }
    }

    private static class GenericArrayOfStrings implements GenericArrayType {
        @Override
        public Type getGenericComponentType() {
            return String.class;
        }
    }
}
