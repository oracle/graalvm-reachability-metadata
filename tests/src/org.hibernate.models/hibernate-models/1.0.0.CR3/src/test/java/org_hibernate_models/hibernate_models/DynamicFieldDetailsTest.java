/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_models.hibernate_models;

import java.lang.reflect.Field;

import org.hibernate.models.internal.BasicModelsContextImpl;
import org.hibernate.models.internal.SimpleClassDetails;
import org.hibernate.models.internal.SimpleClassLoading;
import org.hibernate.models.internal.dynamic.DynamicClassDetails;
import org.hibernate.models.internal.dynamic.DynamicFieldDetails;
import org.hibernate.models.spi.ClassDetails;
import org.hibernate.models.spi.ModelsContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DynamicFieldDetailsTest {
    @Test
    public void resolvesJavaFieldForClassBackedDynamicAttribute() {
        final ModelsContext modelsContext = newModelsContext();
        final DynamicClassDetails declaringType = new DynamicClassDetails(
                "DynamicFieldModel",
                DynamicFieldModel.class.getName(),
                DynamicFieldModel.class,
                false,
                ClassDetails.OBJECT_CLASS_DETAILS,
                null,
                modelsContext
        );

        final DynamicFieldDetails fieldDetails = declaringType.applyAttribute(
                "title",
                new SimpleClassDetails(String.class),
                false,
                false,
                modelsContext
        );

        final Field javaMember = fieldDetails.toJavaMember();

        assertThat(javaMember.getDeclaringClass()).isEqualTo(DynamicFieldModel.class);
        assertThat(javaMember.getName()).isEqualTo("title");
        assertThat(javaMember.getType()).isEqualTo(String.class);
    }

    private static ModelsContext newModelsContext() {
        return new BasicModelsContextImpl(SimpleClassLoading.SIMPLE_CLASS_LOADING, false, null);
    }

    private static final class DynamicFieldModel {
        private String title;
    }
}
