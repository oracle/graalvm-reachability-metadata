/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MetadataContextTest {

    @Test
    public void populatesTheJpaStaticMetamodel() {
        Configuration configuration = new Configuration()
                .addAnnotatedClass(MetamodelRecord.class)
                .setProperty(AvailableSettings.URL, "jdbc:h2:mem:static-metamodel")
                .setProperty(AvailableSettings.DRIVER, "org.h2.Driver")
                .setProperty(AvailableSettings.DIALECT, "org.hibernate.dialect.H2Dialect")
                .setProperty(AvailableSettings.HBM2DDL_AUTO, "create-drop")
                .setProperty(AvailableSettings.JAKARTA_VALIDATION_MODE, "none");

        try (SessionFactory factory = configuration.buildSessionFactory()) {
            assertThat(factory.getMetamodel().entity(MetamodelRecord.class).getName())
                    .isEqualTo("StaticMetamodelRecord");
            assertThat(MetamodelRecord_.id).isNotNull();
            assertThat(MetamodelRecord_.name.getName()).isEqualTo("name");
        }
    }
}
