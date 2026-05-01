/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_logmanager.jboss_logmanager;

import java.io.File;

import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.config.LogContextConfiguration;
import org.jboss.logmanager.config.PojoConfiguration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractPropertyConfigurationTest {

    @Test
    void commitPojoConfigurationUsesGetterToResolveConstructorPropertyType() {
        final LogContextConfiguration configuration = LogContextConfiguration.Factory.create(LogContext.create());
        final String pojoName = "constructorBackedFile";
        final String path = "target/constructor-backed-file.log";

        try {
            final PojoConfiguration pojo = configuration.addPojoConfiguration(null, File.class.getName(), pojoName, "path");
            pojo.setPropertyValueString("path", path);

            configuration.commit();

            assertThat(configuration.getPojoNames()).contains(pojoName);
            assertThat(configuration.getPojoConfiguration(pojoName).getClassName()).isEqualTo(File.class.getName());
            assertThat(configuration.getPojoConfiguration(pojoName).getConstructorProperties()).containsExactly("path");
            assertThat(configuration.getPojoConfiguration(pojoName).getPropertyValueString("path")).isEqualTo(path);
        } finally {
            configuration.forget();
        }
    }
}
