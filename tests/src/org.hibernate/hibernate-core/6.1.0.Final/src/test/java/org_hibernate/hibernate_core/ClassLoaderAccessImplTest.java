/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassLoaderAccessImplTest {

    @Test
    public void resolvesAnAnnotatedClassRegisteredByName() {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting(AvailableSettings.URL, "jdbc:h2:mem:class-loader-access")
                .applySetting(AvailableSettings.DRIVER, "org.h2.Driver")
                .applySetting(AvailableSettings.DIALECT, "org.hibernate.dialect.H2Dialect")
                .applySetting(AvailableSettings.HBM2DDL_AUTO, "create-drop")
                .applySetting(AvailableSettings.JAKARTA_VALIDATION_MODE, "none")
                .build();
        try {
            try (SessionFactory factory = new MetadataSources(registry)
                    .addAnnotatedClassName(MetamodelRecord.class.getName())
                    .buildMetadata()
                    .buildSessionFactory()) {
                assertThat(factory.getMetamodel().entity(MetamodelRecord.class)).isNotNull();
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
