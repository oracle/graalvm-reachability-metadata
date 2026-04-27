/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.mapping.PersistentClass;
import org.junit.jupiter.api.Test;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationBinderTest {

    @Test
    public void appliesRepeatableDialectOverrideAnnotationDuringMetadataBinding() {
        Metadata metadata = buildMetadata(AnnotationBinderDialectOverrideItem.class);

        PersistentClass itemBinding = metadata.getEntityBinding(AnnotationBinderDialectOverrideItem.class.getName());
        List<String> checkConstraints = itemBinding.getTable().getCheckConstraints();

        assertThat(checkConstraints)
                .contains("quantity >= 5")
                .doesNotContain("quantity >= 0", "quantity >= 10");
    }

    private Metadata buildMetadata(Class<?> annotatedClass) {
        StandardServiceRegistry serviceRegistry = new StandardServiceRegistryBuilder()
                .applySetting(AvailableSettings.DIALECT, H2Dialect.class.getName())
                .applySetting(AvailableSettings.HBM2DDL_AUTO, "none")
                .applySetting("hibernate.temp.use_jdbc_metadata_defaults", "false")
                .build();
        try {
            return new MetadataSources(serviceRegistry)
                    .addAnnotatedClass(annotatedClass)
                    .buildMetadata();
        } finally {
            StandardServiceRegistryBuilder.destroy(serviceRegistry);
        }
    }
}

@Entity(name = "AnnotationBinderDialectOverrideItem")
@Table(name = "annotation_binder_dialect_override_item")
@org.hibernate.annotations.Check(constraints = "quantity >= 0")
@org.hibernate.annotations.DialectOverride.Check(
        dialect = H2Dialect.class,
        override = @org.hibernate.annotations.Check(constraints = "quantity >= 5"))
@org.hibernate.annotations.DialectOverride.Check(
        dialect = PostgreSQLDialect.class,
        override = @org.hibernate.annotations.Check(constraints = "quantity >= 10"))
class AnnotationBinderDialectOverrideItem {

    @Id
    private Long id;

    private int quantity;
}
