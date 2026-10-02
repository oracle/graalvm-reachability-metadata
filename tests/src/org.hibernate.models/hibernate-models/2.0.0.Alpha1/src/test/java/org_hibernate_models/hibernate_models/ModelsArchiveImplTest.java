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
import org.hibernate.models.serial.spi.ModelsArchive;
import org.hibernate.models.serial.spi.ModelsArchiveWriter;
import org.hibernate.models.serial.spi.ModelsArchives;
import org.hibernate.models.serial.spi.RestoredModels;
import org.hibernate.models.spi.ClassDetails;
import org.hibernate.models.spi.ModelsContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ModelsArchiveImplTest {
    @Test
    public void restoresAnnotationArrayValuesFromArchive() {
        final ModelsContext context = newModelsContext();
        final ClassDetails details = context.getClassDetailsRegistry()
                .resolveClassDetails(ArchivedModel.class.getName());
        final ModelsArchiveWriter writer = ModelsArchives.createWriter(false);
        final ModelReference reference = writer.reference(details);
        final ModelsArchive archive = writer.finish();

        final RestoredModels restored = archive.restore(SimpleClassLoading.SIMPLE_CLASS_LOADING, null);
        final ClassDetails restoredDetails = (ClassDetails) restored.resolve(reference);
        final ArchiveMarker marker = restoredDetails.getDirectAnnotationUsage(ArchiveMarker.class);

        assertThat(marker.labels()).containsExactly("first", "second", "third");
    }

    private static ModelsContext newModelsContext() {
        return new BasicModelsContextImpl(SimpleClassLoading.SIMPLE_CLASS_LOADING, false, null);
    }

    @ArchiveMarker(labels = {"first", "second", "third"})
    private static final class ArchivedModel {
    }

    @Retention(RetentionPolicy.RUNTIME)
    private @interface ArchiveMarker {
        String[] labels();
    }
}
