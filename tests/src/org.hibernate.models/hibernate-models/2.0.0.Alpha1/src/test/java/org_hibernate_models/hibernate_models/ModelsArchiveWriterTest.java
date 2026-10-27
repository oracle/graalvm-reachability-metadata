/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_models.hibernate_models;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.hibernate.models.internal.BasicModelsContextImpl;
import org.hibernate.models.internal.SimpleClassLoading;
import org.hibernate.models.serial.spi.ModelReference;
import org.hibernate.models.serial.spi.ModelsArchiveWriter;
import org.hibernate.models.serial.spi.ModelsArchives;
import org.hibernate.models.spi.ClassDetails;
import org.hibernate.models.spi.ModelsContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ModelsArchiveWriterTest {
    @Test
    public void archivesClassAnnotationsWithNestedValues() {
        final ModelsContext context = newModelsContext();
        final ClassDetails details = context.getClassDetailsRegistry()
                .resolveClassDetails(AnnotatedModel.class.getName());
        final ModelsArchiveWriter writer = ModelsArchives.createWriter(false);

        final ModelReference reference = writer.reference(details);

        assertThat(reference.kind()).isEqualTo(ModelReference.Kind.CLASS);
        assertThat(writer.finish()).isNotNull();
    }

    private static ModelsContext newModelsContext() {
        return new BasicModelsContextImpl(SimpleClassLoading.SIMPLE_CLASS_LOADING, false, null);
    }

    @ArchiveMarker(
            nested = @NestedMarker(value = "nested-value", category = "archive"),
            target = AnnotatedModel.class,
            labels = {"serial", "metadata"}
    )
    private static final class AnnotatedModel {
    }

    @Retention(RetentionPolicy.RUNTIME)
    private @interface ArchiveMarker {
        NestedMarker nested();

        Class<?> target();

        String[] labels();
    }

    @Retention(RetentionPolicy.RUNTIME)
    private @interface NestedMarker {
        String value();

        String category();
    }
}
