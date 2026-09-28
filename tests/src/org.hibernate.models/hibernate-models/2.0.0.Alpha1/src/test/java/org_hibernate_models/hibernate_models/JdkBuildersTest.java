/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_models.hibernate_models;

import org.hibernate.models.internal.BasicModelsContextImpl;
import org.hibernate.models.internal.SimpleClassLoading;
import org.hibernate.models.internal.jdk.JdkBuilders;
import org.hibernate.models.spi.ClassDetails;
import org.hibernate.models.spi.ModelsContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JdkBuildersTest {
    @Test
    public void buildsObjectArrayClassDetailsFromJvmArrayName() {
        final ClassDetails details = JdkBuilders.DEFAULT_BUILDER
                .buildClassDetails(String[].class.getName(), newModelsContext());

        assertThat(details.getName()).isEqualTo(String[].class.getName());
        assertThat(details.getClassName()).isEqualTo(String[].class.getName());
        assertThat(details.toJavaClass()).isSameAs(String[].class);
    }

    @Test
    public void buildsPrimitiveArrayClassDetailsFromJvmArrayName() {
        final ClassDetails details = JdkBuilders.buildClassDetailsStatic(int[].class.getName(), newModelsContext());

        assertThat(details.getName()).isEqualTo(int[].class.getName());
        assertThat(details.getClassName()).isEqualTo(int[].class.getName());
        assertThat(details.toJavaClass()).isSameAs(int[].class);
    }

    private static ModelsContext newModelsContext() {
        return new BasicModelsContextImpl(SimpleClassLoading.SIMPLE_CLASS_LOADING, false, null);
    }
}
