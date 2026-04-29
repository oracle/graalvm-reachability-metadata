/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_models.hibernate_models;

import org.hibernate.models.internal.BasicModelsContextImpl;
import org.hibernate.models.internal.SimpleClassLoading;
import org.hibernate.models.internal.jdk.JdkClassDetails;
import org.hibernate.models.spi.FieldDetails;
import org.hibernate.models.spi.MethodDetails;
import org.hibernate.models.spi.ModelsContext;
import org.hibernate.models.spi.RecordComponentDetails;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JdkClassDetailsTest {
    @Test
    public void exposesDeclaredFieldsAndMethodsForClassBackedDetails() {
        final JdkClassDetails details = classDetails(EntityLikeModel.class);

        assertThat(details.getFields())
                .extracting(FieldDetails::getName)
                .containsExactlyInAnyOrder("identifier", "displayName");
        assertThat(details.getMethods())
                .extracting(MethodDetails::getName)
                .contains("getDisplayName", "renameTo", "normalizedName");
    }

    @Test
    public void exposesRecordComponentsForRecordBackedDetails() {
        final JdkClassDetails details = classDetails(AuditEntry.class);

        assertThat(details.isRecord()).isTrue();
        assertThat(details.getRecordComponents())
                .extracting(RecordComponentDetails::getName)
                .containsExactly("actor", "action");
    }

    private static JdkClassDetails classDetails(Class<?> javaClass) {
        return new JdkClassDetails(javaClass, newModelsContext());
    }

    private static ModelsContext newModelsContext() {
        return new BasicModelsContextImpl(SimpleClassLoading.SIMPLE_CLASS_LOADING, false, null);
    }

    private static final class EntityLikeModel {
        private String identifier;
        private String displayName;

        String getDisplayName() {
            return displayName;
        }

        EntityLikeModel renameTo(String newDisplayName) {
            this.displayName = newDisplayName;
            return this;
        }

        private String normalizedName() {
            return displayName == null ? identifier : displayName.trim();
        }
    }

    private record AuditEntry(String actor, String action) {
    }
}
