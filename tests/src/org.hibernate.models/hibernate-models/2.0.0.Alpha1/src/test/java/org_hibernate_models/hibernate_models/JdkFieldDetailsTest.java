/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_models.hibernate_models;

import java.lang.reflect.Field;

import org.hibernate.models.internal.BasicModelsContextImpl;
import org.hibernate.models.internal.SimpleClassLoading;
import org.hibernate.models.internal.jdk.JdkClassDetails;
import org.hibernate.models.spi.FieldDetails;
import org.hibernate.models.spi.ModelsContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JdkFieldDetailsTest {
    @Test
    public void resolvesFieldOnProvidedDeclaringClass() {
        final ModelsContext modelsContext = newModelsContext();
        final FieldDetails fieldDetails = classDetails(TemplateFieldModel.class, modelsContext)
                .findFieldByName("description");

        final Field resolvedField = fieldDetails.toJavaMember(
                ConcreteFieldModel.class,
                SimpleClassLoading.SIMPLE_CLASS_LOADING,
                modelsContext
        );

        assertThat(resolvedField.getDeclaringClass()).isEqualTo(ConcreteFieldModel.class);
        assertThat(resolvedField.getName()).isEqualTo("description");
        assertThat(resolvedField.getType()).isEqualTo(String.class);
    }

    private static JdkClassDetails classDetails(Class<?> javaClass, ModelsContext modelsContext) {
        return new JdkClassDetails(javaClass, modelsContext);
    }

    private static ModelsContext newModelsContext() {
        return new BasicModelsContextImpl(SimpleClassLoading.SIMPLE_CLASS_LOADING, false, null);
    }

    private static final class TemplateFieldModel {
        private String description;
    }

    private static final class ConcreteFieldModel {
        private String description;
    }
}
